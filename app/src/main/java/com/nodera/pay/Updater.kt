package com.nodera.pay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Запись истории изменений: версия, дата и список правок на двух языках. */
class ReleaseNote(val code: Int, val name: String, val date: String, val ru: List<String>, val en: List<String>) {
    val lines get() = if (Store.lang == "ru") ru else en
}

/**
 * Обновления через релиз GitHub «latest»: сборка кладёт рядом с APK файл update.json (код версии, хэш, ссылка, список изменений).
 * Приложение сравнивает код версии с установленным, скачивает APK, проверяет хэш и отдаёт системному установщику поверх старой версии.
 */
object Updater {
    private const val BASE = "https://github.com/andrashvarga/noderapay/releases/download/latest"
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    var checking by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var lastChecked by mutableStateOf<Long?>(null)
    var remote by mutableStateOf<List<ReleaseNote>>(emptyList())
    var sha256 by mutableStateOf("")
    var apkUrl by mutableStateOf("$BASE/NoderaPay.apk")
    /** Прогресс скачивания 0..1, -1 если не качаем. */
    var progress by mutableStateOf(-1f)
    var installing by mutableStateOf(false)

    fun currentCode(ctx: Context): Int = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt() }.getOrDefault(0)
    fun currentName(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" }.getOrDefault("")

    fun latest(): ReleaseNote? = remote.maxByOrNull { it.code }
    fun available(ctx: Context): Boolean = (latest()?.code ?: 0) > currentCode(ctx)

    private fun parse(a: JSONArray): List<ReleaseNote> = (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        fun list(k: String) = o.optJSONArray(k)?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList()
        ReleaseNote(o.optInt("code"), o.optString("name"), o.optString("date"), list("ru"), list("en"))
    }

    /** История изменений: то, что вшито в приложение, дополненное более новыми записями из релиза. */
    fun history(ctx: Context): List<ReleaseNote> {
        val local = runCatching { parse(JSONArray(ctx.assets.open("changelog.json").bufferedReader().use { it.readText() })) }.getOrDefault(emptyList())
        return (remote + local).distinctBy { it.code }.sortedByDescending { it.code }
    }

    /** Запрос update.json. Возвращает true, если есть версия новее установленной. */
    suspend fun check(ctx: Context): Boolean {
        checking = true; error = null
        try {
            withContext(Dispatchers.IO) {
                val req = Request.Builder().url("$BASE/update.json").header("Cache-Control", "no-cache").build()
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw ApiException(tr("Сервер обновлений ответил ${r.code}", "The update server replied ${r.code}"))
                    val j = JSONObject(r.body!!.string())
                    remote = parse(j.getJSONArray("releases"))
                    sha256 = j.optString("sha256")
                    apkUrl = j.optString("apkUrl").ifBlank { "$BASE/NoderaPay.apk" }
                }
            }
            lastChecked = System.currentTimeMillis()
        } catch (e: ApiException) { error = e.message } catch (e: Exception) {
            error = tr("Не удалось проверить обновления. Проверьте интернет.", "Could not check for updates. Check your connection.")
        } finally { checking = false }
        return available(ctx)
    }

    /** Скачивает APK в кэш приложения и проверяет контрольную сумму. */
    suspend fun download(ctx: Context): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, "NoderaPay.apk")
        progress = 0f
        try {
            http.newCall(Request.Builder().url(apkUrl).build()).execute().use { r ->
                if (!r.isSuccessful) throw ApiException(tr("Не удалось скачать обновление (${r.code})", "Could not download the update (${r.code})"))
                val body = r.body!!
                val total = body.contentLength().coerceAtLeast(1)
                val md = MessageDigest.getInstance("SHA-256")
                var done = 0L
                body.byteStream().use { input ->
                    out.outputStream().use { os ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf); if (n < 0) break
                            os.write(buf, 0, n); md.update(buf, 0, n); done += n
                            progress = (done.toFloat() / total).coerceIn(0f, 1f)
                        }
                    }
                }
                val hex = md.digest().joinToString("") { "%02x".format(it) }
                if (sha256.isNotBlank() && !hex.equals(sha256, true)) { out.delete(); throw ApiException(tr("Файл повреждён при загрузке, попробуйте ещё раз.", "The file was corrupted during download, try again.")) }
            }
            out
        } catch (e: ApiException) { throw e } catch (e: Exception) {
            throw ApiException(tr("Нет связи при скачивании обновления.", "Connection lost while downloading the update."))
        } finally { progress = -1f }
    }

    /** Нужно ли сначала разрешить установку из приложения (Android 8+). */
    fun needsPermission(ctx: Context): Boolean = !ctx.packageManager.canRequestPackageInstalls()

    fun openPermission(ctx: Context) {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** Передаёт APK системному установщику: новая версия встаёт поверх старой с тем же ключом подписи, данные остаются. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "com.nodera.pay.files", apk)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    /** Полный цикл «скачать и установить»; возвращает текст для пользователя, если нужна его помощь. */
    suspend fun update(ctx: Context): String? {
        if (needsPermission(ctx)) { openPermission(ctx); return tr("Разрешите установку из Nodera Pay и нажмите «Обновить» ещё раз.", "Allow installs from Nodera Pay and tap “Update” again.") }
        installing = true
        return try { install(ctx, download(ctx)); null } catch (e: ApiException) { e.message } finally { installing = false }
    }
}
