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
                    val j = runCatching { JSONObject(text) }.getOrNull()
                    val msg = j?.optString("message")?.ifBlank { null } ?: when (r.code) {
                        401 -> tr("Ключи не подошли. Проверьте api_key и api_secret_key.", "The keys were rejected. Check api_key and api_secret_key.")
                        403 -> tr("Доступ запрещён: возможно, IP телефона не разрешён в кабинете Noros, или запрос не принят.", "Access denied: the phone's IP may not be allowed in the Noros account, or the request was refused.")
                        else -> "HTTP ${r.code}"
                    }
                    val appealId = j?.optInt("appealId", 0) ?: 0
                    throw ApiException(msg, if (appealId > 0) "appeal:$appealId" else r.code.toString())
                }
                if (text.isBlank()) JSONObject() else if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text)
            }
        } catch (e: ApiException) { throw e } catch (e: Exception) {
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
        val cur = runCatching { balance().optString("currency") }.getOrDefault("")
        val rec = JSONObject().put("id", r.getInt("id")).put("link", r.getString("link")).put("amount", amount).put("currency", cur)
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

    private val ACTIVE = setOf("created", "offer", "accepted", "proof")

    /** Заявки, созданные в приложении: активные обновляются запросами к Noros, закрытые берутся из памяти приложения. */
    private suspend fun rows(kind: String): List<JSONObject> = coroutineScope {
        val fresh = Store.orders().map { r ->
            async { if (r.optString("status") in ACTIVE) runCatching { transaction(r.getInt("id")) }.getOrDefault(r) else r }
        }.awaitAll()
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
