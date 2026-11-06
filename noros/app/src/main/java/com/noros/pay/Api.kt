package com.noros.pay

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(message: String, val code: String? = null) : Exception(message)

/**
 * Клиент Merchant API Noros (https://rest.noros.org/api/v1). Заголовки api_key и api_secret_key уходят с каждым запросом.
 * В API Noros нет списков заявок и диспутов, поэтому приложение помнит созданные в нём заявки и диспуты (Store) и опрашивает их по одной.
 * Ответы приведены к форме, с которой работает интерфейс: рядом с полями Noros лежат метод, логотип, таймер, ссылка.
 */
object Api {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private const val PAYMENT_MINUTES = 15L

    private suspend fun call(
        method: String, path: String, body: JSONObject? = null, multipart: MultipartBody? = null,
        baseUrl: String = Store.baseUrl, key: String = Store.apiKey, secret: String = Store.apiSecret,
    ): Any = withContext(Dispatchers.IO) {
        val b = Request.Builder().url("${baseUrl.trimEnd('/')}/api/v1$path").header("api_key", key).header("api_secret_key", secret).header("accept", "application/json")
        val rb: RequestBody? = multipart ?: if (body != null) body.toString().toRequestBody(JSON) else if (method == "GET") null else "".toRequestBody(JSON)
        b.method(method, rb)
        try {
            http.newCall(b.build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    Notices.http(r.code, method, multipart != null)
                    val j = runCatching { JSONObject(text) }.getOrNull()
                    val msg = j?.optString("message")?.ifBlank { null } ?: when (r.code) {
                        401 -> tr("Ключи не подошли. Проверьте api_key и api_secret_key.", "The keys were rejected. Check api_key and api_secret_key.")
                        403 -> tr("Доступ запрещён: возможно, IP телефона не разрешён в кабинете Noros, или запрос не принят.", "Access denied: the phone's IP may not be allowed in the Noros account, or the request was refused.")
                        else -> "HTTP ${r.code}"
                    }
                    val appealId = j?.optInt("appealId", 0) ?: 0
                    throw ApiException(msg, if (appealId > 0) "appeal:$appealId" else r.code.toString())
                }
                Notices.ok()
                if (text.isBlank()) JSONObject() else if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text)
            }
        } catch (e: ApiException) { throw e } catch (e: Exception) {
            Notices.network()
            throw ApiException(tr("Нет связи с сервером Noros. Проверьте интернет.", "Cannot reach the Noros server. Check your connection."))
        }
    }

    private suspend fun obj(method: String, path: String, body: JSONObject? = null, multipart: MultipartBody? = null) = call(method, path, body, multipart) as JSONObject

    /** Проверка введённых ключей: баланс требует api_secret_key, список банков — api_key. */
    suspend fun verify(url: String, key: String, secret: String): JSONObject {
        val bal = call("GET", "/balance", null, null, url, key, secret) as JSONObject
        call("GET", "/banks", null, null, url, key, secret)
        return bal
    }

    suspend fun balance() = obj("GET", "/balance")

    /**
     * Фиатная валюта кабинета. Баланс Noros в USDT, поэтому валюту берём из заявок (currency), выписок CSV (TerminalCurrency)
     * или полей баланса, если они там есть, но никогда не USDT.
     */
    suspend fun resolveFiat() {
        if (Store.fiat.isNotBlank() && Store.payoutFiat.isNotBlank()) return
        runCatching {
            val b = balance()
            listOf("terminalCurrency", "payoutCurrency", "fiatCurrency", "currency").forEach { k -> val v = b.optString(k); if (isFiat(v)) { Store.noteFiat(v); Store.notePayoutFiat(v); return@forEach } }
        }
        Store.orders().firstOrNull { isFiat(it.optString("currency")) }?.let { Store.noteFiat(it.optString("currency")) }
        if (Store.payoutFiat.isBlank()) runCatching { csvCurrency("payouts")?.let { Store.notePayoutFiat(it); if (Store.fiat.isBlank()) Store.noteFiat(it) } }
        if (Store.fiat.isBlank()) runCatching { csvCurrency("transactions")?.let { Store.noteFiat(it) } }
    }

    private suspend fun csvCurrency(kind: String): String? {
        val now = java.time.Instant.now()
        val text = String(exportCsv(kind, now.minusSeconds(30L * 86400), now, "created", null), Charsets.UTF_8)
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return null
        val head = lines[0].split(';')
        val idx = head.indexOfFirst { it.contains("currency", true) && !it.contains("merchant", true) }
        if (idx < 0) return null
        return lines.drop(1).map { it.split(';').getOrNull(idx)?.trim('"') }.firstOrNull { isFiat(it) }?.uppercase()
    }

    /** Выписка CSV за период (до 31 дня): /export/payouts или /export/transactions. */
    suspend fun exportCsv(kind: String, from: java.time.Instant, to: java.time.Instant, dateField: String, status: String?): ByteArray = withContext(Dispatchers.IO) {
        val q = StringBuilder("?from=${from.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)}&to=${to.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)}&dateField=$dateField")
        if (!status.isNullOrBlank()) q.append("&status=$status")
        val req = Request.Builder().url("${Store.baseUrl.trimEnd('/')}/api/v1/export/$kind$q").header("api_key", Store.apiKey).header("api_secret_key", Store.apiSecret).get().build()
        try {
            http.newCall(req).execute().use { r ->
                val body = r.body?.bytes() ?: ByteArray(0)
                if (!r.isSuccessful) {
                    val msg = runCatching { JSONObject(String(body)).optString("message") }.getOrNull()?.ifBlank { null } ?: when (r.code) {
                        429 -> tr("Выписка уже формируется. Повторите через ${r.header("Retry-After") ?: "несколько"} сек.", "A statement is already being prepared. Retry in ${r.header("Retry-After") ?: "a few"} s.")
                        401, 403 -> tr("Нет доступа к выпискам: проверьте ключи и права.", "No access to statements: check the keys and permissions.")
                        else -> "HTTP ${r.code}"
                    }
                    throw ApiException(msg, r.code.toString())
                }
                body
            }
        } catch (e: ApiException) { throw e } catch (e: Exception) {
            throw ApiException(tr("Нет связи с сервером Noros. Проверьте интернет.", "Cannot reach the Noros server. Check your connection."))
        }
    }

    // ---- Выплаты: POST /payout, GET/DELETE /payout/{id} ----
    private val PAYOUT_ACTIVE = setOf("Pending", "Processing")

    suspend fun createPayout(amount: Double, number: String, bank: String, owner: String): JSONObject {
        val uid = "po-" + java.util.UUID.randomUUID().toString().take(10)
        val r = obj("POST", "/payout", JSONObject().put("amount", amount).put("number", number).put("bankname", bank).put("owner", owner).put("uid", uid))
        val row = JSONObject(r.toString()); if (!row.has("uid") || row.isNull("uid")) row.put("uid", uid)
        Store.putPayout(row)
        return row
    }

    suspend fun payout(id: Int): JSONObject {
        val row = obj("GET", "/payout/$id")
        Store.putPayout(row)
        return row
    }

    suspend fun cancelPayout(id: Int): JSONObject {
        val r = obj("DELETE", "/payout/$id")
        Store.putPayout(r)
        return r
    }

    /** Выплаты, созданные в приложении: активные обновляются запросами к Noros, закрытые берутся из памяти. */
    suspend fun payoutRows(kind: String): List<JSONObject> = coroutineScope {
        val fresh = Store.payouts().map { r ->
            async { if (r.optString("status") in PAYOUT_ACTIVE) runCatching { payout(r.getInt("id")) }.getOrDefault(r) else r }
        }.awaitAll()
        Store.activePayouts = fresh.count { it.optString("status") in PAYOUT_ACTIVE }
        Store.activeOrders = fresh.count { it.optString("status") in ACTIVE }
        fresh.filter { if (kind == "active") it.optString("status") in PAYOUT_ACTIVE else if (kind == "archive") it.optString("status") !in PAYOUT_ACTIVE else true }
            .sortedByDescending { it.optInt("id") }
    }

    // ---- Методы (банки): нужны для логотипов в заявках ----
    private var banksCache: List<JSONObject>? = null
    private suspend fun banks(): List<JSONObject> {
        banksCache?.let { return it }
        val a = call("GET", "/banks") as JSONArray
        return a.objects().also { banksCache = it }
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")

    // ---- Создание ссылки ----
    suspend fun createLink(amount: Double, orderId: String): JSONObject {
        val r = obj("POST", "/widget", JSONObject().put("amount", amount).put("orderId", orderId))
        val rec = JSONObject().put("id", r.getInt("id")).put("link", r.getString("link")).put("amount", amount).put("currency", Store.fiat)
            .put("orderId", orderId).put("status", "created").put("createdAt", java.time.Instant.now().toString())
        Store.putOrder(rec)
        return r
    }

    // ---- Заявки ----
    private suspend fun enrich(t: JSONObject, saved: JSONObject?): JSONObject {
        val o = JSONObject(t.toString())
        saved?.let { s -> listOf("link", "hasAppeal").forEach { k -> if (s.has(k)) o.put(k, s.get(k)) } }
        val created = o.optString("createdAt").ifBlank { saved?.optString("createdAt").orEmpty() }
        if (created.isNotBlank()) o.put("createdAt", created)
        // Таймер: срок оплаты, который отдаёт Noros; пока его нет, считаем от создания заявки.
        val dl = o.optStr("paymentDeadline") ?: parseIsoMs(created)?.let { java.time.Instant.ofEpochMilli(it + PAYMENT_MINUTES * 60_000).toString() }
        if (dl != null) o.put("expiresAt", dl)
        val name = o.optStr("bankReceiver")
        if (name != null) {
            o.put("method", name)
            val b = runCatching { banks() }.getOrDefault(emptyList()).firstOrNull { norm(it.optString("name")) == norm(name) }
            if (b != null) {
                o.put("method", b.optString("name")).put("methodLogo", b.optString("logo"))
                METHOD_BG[b.optInt("id")]?.let { o.put("methodBg", it) }
            }
        }
        if (!o.has("fee")) o.put("fee", 0.0)
        if (isFiat(o.optString("currency"))) Store.noteFiat(o.optString("currency"))
        return o
    }

    suspend fun transaction(id: Int): JSONObject {
        val saved = Store.order(id)
        val row = enrich(obj("GET", "/transaction/$id"), saved)
        Store.putOrder(row)
        return row
    }

    suspend fun live(id: Int) = transaction(id)

    /** Ход заявки: в API Noros нет истории событий, поэтому показываем создание и текущую стадию по датам заявки. */
    suspend fun history(id: Int): JSONArray {
        val r = Store.order(id) ?: return JSONArray()
        val a = JSONArray()
        a.put(JSONObject().put("type", "created").put("at", r.optString("createdAt")).put("by", "merchant"))
        val st = r.optString("status")
        if (st != "created") a.put(JSONObject().put("type", st).put("at", r.optString("updatedAt")).put("by", if (st == "completed" || st == "canceled") "system" else "customer"))
        if (r.optBoolean("hasAppeal")) a.put(JSONObject().put("type", "appeal").put("at", r.optString("updatedAt")).put("by", "merchant"))
        return a
    }

    /** Счётчики на вкладках «Заявки»/«Выплаты»: обновляются независимо от открытой вкладки. */
    suspend fun refreshCounts() { runCatching { rows("active") }; runCatching { payoutRows("active") } }

    private val ACTIVE = setOf("created", "offer", "accepted", "proof")

    /** Заявки, созданные в приложении: активные обновляются запросами к Noros, закрытые берутся из памяти приложения. */
    private suspend fun rows(kind: String): List<JSONObject> = coroutineScope {
        val fresh = Store.orders().map { r ->
            async { if (r.optString("status") in ACTIVE) runCatching { transaction(r.getInt("id")) }.getOrDefault(r) else r }
        }.awaitAll()
        Store.activeOrders = fresh.count { it.optString("status") in ACTIVE }
        fresh.filter { if (kind == "active") it.optString("status") in ACTIVE else if (kind == "archive") it.optString("status") !in ACTIVE else true }
            .sortedByDescending { it.optInt("id") }
    }

    suspend fun transactions(kind: String, page: Int) = JSONObject().put("rows", JSONArray(rows(kind)))
    suspend fun allTransactions() = JSONObject().put("rows", JSONArray(rows("all")))

    suspend fun cancelTransaction(id: Int): JSONObject {
        val t = obj("DELETE", "/transaction/$id")
        return runCatching { transaction(id) }.getOrDefault(t)
    }

    private fun multipart(bytes: ByteArray, fields: Map<String, String>): MultipartBody {
        val (name, mime) = when {
            bytes.size > 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() -> "receipt.pdf" to "application/pdf"
            bytes.size > 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "receipt.png" to "image/png"
            else -> "receipt.jpg" to "image/jpeg"
        }
        val m = MultipartBody.Builder().setType(MultipartBody.FORM)
        fields.forEach { (k, v) -> m.addFormDataPart(k, v) }
        m.addFormDataPart("doc", name, bytes.toRequestBody(mime.toMediaType()))
        return m.build()
    }

    suspend fun addProof(id: Int, docBase64: String): JSONObject {
        val bytes = Base64.decode(docBase64, Base64.DEFAULT)
        val t = obj("PATCH", "/transaction/$id/proof", null, multipart(bytes, emptyMap()))
        return runCatching { transaction(id) }.getOrDefault(t)
    }

    // ---- Диспуты (апелляции) ----
    private fun appealRow(a: JSONObject, orderId: Int): JSONObject =
        JSONObject().put("id", a.optInt("id")).put("num", a.optInt("id")).put("payment_num", orderId.toString())
            .put("created_at", a.optString("createdAt")).put("status", a.optString("status"))

    suspend fun createAppeal(txId: Int, reason: String, claimed: Double?, docBase64: String): JSONObject {
        val bytes = Base64.decode(docBase64, Base64.DEFAULT)
        val tx = Store.order(txId)
        val completed = tx?.optString("status") == "completed"
        val f = linkedMapOf("transactionId" to txId.toString())
        if (reason.isNotBlank()) f["reason"] = reason
        if (claimed != null && (completed || claimed != tx?.optDouble("amount"))) { f["amountMismatch"] = "true"; f["claimedAmount"] = claimed.toString() }
        val r = try { obj("POST", "/appeal", null, multipart(bytes, f)) } catch (e: ApiException) {
            // 409: по заявке уже есть открытый диспут (мог быть создан не из приложения) — запоминаем его номер.
            val code = e.code
            val id = if (code != null && code.startsWith("appeal:")) code.removePrefix("appeal:").toIntOrNull() else null
            if (id != null) Store.putAppeal(JSONObject().put("id", id).put("transactionId", txId).put("status", "created").put("createdAt", java.time.Instant.now().toString()))
            throw e
        }
        Store.putAppeal(JSONObject(r.toString()).put("transactionId", txId))
        Store.order(txId)?.let { Store.putOrder(it.put("hasAppeal", true)) }
        return r
    }

    suspend fun appeals(): JSONObject = coroutineScope {
        val rows = Store.appeals().map { a ->
            async {
                val closed = a.optString("status") in setOf("approved", "rejected", "canceled")
                val cur = if (closed) a else runCatching { obj("GET", "/appeal/${a.getInt("id")}").put("transactionId", a.optInt("transactionId")) }.getOrDefault(a)
                if (!closed) Store.putAppeal(cur)
                appealRow(cur, cur.optInt("transactionId"))
            }
        }.awaitAll().sortedByDescending { it.optInt("id") }
        JSONObject().put("rows", JSONArray(rows))
    }

    suspend fun appealsAll() = appeals()
}
