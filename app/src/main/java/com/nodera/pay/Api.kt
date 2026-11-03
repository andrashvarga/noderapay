package com.nodera.pay

import kotlinx.coroutines.Dispatchers
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
                        val e = runCatching { JSONObject(text).getJSONObject("error") }.getOrNull()
                        throw ApiException(e?.optString("message")?.ifBlank { null } ?: "HTTP ${r.code}", e?.optString("code"))
                    }
                    if (text.isBlank()) JSONObject() else if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text)
                }
            } catch (e: ApiException) { throw e } catch (e: Exception) {
                throw ApiException(tr("Нет связи с сервером. Проверьте адрес и интернет.", "Cannot reach the server. Check the address and your connection."))
            }
        }

    private suspend fun obj(method: String, path: String, body: JSONObject? = null) = call(method, path, body) as JSONObject
    private suspend fun arr(path: String) = call("GET", path) as JSONArray

    /** Проверка введенных ключей: баланс требует api_secret_key. */
    suspend fun verify(url: String, key: String, secret: String): JSONObject = call("GET", "/balance", null, url, key, secret) as JSONObject

    suspend fun balance() = obj("GET", "/balance")
    suspend fun methods() = arr("/banks")

    suspend fun createLink(amount: Double, bankIds: List<Int>, orderId: String): JSONObject {
        val b = JSONObject().put("amount", amount).put("orderId", orderId)
        if (bankIds.isNotEmpty()) b.put("bankIds", JSONArray(bankIds))
        return obj("POST", "/widget", b)
    }

    suspend fun transaction(id: Int) = obj("GET", "/transaction/$id")
    suspend fun history(id: Int) = arr("/transaction/$id/history")
    suspend fun allTransactions() = obj("GET", "/transactions?state=all&page=1&per_page=50")
    suspend fun appealsAll() = obj("GET", "/appeals?status=all&per_page=50")

    suspend fun transactions(state: String, page: Int) = obj("GET", "/transactions?state=$state&page=$page&per_page=30")

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
