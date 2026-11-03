package com.noros.pay

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
    const val SERVER = "https://rest.noros.org"
    var baseUrl by mutableStateOf(SERVER)
    var theme by mutableStateOf("system") // system | light | dark
    var lang by mutableStateOf("ru")      // ru | en
    var logoMode by mutableStateOf("noros") // noros | none | custom
    var logoVersion by mutableStateOf(0)
    var icon by mutableStateOf("dark")    // dark | light
    var services by mutableStateOf(listOf<Service>())
    var testMerchant by mutableStateOf(false)
    var notify by mutableStateOf(true)
    var onboardingSeen by mutableStateOf(false)

    val connected get() = apiKey.isNotBlank() && apiSecret.isNotBlank()
    val isTest get() = false

    fun init(ctx: Context) {
        app = ctx.applicationContext
        plain = app.getSharedPreferences("noros_pay", Context.MODE_PRIVATE)
        secure = openSecure()
        apiKey = secure.getString("api_key", "") ?: ""
        apiSecret = secure.getString("api_secret", "") ?: ""
        baseUrl = SERVER
        theme = plain.getString("theme", "system") ?: "system"
        lang = plain.getString("lang", if (java.util.Locale.getDefault().language == "ru") "ru" else "en") ?: "ru"
        logoMode = plain.getString("logo_mode", "noros") ?: "noros"
        icon = plain.getString("icon", "dark") ?: "dark"
        onboardingSeen = plain.getBoolean("onboarding", false)
        testMerchant = plain.getBoolean("test_merchant", false)
        notify = plain.getBoolean("notify", true)
        services = runCatching {
            val a = JSONArray(plain.getString("services", "[]"))
            (0 until a.length()).map { val o = a.getJSONObject(it); Service(o.getString("id"), o.getString("name"), o.optString("description"), o.getString("amount")) }
        }.getOrDefault(emptyList())
    }

    private fun openSecure(): SharedPreferences {
        fun make() = EncryptedSharedPreferences.create(
            app, "noros_pay_secure", MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        return try { make() } catch (e: Exception) {
            // Хранилище повреждено (например, после восстановления): ключи придется ввести заново.
            app.deleteSharedPreferences("noros_pay_secure"); make()
        }
    }

    fun saveKeys(url: String, key: String, secret: String, test: Boolean = false) {
        secure.edit().putString("api_key", key).putString("api_secret", secret).apply()
        apiKey = key; apiSecret = secret; baseUrl = SERVER
    }

    /** Ключ можно только удалить и ввести новый. */
    fun deleteKeys() {
        secure.edit().remove("api_key").remove("api_secret").apply()
        apiKey = ""; apiSecret = ""
        watchPrefs().edit().clear().apply()
        // Список заявок и диспутов относится к ключам мерчанта: со сменой ключей он очищается.
        plain.edit().remove("orders").remove("appeals").apply()
    }

    fun applyTheme(v: String) { theme = v; plain.edit().putString("theme", v).apply() }
    fun applyLang(v: String) { lang = v; plain.edit().putString("lang", v).apply() }
    fun applyNotify(v: Boolean) { notify = v; plain.edit().putBoolean("notify", v).apply() }
    fun watchPrefs() = app.getSharedPreferences("noros_pay_watch", Context.MODE_PRIVATE)
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
            ComponentName(app, "com.noros.pay.$name"),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP,
        )
        // Сначала включаем новую, потом выключаем старую, чтобы у приложения всегда была иконка.
        set(if (v == "light") "IconLight" else "IconDark", true)
        set(if (v == "light") "IconDark" else "IconLight", false)
    }

    // ---- Заявки и диспуты, созданные в приложении (в API Noros списков нет) ----
    private fun readList(key: String): MutableList<JSONObject> = synchronized(this) {
        runCatching { val a = JSONArray(plain.getString(key, "[]")); (0 until a.length()).map { a.getJSONObject(it) }.toMutableList() }.getOrDefault(mutableListOf())
    }

    private fun writeList(key: String, list: List<JSONObject>) = synchronized(this) {
        val a = JSONArray(); list.forEach { a.put(it) }
        plain.edit().putString(key, a.toString()).apply()
    }

    fun orders(): List<JSONObject> = readList("orders")
    fun order(id: Int): JSONObject? = orders().firstOrNull { it.optInt("id") == id }?.let { JSONObject(it.toString()) }
    fun putOrder(r: JSONObject) = synchronized(this) {
        val l = readList("orders"); val old = l.indexOfFirst { it.optInt("id") == r.optInt("id") }
        val row = JSONObject(r.toString())
        if (old >= 0) { val prev = l[old]; listOf("link", "hasAppeal", "currency").forEach { k -> if (!row.has(k) && prev.has(k)) row.put(k, prev.get(k)) }; l[old] = row } else l.add(row)
        writeList("orders", l.sortedByDescending { it.optInt("id") }.take(300))
    }

    fun appeals(): List<JSONObject> = readList("appeals")
    fun putAppeal(a: JSONObject) = synchronized(this) {
        val l = readList("appeals"); val old = l.indexOfFirst { it.optInt("id") == a.optInt("id") }
        val row = JSONObject(a.toString())
        if (old >= 0) { if (!row.has("transactionId")) row.put("transactionId", l[old].optInt("transactionId")); l[old] = row } else l.add(row)
        writeList("appeals", l)
    }

    fun saveServices(list: List<Service>) {
        services = list
        val a = JSONArray(); list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("description", it.description).put("amount", it.amount)) }
        plain.edit().putString("services", a.toString()).apply()
    }
}

/** Двуязычные подписи: русский по умолчанию, английский по выбору в настройках. */
fun tr(ru: String, en: String): String = if (Store.lang == "ru") ru else en
