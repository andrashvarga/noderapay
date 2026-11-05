package com.noros.pay

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Шуточные плашки о связи и ошибках: на каждую ситуацию несколько вариантов, показываются вразнобой, подряд не повторяются. */
object Notices {
    class Msg(val id: Long, val kind: String, val title: String, val text: String, val action: (() -> Unit)? = null)
    private class N(val kind: String, val title: String, val text: String)

    var current by mutableStateOf<Msg?>(null)
        private set
    @Volatile var offline = false
    private var fails = 0
    private var seq = 0L
    private val last = HashMap<String, Int>()
    private val at = HashMap<String, Long>()

    private val V: Map<String, List<N>> = mapOf(
        "offline" to listOf(N("off","Связь отвалилась","Пиксели на месте, интернета нет."),N("off","Тишина в эфире","Нет сети. Показываем последнее загруженное."),N("off","Офлайн","Телефон ищет сеть. Мы подождём.")),
        "online" to listOf(N("ok","Сеть вернулась","Пиксели собрались обратно. Обновляем."),N("ok","Снова в эфире","Загружаем то, что пропустили."),N("ok","Онлайн","Связь есть, можно работать.")),
        "weak" to listOf(N("warn","Сигнал слабый","Данные едут на велосипеде."),N("warn","Медленная сеть","Пиксели грузятся по одному."),N("warn","Связь шалит","Попробуйте подойти к окну.")),
        "server" to listOf(N("bad","Сервер не отвечает","Постучали, в ответ тишина. Повторим позже."),N("bad","Там никого нет","Сервер недоступен. Мы стучимся дальше."),N("bad","Сервер молчит","Возможно, идут работы. Попробуйте позже.")),
        "retry" to listOf(N("warn","Подключаемся…","Ещё одна попытка. Пиксели собираются."),N("warn","Пробуем снова","Следующий запрос через 5 секунд."),N("warn","Ищем сервер","Звоним ещё раз.")),
        "updated" to listOf(N("ok","Данные обновлены","Пиксели свежие."),N("ok","Готово","Список обновлён."),N("ok","Синхронизировано","Новое уже на экране.")),
        "session" to listOf(N("bad","Сессия истекла","Войдите заново, это быстро."),N("bad","Нас попросили представиться","Ключи не приняты. Проверьте api_key и api_secret_key."),N("bad","Вход устарел","Войдите снова, пиксели подождут.")),
        "forbidden" to listOf(N("bad","Доступ закрыт","Возможно, IP телефона не разрешён в кабинете Noros."),N("bad","Не пропустили","Проверьте права ключа."),N("bad","Стоп, пиксель","Запрос отклонён. Напишите в поддержку, если это неожиданно.")),
        "limit" to listOf(N("warn","Притормозите","Лимит запросов, подождите минуту."),N("warn","Пиксели устали","Слишком часто. Повторите чуть позже."),N("warn","Перерыв на минуту","Сервер просит не торопиться.")),
        "loadfail" to listOf(N("bad","Пиксели потерялись","Не удалось загрузить. Потяните вниз."),N("bad","Загрузка сорвалась","Повторите через пару секунд."),N("bad","Пусто, но не по-настоящему","Данные не загрузились. Попробуйте снова.")),
        "savefail" to listOf(N("bad","Не сохранилось","Изменения не записались. Повторите."),N("bad","Запись сорвалась","Проверьте связь и нажмите ещё раз."),N("bad","Пиксель не лёг","Изменения не применены. Попробуйте снова.")),
        "upload" to listOf(N("bad","Чек не ушёл","Файл не отправился. Повторите."),N("bad","Загрузка застряла","Проверьте размер файла и связь."),N("bad","Файл не доехал","Попробуйте отправить ещё раз.")),
    )
    private val EN: Map<String, Pair<String, String>> = mapOf(
        "offline" to ("No connection" to "Showing the last loaded data."),
        "online" to ("Back online" to "Refreshing your data."),
        "weak" to ("Weak connection" to "Things may be slow."),
        "server" to ("Server not responding" to "Try again in a minute."),
        "retry" to ("Reconnecting" to "Trying again."),
        "updated" to ("Up to date" to "Data refreshed."),
        "session" to ("Sign-in failed" to "Check api_key and api_secret_key."),
        "forbidden" to ("Access denied" to "Check the key permissions or the allowed IP."),
        "limit" to ("Too many requests" to "Wait a minute and try again."),
        "loadfail" to ("Could not load" to "Try again."),
        "savefail" to ("Could not save" to "Try again."),
        "upload" to ("Upload failed" to "Try again."),
    )

    @Synchronized
    fun show(key: String, minGapMs: Long = 15000) {
        val now = System.currentTimeMillis()
        if (now - (at[key] ?: 0L) < minGapMs) return
        val l = V[key] ?: return
        var i = l.indices.random()
        if (l.size > 1 && i == last[key]) i = (i + 1) % l.size
        last[key] = i; at[key] = now
        val t = l[i]
        val en = EN[key]
        current = if (Store.lang == "ru" || en == null) Msg(++seq, t.kind, t.title, t.text) else Msg(++seq, t.kind, en.first, en.second)
    }

    private val UPD = listOf(Pair("Пиксели обновились", "Вышла версия %v. Нажмите, чтобы обновить."),Pair("Вышла новая версия", "Нажмите, чтобы обновиться, ключи останутся."),Pair("Свежая сборка", "Версия %v готова. Нажмите, чтобы поставить."))
    private var updLast = -1

    /** Один из трёх текстов об обновлении: заголовок и пояснение с номером версии. */
    fun updateText(version: String): Pair<String, String> {
        var i = UPD.indices.random()
        if (i == updLast) i = (i + 1) % UPD.size
        updLast = i
        val p = UPD[i]
        return if (Store.lang == "ru") p.first.replace("%v", version) to p.second.replace("%v", version)
        else "Update available" to "Version $version is ready. Tap to update."
    }

    /** Плашка «вышло обновление»: по нажатию вызывается action (открывает окно обновления). */
    fun showUpdate(version: String, action: () -> Unit) {
        val t = updateText(version)
        current = Msg(++seq, "ok", t.first, t.second, action)
    }

    fun dismiss() { current = null }

    /** Успешный ответ сервера: после череды сбоев показываем «данные обновлены». */
    fun ok() {
        val had = fails
        fails = 0
        if (had >= 2 && !offline && System.currentTimeMillis() - (at["online"] ?: 0L) > 8000) show("updated", 60000)
    }

    /** Ответ с ошибкой HTTP. */
    fun http(code: Int, method: String, upload: Boolean) {
        fails++
        val key = when {
            code == 401 -> "session"
            code == 403 -> "forbidden"
            code == 429 -> "limit"
            code >= 500 -> "server"
            upload -> "upload"
            method != "GET" -> "savefail"
            else -> "loadfail"
        }
        show(key)
    }

    /** Запрос не дошёл до сервера. */
    fun network() {
        fails++
        if (offline) return
        show(if (fails == 2) "retry" else "server")
    }

    /** Следим за сетью телефона: пропала и вернулась. */
    fun watchNetwork(ctx: Context) {
        val cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val h = Handler(Looper.getMainLooper())
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    h.postDelayed({
                        if (cm.activeNetwork == null && !offline) { offline = true; show("offline", 0) }
                    }, 1500)
                }
                override fun onAvailable(network: Network) {
                    if (offline) { offline = false; fails = 0; show("online", 0) }
                }
            })
        }
    }
}

@Composable
fun NoticeHost() {
    val m = Notices.current
    var shown by remember { mutableStateOf<Notices.Msg?>(null) }
    LaunchedEffect(m?.id) {
        if (m != null) { shown = m; delay(if (m.action != null) 9000 else 4500); if (Notices.current?.id == m.id) Notices.dismiss() }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible = m != null, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            val s = shown
            if (s != null) {
                val c = when (s.kind) { "ok" -> Ok; "warn" -> Warn; "bad" -> Bad; else -> Color(0xFF6B6F8C) }
                Row(
                    Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(pal.surface)
                        .clickable { s.action?.invoke(); Notices.dismiss() }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(3.dp)).background(c), contentAlignment = Alignment.Center) {
                        Text(when (s.kind) { "ok" -> "✓"; "off" -> "×"; else -> "!" }, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(s.title, color = pal.text, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        Text(s.text, color = pal.muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
