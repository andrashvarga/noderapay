package com.nodera.pay

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Услуга/товар с заранее заданной суммой. */
data class Service(val id: String, val name: String, val description: String, val amount: String)

/**
 * Хранилище. API-ключи лежат только в EncryptedSharedPreferences (ключ шифрования в Android Keystore);
 * остальные настройки (тема, язык, услуги) не секретные и лежат в обычных настройках.
 */
object Store {
    private lateinit var secure: SharedPreferences
    private lateinit var plain: SharedPreferences
    private lateinit var app: Context

    var apiKey by mutableStateOf("")
    var apiSecret by mutableStateOf("")
    var baseUrl by mutableStateOf("")
    var theme by mutableStateOf("system") // system | light | dark
    var lang by mutableStateOf("ru")      // ru | en
    var logoMode by mutableStateOf("nodera") // nodera | none | custom
    var logoVersion by mutableStateOf(0)
    var icon by mutableStateOf("dark")    // dark | light
    var services by mutableStateOf(listOf<Service>())
    var testMerchant by mutableStateOf(false)
    /** Фиатная валюта приёма (по гео кабинета) и валюта выплат; определяются по заявкам и выпискам, а не по USDT-балансу. */
    var fiat by mutableStateOf("")
    var payoutFiat by mutableStateOf("")
    val payoutCur get() = payoutFiat.ifBlank { fiat }
    /** Сколько заявок и выплат сейчас активно: для значков на вкладках и плашки «активная». */
    var activeOrders by mutableStateOf(0)
    var activePayouts by mutableStateOf(0)
    var notify by mutableStateOf(true)
    var onboardingSeen by mutableStateOf(false)

    val connected get() = apiKey.isNotBlank() && apiSecret.isNotBlank() && baseUrl.isNotBlank()
    val isTest get() = apiKey.startsWith("ak_test_") || testMerchant

    fun init(ctx: Context) {
        app = ctx.applicationContext
        plain = app.getSharedPreferences("nodera_pay", Context.MODE_PRIVATE)
        secure = openSecure()
        apiKey = secure.getString("api_key", "") ?: ""
        apiSecret = secure.getString("api_secret", "") ?: ""
        baseUrl = plain.getString("base_url", "") ?: ""
        theme = plain.getString("theme", "system") ?: "system"
        lang = plain.getString("lang", if (java.util.Locale.getDefault().language == "ru") "ru" else "en") ?: "ru"
        logoMode = plain.getString("logo_mode", "nodera") ?: "nodera"
        icon = plain.getString("icon", "dark") ?: "dark"
        onboardingSeen = plain.getBoolean("onboarding", false)
        testMerchant = plain.getBoolean("test_merchant", false)
        fiat = plain.getString("fiat", "") ?: ""
        payoutFiat = plain.getString("payout_fiat", "") ?: ""
        notify = plain.getBoolean("notify", true)
        services = runCatching {
            val a = JSONArray(plain.getString("services", "[]"))
            (0 until a.length()).map { val o = a.getJSONObject(it); Service(o.getString("id"), o.getString("name"), o.optString("description"), o.getString("amount")) }
        }.getOrDefault(emptyList())
    }

    private fun openSecure(): SharedPreferences {
        fun make() = EncryptedSharedPreferences.create(
            app, "nodera_pay_secure", MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        return try { make() } catch (e: Exception) {
            // Хранилище повреждено (например, после восстановления): ключи придется ввести заново.
            app.deleteSharedPreferences("nodera_pay_secure"); make()
        }
    }

    fun saveKeys(url: String, key: String, secret: String, test: Boolean = false) {
        testMerchant = test; plain.edit().putBoolean("test_merchant", test).apply()
        secure.edit().putString("api_key", key).putString("api_secret", secret).apply()
        plain.edit().putString("base_url", url).apply()
        apiKey = key; apiSecret = secret; baseUrl = url
    }

    /** Ключ можно только удалить и ввести новый. */
    fun deleteKeys() {
        secure.edit().remove("api_key").remove("api_secret").apply()
        apiKey = ""; apiSecret = ""
        watchPrefs().edit().clear().apply()
        // Выплаты и валюта относятся к ключам мерчанта: со сменой ключей они очищаются.
        plain.edit().remove("payouts").remove("fiat").remove("payout_fiat").apply()
        fiat = ""; payoutFiat = ""; activeOrders = 0; activePayouts = 0
    }

    fun applyTheme(v: String) { theme = v; plain.edit().putString("theme", v).apply() }
    fun applyLang(v: String) { lang = v; plain.edit().putString("lang", v).apply() }
    fun applyNotify(v: Boolean) { notify = v; plain.edit().putBoolean("notify", v).apply() }
    fun watchPrefs() = app.getSharedPreferences("nodera_pay_watch", Context.MODE_PRIVATE)
    fun setOnboardingSeen() { onboardingSeen = true; plain.edit().putBoolean("onboarding", true).apply() }
    fun applyLogoMode(v: String) { logoMode = v; plain.edit().putString("logo_mode", v).apply() }

    val logoFile get() = File(app.filesDir, "custom_logo.png")

    fun saveCustomLogo(bytes: ByteArray) {
        logoFile.writeBytes(bytes); logoVersion++; applyLogoMode("custom")
    }

    fun applyIcon(v: String) {
        icon = v; plain.edit().putString("icon", v).apply()
        val pm = app.packageManager
        fun set(name: String, on: Boolean) = pm.setComponentEnabledSetting(
            ComponentName(app, "com.nodera.pay.$name"),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP,
        )
        // Сначала включаем новую, потом выключаем старую, чтобы у приложения всегда была иконка.
        set(if (v == "light") "IconLight" else "IconDark", true)
        set(if (v == "light") "IconDark" else "IconLight", false)
    }

    fun noteFiat(c: String?) { if (isFiat(c) && c!!.uppercase() != fiat) { fiat = c.uppercase(); plain.edit().putString("fiat", fiat).apply() } }
    fun notePayoutFiat(c: String?) { if (isFiat(c) && c!!.uppercase() != payoutFiat) { payoutFiat = c.uppercase(); plain.edit().putString("payout_fiat", payoutFiat).apply() } }

    // ---- Выплаты, созданные в приложении (в API мерчанта списка выплат нет) ----
    private fun readList(key: String): MutableList<JSONObject> = synchronized(this) {
        runCatching { val a = JSONArray(plain.getString(key, "[]")); (0 until a.length()).map { a.getJSONObject(it) }.toMutableList() }.getOrDefault(mutableListOf())
    }
    private fun writeList(key: String, list: List<JSONObject>) = synchronized(this) {
        val a = JSONArray(); list.forEach { a.put(it) }
        plain.edit().putString(key, a.toString()).apply()
    }
    fun payouts(): List<JSONObject> = readList("payouts")
    fun putPayout(r: JSONObject) = synchronized(this) {
        val l = readList("payouts"); val old = l.indexOfFirst { it.optInt("id") == r.optInt("id") }
        if (old >= 0) l[old] = JSONObject(r.toString()) else l.add(JSONObject(r.toString()))
        writeList("payouts", l.sortedByDescending { it.optInt("id") }.take(300))
    }

    fun saveServices(list: List<Service>) {
        services = list
        val a = JSONArray(); list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("description", it.description).put("amount", it.amount)) }
        plain.edit().putString("services", a.toString()).apply()
    }
}

private val CRYPTO = setOf("BTC", "ETH", "TRX", "TON", "BNB", "SOL", "XRP", "LTC", "DOGE", "ADA", "DAI")
/** Фиатная ли валюта: три буквы и не криптовалюта (USDT из четырёх букв отсекается сам). */
fun isFiat(c: String?): Boolean = c != null && Regex("^[A-Za-z]{3}$").matches(c) && c.uppercase() !in CRYPTO

/** Двуязычные подписи: русский по умолчанию, английский по выбору в настройках. */
fun tr(ru: String, en: String): String = if (Store.lang == "ru") ru else en
