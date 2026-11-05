package com.nodera.pay

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Рассылки из админки Nodera через Firebase Cloud Messaging: приложение отдаёт серверу токен устройства,
 * а сервер шлёт на него пуш. Без Google Play Services токена нет, тогда приложение просто работает как раньше.
 */
object Push {
    private val scope = CoroutineScope(Dispatchers.IO)

    private suspend fun token(): String? = suspendCancellableCoroutine { c ->
        runCatching { FirebaseMessaging.getInstance().token.addOnCompleteListener { t -> c.resume(if (t.isSuccessful) t.result else null) } }
            .onFailure { c.resume(null) }
    }

    private fun version(ctx: Context): String = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()

    /** Сообщает серверу токен этого устройства (при каждом запуске и при смене токена). */
    suspend fun sync(ctx: Context) {
        if (!Store.connected) return
        ensureChannels(ctx)
        val t = token() ?: return
        runCatching { Api.registerDevice(t, version(ctx)) }
    }

    /** Перед удалением ключей: убирает устройство из рассылок (ключи запоминаются до удаления). */
    fun forget() {
        val url = Store.baseUrl; val key = Store.apiKey; val secret = Store.apiSecret
        scope.launch { val t = token() ?: return@launch; runCatching { Api.unregisterDevice(t, url, key, secret) } }
    }

    fun refresh(ctx: Context) { scope.launch { sync(ctx) } }
}

class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Store.init(applicationContext)
        Push.refresh(applicationContext)
    }

    /** В фоне уведомление показывает сама система; здесь оно приходит, пока приложение открыто. */
    override fun onMessageReceived(message: RemoteMessage) {
        Store.init(applicationContext)
        val title = message.notification?.title ?: message.data["title"] ?: return
        val text = message.notification?.body ?: message.data["body"].orEmpty()
        ensureChannels(applicationContext)
        push(applicationContext, 91_000 + (message.data["id"]?.toIntOrNull() ?: (System.currentTimeMillis() % 1000).toInt()), title, text, CH_BROADCAST)
    }
}
