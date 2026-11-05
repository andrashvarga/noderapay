package com.nodera.pay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiException(message: String, val code: String? = null) : Exception(message)

/** Клиент Merchant API Nodera (/api/v1). Все запросы подписываются заголовками api_key / api_secret_key. */
object Api {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private suspend fun call(method: String, path: String, body: JSONObject? = null, baseUrl: String = Store.baseUrl, key: String = Store.apiKey, secret: String = Store.apiSecret): Any =
        withContext(Dispatchers.IO) {
            val b = Request.Builder().url("${baseUrl.trimEnd('/')}/api/v1$path").header("api_secret_key", secret).header("accept", "application/json")
            val rb = if (body != null) body.toString().toRequestBody(JSON) else if (method == "GET") null else "".toRequestBody(JSON)
            b.method(method, rb)
            try {
                http.newCall(b.build()).execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    if (!r.isSuccessful) {
                        Notices.http(r.code, method, false)
                        val e = runCatching { JSONObject(text).getJSONObject("error") }.getOrNull()
                        throw ApiException(e?.optString("message")?.ifBlank { null } ?: "HTTP ${r.code}", e?.optString("code"))
                    }
                    Notices.ok()
                    if (text.isBlank()) JSONObject() else if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text)
                }
            } catch (e: ApiException) { throw e } catch (e: Exception) {
            Notices.network()
                throw ApiException(tr("Нет связи с сервером. Проверьте адрес и интернет.", "Cannot reach the server. Check the address and your connection."))
            }
        }

    private suspend fun obj(method: String, path: String, body: JSONObject? = null) = call(method, path, body) as JSONObject
    private suspend fun arr(path: String) = call("GET", path) as JSONArray

    /** Проверка введенных ключей: баланс требует api_secret_key. */
    suspend fun verify(url: String, key: String, secret: String): JSONObject = call("GET", "/balance", null, url, key, secret) as JSONObject

    suspend fun balance() = obj("GET", "/balance")

    /**
     * Фиатная валюта кабинета. Баланс в USDT, поэтому валюту берём из заявок (currency) и выписок CSV
     * (TerminalCurrency / Currency), но никогда не USDT.
     */
    suspend fun resolveFiat() {
        if (Store.fiat.isNotBlank() && Store.payoutFiat.isNotBlank()) return
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
        val req = Request.Builder().url("${Store.baseUrl.trimEnd('/')}/api/v1/export/$kind$q").header("api_secret_key", Store.apiSecret).get().build()
        try {
            http.newCall(req).execute().use { r ->
                val body = r.body?.bytes() ?: ByteArray(0)
                if (!r.isSuccessful) {
                    val msg = runCatching { JSONObject(String(body)).getJSONObject("error").optString("message") }.getOrNull()?.ifBlank { null } ?: when (r.code) {
                        429 -> tr("Выписка уже формируется. Повторите через ${r.header("Retry-After") ?: "несколько"} сек.", "A statement is already being prepared. Retry in ${r.header("Retry-After") ?: "a few"} s.")
                        else -> "HTTP ${r.code}"
                    }
                    throw ApiException(msg, r.code.toString())
                }
                body
            }
        } catch (e: ApiException) { throw e } catch (e: Exception) {
            throw ApiException(tr("Нет связи с сервером. Проверьте адрес и интернет.", "Cannot reach the server. Check the address and your connection."))
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

    suspend fun payout(id: Int): JSONObject { val row = obj("GET", "/payout/$id"); Store.putPayout(row); return row }
    suspend fun cancelPayout(id: Int): JSONObject { val r = obj("DELETE", "/payout/$id"); Store.putPayout(r); return r }

    /** Выплаты, созданные в приложении: активные обновляются запросами к серверу, закрытые берутся из памяти. */
    suspend fun payoutRows(kind: String): List<JSONObject> = coroutineScope {
        val fresh = Store.payouts().map { r ->
            async { if (r.optString("status") in PAYOUT_ACTIVE) runCatching { payout(r.getInt("id")) }.getOrDefault(r) else r }
        }.awaitAll()
        Store.activePayouts = fresh.count { it.optString("status") in PAYOUT_ACTIVE }
        fresh.filter { if (kind == "active") it.optString("status") in PAYOUT_ACTIVE else if (kind == "archive") it.optString("status") !in PAYOUT_ACTIVE else true }
            .sortedByDescending { it.optInt("id") }
    }

    private fun note(o: JSONObject): JSONObject {
        o.optJSONArray("rows")?.let { rows -> (0 until rows.length()).forEach { Store.noteFiat(rows.getJSONObject(it).optString("currency")) } }
        return o
    }
    suspend fun methods() = arr("/banks")

    suspend fun createLink(amount: Double, bankIds: List<Int>, orderId: String): JSONObject {
        val b = JSONObject().put("amount", amount).put("orderId", orderId)
        if (bankIds.isNotEmpty()) b.put("bankIds", JSONArray(bankIds))
        return obj("POST", "/widget", b)
    }

    suspend fun transaction(id: Int) = obj("GET", "/transaction/$id")
    suspend fun live(id: Int) = obj("GET", "/transaction/$id/live")
    suspend fun history(id: Int) = arr("/transaction/$id/history")
    suspend fun allTransactions() = note(obj("GET", "/transactions?state=all&page=1&per_page=50"))
    suspend fun appealsAll() = obj("GET", "/appeals?status=all&per_page=50")

    suspend fun transactions(state: String, page: Int): JSONObject {
        val o = note(obj("GET", "/transactions?state=$state&page=$page&per_page=30"))
        if (state == "active") Store.activeOrders = o.optInt("count", o.optJSONArray("rows")?.length() ?: 0)
        return o
    }

    suspend fun cancelTransaction(id: Int) = obj("DELETE", "/transaction/$id")
    suspend fun addProof(id: Int, docBase64: String) = obj("PATCH", "/transaction/$id/proof", JSONObject().put("doc", docBase64))

    suspend fun appeals() = obj("GET", "/appeals?status=all")
    suspend fun createAppeal(txId: Int, reason: String, claimed: Double?, docBase64: String): JSONObject {
        val b = JSONObject().put("transactionId", txId).put("doc", docBase64)
        if (reason.isNotBlank()) b.put("reason", reason)
        if (claimed != null) b.put("claimedAmount", claimed)
        return obj("POST", "/appeal", b)
    }

    suspend fun withdrawalRules() = obj("GET", "/withdrawal/rules")
    suspend fun wallets() = arr("/withdrawal/addresses")
    suspend fun addWallet(network: String, address: String, label: String) =
        obj("POST", "/withdrawal/address", JSONObject().put("network", network).put("address", address).put("label", label))
    suspend fun deleteWallet(id: String) = obj("DELETE", "/withdrawal/address/${enc(id)}")
    suspend fun withdrawals() = arr("/withdrawals")
    suspend fun withdraw(amount: Double, addressId: String) = obj("POST", "/withdrawal", JSONObject().put("amount", amount).put("addressId", addressId))
    suspend fun cancelWithdrawal(id: String) = obj("DELETE", "/withdrawal/${enc(id)}")

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
