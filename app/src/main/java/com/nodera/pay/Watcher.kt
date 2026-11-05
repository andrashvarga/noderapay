package com.nodera.pay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val CH_EVENTS = "events"
private const val CH_WATCH = "watch"
private const val POLL_MS = 30_000L

/** Каналы уведомлений: события (заявки, выводы, диспуты) и служебное «слежу за статусами». */
fun ensureChannels(ctx: Context) {
    if (Build.VERSION.SDK_INT < 26) return
    val nm = ctx.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CH_EVENTS, tr("Статусы заявок, выплат, выводов и диспутов", "Order, payout, withdrawal and dispute updates"), NotificationManager.IMPORTANCE_HIGH))
    nm.createNotificationChannel(NotificationChannel(CH_WATCH, tr("Работа в фоне", "Background work"), NotificationManager.IMPORTANCE_MIN))
}

fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Запускает или останавливает слежение в зависимости от настроек и подключения. */
fun syncWatcher(ctx: Context) {
    val i = Intent(ctx, WatchService::class.java)
    if (Store.connected && Store.notify) runCatching { ContextCompat.startForegroundService(ctx, i) } else ctx.stopService(i)
}

private fun push(ctx: Context, id: Int, title: String, text: String) {
    if (!canNotify(ctx)) return
    val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val n = NotificationCompat.Builder(ctx, CH_EVENTS).setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
    runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
}

/**
 * Слежение за статусами без Firebase: служба на переднем плане раз в 30 секунд опрашивает API и сравнивает статусы
 * заявок, выводов и диспутов с запомненными. При смене статуса приходит уведомление.
 * Ограничения: нужна постоянная заметка в шторке (требование Android), а на некоторых телефонах (Xiaomi, Huawei, Samsung)
 * система может убивать фоновые службы — тогда в настройках телефона нужно отключить оптимизацию батареи для Nodera Pay.
 */
class WatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var lastUpdateCheck = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels(this)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CH_WATCH).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Nodera Pay").setContentText(tr("Слежу за статусами заявок, выплат и выводов", "Watching order, payout and withdrawal statuses"))
            .setContentIntent(open).setOngoing(true).setPriority(NotificationCompat.PRIORITY_MIN).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(1, n)
        if (job?.isActive != true) job = scope.launch {
            while (isActive) {
                if (Store.connected && Store.notify) { runCatching { poll(applicationContext) }; runCatching { checkUpdate(applicationContext) } }
                else { stopSelf(); break }
                delay(POLL_MS)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    /** Раз в полчаса смотрим релиз; о каждой новой версии сообщаем один раз. */
    private suspend fun checkUpdate(ctx: Context) {
        if (System.currentTimeMillis() - lastUpdateCheck < 30 * 60_000L) return
        lastUpdateCheck = System.currentTimeMillis()
        if (!Updater.check(ctx)) return
        val n = Updater.latest() ?: return
        val prefs = ctx.getSharedPreferences("nodera_pay_update", Context.MODE_PRIVATE)
        if (prefs.getInt("update_notified", 0) >= n.code) return
        prefs.edit().putInt("update_notified", n.code).apply()
        Notices.updateText(n.name).let { push(ctx, 90_000, it.first, it.second) }
    }

    private suspend fun poll(ctx: Context) {
        val prefs = Store.watchPrefs()
        val first = !prefs.getBoolean("init", false)
        val old = prefs.all
        val next = HashMap<String, String>()
        fun seen(key: String, status: String, onChange: (String) -> Unit) {
            next[key] = status
            val was = old[key] as? String
            if (!first && was != null && was != status) onChange(status)
        }
        for (t in Api.allTransactions().getJSONArray("rows").objects()) {
            val id = t.optInt("id")
            seen("tx:$id", t.optString("status")) { st ->
                val amount = money(t.optDouble("amount"), curOf(t))
                when (st) {
                    "completed" -> push(ctx, 10_000 + id, tr("Заявка #$id оплачена", "Order #$id paid"), tr("Сумма $amount. Оплата подтверждена.", "Amount $amount. Payment confirmed."))
                    "canceled", "expired", "failed" -> push(ctx, 10_000 + id, tr("Заявка #$id отменена", "Order #$id canceled"),
                        (cancelText(t.optString("cancelReason")) ?: tr("Заявка закрыта без оплаты.", "The order was closed unpaid.")) + " $amount")
                }
            }
        }
        for (p in Api.payoutRows("all")) {
            val id = p.optInt("id")
            seen("po:$id", p.optString("status")) { st ->
                val amount = money(p.optDouble("amount"), curOf(p, true))
                when (st) {
                    "Processing" -> push(ctx, 50_000 + id, tr("Выплата #$id в работе", "Payout #$id in progress"), tr("Сумма $amount. Исполнитель взял выплату.", "Amount $amount. A provider took the payout."))
                    "Success" -> push(ctx, 50_000 + id, tr("Выплата #$id выполнена", "Payout #$id completed"), tr("Сумма $amount переведена получателю.", "Amount $amount was sent to the recipient."))
                    "Canceled" -> push(ctx, 50_000 + id, tr("Выплата #$id отменена", "Payout #$id canceled"), (p.optString("comment").takeIf { it.isNotBlank() && it != "null" } ?: tr("Выплата закрыта без перевода.", "The payout was closed without a transfer.")) + " $amount")
                }
            }
        }
        for (w in Api.withdrawals().objects()) {
            val num = w.optInt("num")
            seen("wd:${w.optString("id")}", w.optString("status")) { st ->
                val amount = money(w.optDouble("amountUsdt", 0.0), "USDT")
                when (st) {
                    "success", "completed" -> push(ctx, 20_000 + num, tr("Вывод #$num выполнен", "Withdrawal #$num completed"), amount)
                    "canceled" -> push(ctx, 20_000 + num, tr("Вывод #$num отменён", "Withdrawal #$num canceled"), w.optString("comment").takeIf { it.isNotBlank() && it != "null" } ?: amount)
                }
            }
        }
        for (a in Api.appealsAll().getJSONArray("rows").objects()) {
            val num = a.optInt("num"); val order = a.optString("payment_num")
            seen("ap:${a.optString("id")}", a.optString("status")) { st ->
                when (st) {
                    "approved" -> push(ctx, 30_000 + num, tr("Диспут #$num принят", "Dispute #$num approved"), tr("Заявка #$order будет зачтена.", "Order #$order will be credited."))
                    "rejected" -> {
                        val why = a.optString("reject_reason").takeIf { it.isNotBlank() && it != "null" } ?: a.optJSONObject("last_event")?.optString("comment")?.takeIf { it.isNotBlank() && it != "null" } ?: tr("причина не указана", "no reason given")
                        push(ctx, 30_000 + num, tr("Диспут #$num отклонён", "Dispute #$num rejected"), tr("Заявка #$order. Причина: $why", "Order #$order. Reason: $why"))
                    }
                    "canceled" -> push(ctx, 30_000 + num, tr("Диспут #$num отменён", "Dispute #$num canceled"), tr("Заявка #$order.", "Order #$order."))
                }
            }
        }
        val e = prefs.edit().clear().putBoolean("init", true)
        next.forEach { (k, v) -> e.putString(k, v) }
        e.apply()
    }

    private fun cancelText(code: String?): String? = when (code) {
        "canceled_by_merchant" -> tr("Отменена вами.", "Canceled by you.")
        "receipt_rejected" -> tr("Чек отклонён.", "Receipt rejected.")
        "expired", "timeout" -> tr("Истекло время оплаты.", "Payment time expired.")
        else -> null
    }
}

/** После перезагрузки телефона слежение запускается снова. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { Store.init(context); syncWatcher(context) }
    }
}
