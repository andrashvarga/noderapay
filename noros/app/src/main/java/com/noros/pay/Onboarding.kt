package com.noros.pay

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private class Slide(val icon: ImageVector, val ru: String, val en: String, val ruText: String, val enText: String, val target: Int, val mock: Int)

private val slides = listOf(
    Slide(Icons.Rounded.Key, "Подключите ключи", "Connect your keys", "Введите api_key и api_secret_key из кабинета my.noros.org. Без них терминал не работает. Ключи только удаляются и вводятся заново.", "Enter the api_key and api_secret_key from your my.noros.org account. The terminal does not work without them. Keys can only be deleted and re-entered.", 3, 0),
    Slide(Icons.Rounded.QrCode2, "QR и ссылка в одном", "QR and link in one", "Введите сумму: заявка покажет QR, таймер и кнопку «Поделиться ссылкой». Метод оплаты клиент выберет на платёжной странице Noros. Валюта берётся из вашего кабинета.", "Enter the amount: the order shows a QR, a timer and a “Share link” button. The customer picks the payment method on the Noros payment page. The currency comes from your account.", 2, 1),
    Slide(Icons.Rounded.Storefront, "Услуги и товары", "Services", "Сохраните услуги с готовой суммой, например «Ремонт ПК — 1000». Покажите карточку клиенту и создайте заявку.", "Save services with preset prices, e.g. “PC repair — 1000”. Show the card to the customer and create an order.", 2, 3),
    Slide(Icons.Rounded.Receipt, "Заявки и диспуты", "Orders and disputes", "Здесь заявки, созданные в приложении. Листайте вкладки свайпом, тяните вниз для обновления. Если клиент оплатил, а платёж не подтверждён — откройте диспут и приложите чек. Сверху видно не больше трёх активных заявок, остальные ждут в архиве; из архива можно выгрузить CSV.", "Orders created in the app. Swipe between tabs and pull down to refresh. If the customer paid but it is not confirmed — open a dispute and attach the receipt. At most three active orders show on top, the rest wait in the archive; the archive can be exported to CSV.", 0, 4),
    Slide(Icons.Rounded.NorthEast, "Выплаты клиентам", "Payouts to customers", "Нажмите «Новая выплата», введите сумму, карту или телефон, банк и ФИО получателя и подтвердите. Карточка окрашена по статусу: в очереди, выполняется, выплачено, отменена. Пока выплата в очереди, её можно отменить. Активные и архив лежат в одном разделе, из архива выгружается CSV.", "Tap “New payout”, enter the amount, card or phone, bank and recipient name, then confirm. The card is coloured by status: queued, in progress, paid out, canceled. A queued payout can be canceled. Active and archive live in one section; the archive exports to CSV.", 1, 5),
)

private const val SLIDE_MS = 5500

/** Инструкция: слайды листаются свайпом и сами, пауза при нажатии на слайд. */
@Composable
fun OnboardingDialog(onClose: () -> Unit) {
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    var paused by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(pager.currentPage, paused) {
        if (paused) return@LaunchedEffect
        // Продолжаем с того места, где остановились, чтобы пауза не сбрасывала полоску.
        progress.animateTo(1f, tween(((1f - progress.value) * SLIDE_MS).toInt().coerceAtLeast(1), easing = LinearEasing))
        if (pager.currentPage < slides.size - 1) pager.animateScrollToPage(pager.currentPage + 1) else onClose()
    }
    LaunchedEffect(pager.currentPage) { progress.snapTo(0f) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            SoftBackground()
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(top = 12.dp, bottom = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    slides.indices.forEach { i ->
                        val frac = when { i < pager.currentPage -> 1f; i == pager.currentPage -> progress.value; else -> 0f }
                        Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(pal.line)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Accent))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrandTitle(14)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(40.dp).clip(CircleShape).background(pal.surface2).pointerInput(Unit) { detectTapGestures { onClose() } }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, null, tint = pal.text)
                    }
                }
                HorizontalPager(
                    pager, Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                        detectTapGestures(onPress = { paused = true; tryAwaitRelease(); paused = false })
                    },
                ) { i -> SlideView(slides[i], pager.currentPage == i, slides[i].mock) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (paused) tr("Пауза", "Paused") else tr("Удерживайте для паузы", "Hold to pause"), color = pal.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    val last = pager.currentPage == slides.size - 1
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(Accent).pointerInput(last) {
                            detectTapGestures { if (last) onClose() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
                        }.padding(horizontal = 22.dp, vertical = 12.dp),
                    ) { Text(if (last) tr("Готово", "Done") else tr("Далее", "Next"), color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** Макет телефона с приложением: палец показывает, куда нажимать; внутри — оживающий экран нужного раздела. */
@Composable
private fun SlideView(s: Slide, active: Boolean, index: Int) {
    val t = rememberInfiniteTransition(label = "slide")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "phase")
    val appear by animateFloatAsState(if (active) 1f else 0f, tween(500), label = "appear")
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.scale(0.92f + 0.08f * appear).alpha(0.4f + 0.6f * appear)) { MockPhone(index, s.target, phase) }
        Spacer(Modifier.height(22.dp))
        Text(tr(s.ru, s.en), color = pal.text, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(tr(s.ruText, s.enText), color = pal.muted, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
    }
}

private val navIcons = listOf(Icons.Rounded.Receipt, Icons.Rounded.NorthEast, Icons.Rounded.QrCode2, Icons.Rounded.Settings)

@Composable
private fun MockPhone(index: Int, target: Int, p: Float) {
    val W = 232; val H = 300
    // Палец: 0–.2 подъезжает к вкладке, .2–.35 нажатие, .35–.6 едет к элементу на экране, .6–.75 второе нажатие.
    val navX = 5f + (W - 10f) / navIcons.size * (target + 0.5f) - 11f
    val navY = H - 30f
    val inX = 116f; val inY = when (index) { 0 -> 146f; 1 -> 190f; 2 -> 180f; 3 -> 176f; 4 -> 112f; else -> 190f }
    val fx = when { p < 0.2f -> lerp(190f, navX, p / 0.2f); p < 0.35f -> navX; p < 0.6f -> lerp(navX, inX, (p - 0.35f) / 0.25f); else -> inX }
    val fy = when { p < 0.2f -> lerp(H + 40f, navY, p / 0.2f); p < 0.35f -> navY; p < 0.6f -> lerp(navY, inY, (p - 0.35f) / 0.25f); else -> inY }
    val tap1 = if (p in 0.2f..0.35f) 1f - kotlin.math.abs((p - 0.275f) / 0.075f) else 0f
    val tap2 = if (p in 0.6f..0.78f) 1f - kotlin.math.abs((p - 0.69f) / 0.09f) else 0f
    val navOn = p > 0.25f
    val done = ((p - 0.62f) / 0.2f).coerceIn(0f, 1f) // «результат» действия на экране
    Box(Modifier.size(W.dp, H.dp).shadow(18.dp, RoundedCornerShape(34.dp), ambientColor = Color.Black.copy(alpha = .25f), spotColor = Color.Black.copy(alpha = .4f)).background(Color(0xFF14161E), RoundedCornerShape(34.dp)).padding(5.dp).clip(RoundedCornerShape(29.dp)).background(pal.bg)) {
        // Шапка.
        Row(Modifier.fillMaxWidth().padding(14.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandTitle(8)
        }
        // Содержимое раздела.
        Box(Modifier.padding(top = 44.dp, bottom = 58.dp).fillMaxSize().padding(horizontal = 14.dp)) { MockContent(index, p, done) }
        // Нижняя панель.
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(52.dp).background(pal.surface), verticalAlignment = Alignment.CenterVertically) {
            navIcons.forEachIndexed { i, ic ->
                val on = i == target && navOn
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(if (on) Accent.copy(alpha = .16f) else Color.Transparent), contentAlignment = Alignment.Center) {
                        Icon(ic, null, tint = if (on) Accent else pal.muted, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        // Кольцо нажатия и палец.
        val tap = maxOf(tap1, tap2)
        if (tap > 0f) Box(Modifier.offset((fx - 2f).dp, (fy - 2f).dp).size((26f + 14f * (1f - tap)).dp).clip(CircleShape).background(Accent.copy(alpha = 0.3f * tap)))
        Icon(Icons.Rounded.TouchApp, null, tint = pal.text, modifier = Modifier.offset((fx - 4f).dp, (fy - 2f).dp).size(34.dp).scale(1f - 0.12f * tap))
    }
}

@Composable
private fun Bar(w: Int, h: Int = 9, color: Color = Color(0x4D8D92B0), r: Int = 5) =
    Box(Modifier.width(w.dp).height(h.dp).clip(RoundedCornerShape(r.dp)).background(color))

@Composable
private fun MockContent(index: Int, p: Float, done: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxSize()) {
        when (index) {
            0 -> { // Ключи: поля заполняются точками, потом замок.
                Bar(90, 10, Color(0x808D92B0))
                repeat(3) { i ->
                    Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(9.dp)).background(pal.surface2).padding(horizontal = 9.dp), contentAlignment = Alignment.CenterStart) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(((p * 1.6f - i * 0.2f).coerceIn(0f, 1f) * 16).toInt()) { Box(Modifier.size(5.dp).clip(CircleShape).background(pal.text.copy(alpha = .75f))) } }
                    }
                }
                Row(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(10.dp)).background(if (done > 0.5f) Ok else Accent), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(if (done > 0.5f) Icons.Rounded.Lock else Icons.Rounded.Key, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            1 -> { // QR: ячейки появляются, бежит линия сканирования.
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(104.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)) {
                        val n = 9; val c = size.width / n
                        for (i in 0 until n) for (j in 0 until n) {
                            val corner = (i < 3 && j < 3) || (i > 5 && j < 3) || (i < 3 && j > 5)
                            val on = corner || ((i * 7 + j * 13 + i * j) % 3 == 0)
                            val shown = ((j * n + i) / (n * n).toFloat()) < (0.15f + done * 0.85f)
                            if (on && shown) drawRect(Color(0xFF10121C), Offset(i * c + 1, j * c + 1), Size(c - 2, c - 2))
                        }
                        val y = size.height * ((p * 2f) % 1f)
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Accent.copy(alpha = .6f), Color.Transparent), y - 20f, y + 20f), Offset(0f, y - 20f), Size(size.width, 40f))
                    }
                }
            }
            2 -> { // Ссылка: методы отмечаются галочками, ссылка готова.
                repeat(3) { i ->
                    val on = ((p * 3f) > i + 0.4f) && i < 3
                    Row(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(9.dp)).background(if (on) Accent.copy(alpha = .35f) else pal.surface2).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(pal.surface).border(1.dp, pal.line, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) { if (on) Icon(Icons.Rounded.Check, null, tint = Accent, modifier = Modifier.size(14.dp)) }
                        Spacer(Modifier.width(8.dp)); Bar(70 - i * 10)
                    }
                }
                Row(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(10.dp)).background(Accent), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(if (done > 0.5f) Icons.Rounded.Share else Icons.Rounded.Link, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            3 -> { // Услуги: карточка выбирается и раскрывается.
                repeat(3) { i ->
                    val sel = i == 1 && p > 0.3f
                    Row(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) Accent.copy(alpha = .35f) else pal.surface2).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Storefront, null, tint = if (sel) Accent else GradA, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp)); Bar(56); Spacer(Modifier.weight(1f)); Bar(26, 9, GradC.copy(alpha = .8f))
                    }
                }
                Row(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(9.dp)).background(if (done > 0.5f) Accent.copy(alpha = .18f) else pal.surface2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Rounded.Visibility, null, tint = pal.text, modifier = Modifier.size(15.dp))
                }
            }
            4 -> { // Заявки: строки со статусами, крестик отмены и скрепка чека.
                repeat(3) { i ->
                    val paid = i == 0
                    Row(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(10.dp)).background(pal.surface2).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Bar(44); Spacer(Modifier.weight(1f))
                        Box(Modifier.clip(RoundedCornerShape(50)).background((if (paid) Ok else Warn).copy(alpha = .3f)).padding(horizontal = 7.dp, vertical = 2.dp)) { Box(Modifier.size(width = 22.dp, height = 5.dp).clip(CircleShape).background(if (paid) Ok else Warn)) }
                        if (!paid) { Spacer(Modifier.width(6.dp)); Box(Modifier.size(20.dp).clip(CircleShape).background(Bad.copy(alpha = if (i == 1 && done > 0.4f) .9f else .55f)), contentAlignment = Alignment.Center) { Icon(if (i == 1) Icons.Rounded.Close else Icons.Rounded.AttachFile, null, tint = Color.White, modifier = Modifier.size(13.dp)) } }
                    }
                }
            }
            else -> { // Выплата: форма, карточка меняет цвет по статусу.
                val col = if (done > 0.5f) Ok else Accent
                repeat(2) { Box(Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(9.dp)).background(pal.surface2)) }
                Row(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(col.copy(alpha = .18f)).border(1.5.dp, col.copy(alpha = .7f), RoundedCornerShape(12.dp)).padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (done > 0.5f) Icons.Rounded.CheckCircle else Icons.Rounded.NorthEast, null, tint = col, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Bar(50); Spacer(Modifier.weight(1f)); Bar(26, 9, col.copy(alpha = .8f))
                }
            }
        }
    }
}
