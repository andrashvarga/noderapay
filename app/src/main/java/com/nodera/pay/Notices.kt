package com.nodera.pay

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
        "offline" to listOf(N("off","Интернет ушёл за хлебом","Заявки на паузе. Как вернётся, подтянем свежее."),N("off","Связь пропала","Данные на сервере, мы просто их сейчас не видим."),N("off","Сеть сделала перерыв","Показываем то, что успели загрузить.")),
        "online" to listOf(N("ok","Интернет вернулся","Как будто и не уходил. Обновляем заявки."),N("ok","Мы снова на связи","Сеть на месте, подтягиваем свежее."),N("ok","Вернулись в онлайн","Он просто выходил подышать.")),
        "weak" to listOf(N("warn","Сеть еле дышит","Всё работает, но медленно. Не нажимайте дважды."),N("warn","Слабый сигнал","Запрос идёт пешком. Он ещё в пути."),N("warn","Связь на тоненького","Подождите немного или найдите сеть получше.")),
        "server" to listOf(N("bad","Сервер задумался","Не отвечает. Попробуйте через минуту."),N("bad","Касса на минутку закрыта","Сервер недоступен. Дело не в вашем телефоне."),N("bad","Сервер ушёл на перекур","Попробуйте ещё раз чуть позже.")),
        "retry" to listOf(N("warn","Подключаемся снова","Пробуем достучаться, попытка 2 из 5."),N("warn","Стучимся ещё раз","Связь с сервером восстанавливается."),N("warn","Переподключение","Чиним мостик между вами и сервером.")),
        "updated" to listOf(N("ok","Всё свежее","Заявки обновлены только что."),N("ok","Обновили","Новые данные уже на экране."),N("ok","Подтянули новое","Список заявок актуален.")),
        "session" to listOf(N("bad","Сессия закончилась","Попросим войти заново, это быстро."),N("bad","Время вышло","Для безопасности нужно войти ещё раз."),N("bad","Ключи не подошли","Проверьте api_key и api_secret_key, возможно, опечатка.")),
        "forbidden" to listOf(N("bad","Сюда нельзя","Нет доступа. Возможно, IP телефона не разрешён в кабинете."),N("bad","Дверь закрыта","Доступ запрещён. Проверьте права ключа."),N("bad","Охрана не пустила","Запрос отклонён. Если это неожиданно, напишите в поддержку.")),
        "limit" to listOf(N("warn","Не так быстро","Слишком много запросов. Передохните минуту."),N("warn","Тише едешь","Лимит запросов. Попробуйте через минуту."),N("warn","Сервер просит паузу","Запросов слишком много, подождите чуть-чуть.")),
        "loadfail" to listOf(N("bad","Данные не приехали","Не удалось загрузить. Потяните вниз, чтобы повторить."),N("bad","Список потерялся по дороге","Загрузка не удалась. Повторите."),N("bad","Не вышло загрузить","Что-то пошло не так. Попробуйте ещё раз.")),
        "savefail" to listOf(N("bad","Сохранить не вышло","Изменения не записались. Нажмите ещё раз."),N("bad","Не записалось","Ничего не сохранилось. Проверьте связь и повторите."),N("bad","Сохранение споткнулось","Изменения не применены. Повторите.")),
        "upload" to listOf(N("bad","Чек не долетел","Файл не отправился. Попробуйте ещё раз."),N("bad","Файл застрял","Загрузка не удалась. Проверьте размер и связь."),N("bad","Чек остался у вас","Не отправилось. Повторите.")),
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

    private val UPD = listOf(Pair("Вышло обновление %v", "Нажмите, чтобы обновиться. Данные и ключи останутся на месте."),Pair("Подвезли новую версию", "Версия %v готова. Нажмите, чтобы поставить."),Pair("Приложение обросло обновкой", "Версия %v ждёт вас. Нажмите, чтобы посмотреть, что нового."))
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
                    Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(pal.surface)
                        .clickable { s.action?.invoke(); Notices.dismiss() }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(c), contentAlignment = Alignment.Center) {
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
