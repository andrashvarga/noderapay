package com.nodera.pay

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

private class Slide(val icon: ImageVector, val ru: String, val en: String, val ruText: String, val enText: String, val target: Int)

private val slides = listOf(
    Slide(Icons.Rounded.Key, "Подключите ключи", "Connect your keys", "Введите адрес сервера, api_key и api_secret_key из кабинета Nodera. Без них терминал не работает.", "Enter the server address, api_key and api_secret_key from your Nodera account. The terminal does not work without them.", 4),
    Slide(Icons.Rounded.QrCode2, "Принимайте по QR", "Accept by QR", "Введите сумму, выберите метод — приложение создаст заявку и покажет QR. Клиент сканирует и попадает на платёжную страницу.", "Enter the amount, pick a method — the app creates an order and shows a QR. The customer scans it and lands on the payment page.", 2),
    Slide(Icons.Rounded.Link, "Ссылка на оплату", "Payment link", "Отправьте ссылку клиенту удалённо. Оставьте все методы на выбор клиента или отметьте нужные галочками.", "Send the link remotely. Let the customer choose any method or tick the ones you allow.", 3),
    Slide(Icons.Rounded.Storefront, "Услуги и товары", "Services", "Сохраните услуги с готовой суммой, например «Ремонт ПК — 1000». Покажите карточку клиенту и создайте QR или ссылку.", "Save services with preset prices, e.g. “PC repair — 1000”. Show the card to the customer and create a QR or a link.", 2),
    Slide(Icons.Rounded.Receipt, "Заявки и диспуты", "Orders and disputes", "Следите за статусом активных заявок и архивом. Если клиент оплатил, а платёж не подтверждён — откройте диспут и приложите чек.", "Track active orders and the archive. If the customer paid but it is not confirmed — open a dispute and attach the receipt.", 0),
    Slide(Icons.Rounded.AccountBalanceWallet, "Вывод в USDT", "USDT withdrawals", "Сохраните кошелёк TRC-20 или BEP-20 и выводите баланс целиком или частью.", "Save a TRC-20 or BEP-20 wallet and withdraw all of your balance or part of it.", 1),
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
            LiveBackground(tint = when (pager.currentPage) { 0 -> GradA; 1 -> Accent; 2 -> GradC; 3 -> Warn; 4 -> Ok; else -> GradB })
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(top = 12.dp, bottom = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    slides.indices.forEach { i ->
                        val frac = when { i < pager.currentPage -> 1f; i == pager.currentPage -> progress.value; else -> 0f }
                        Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(BrandBrush))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppLogo(28)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = .1f)).pointerInput(Unit) { detectTapGestures { onClose() } }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, null, tint = Color.White)
                    }
                }
                HorizontalPager(
                    pager, Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                        detectTapGestures(onPress = { paused = true; tryAwaitRelease(); paused = false })
                    },
                ) { i -> SlideView(slides[i], pager.currentPage == i, i) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (paused) tr("Пауза", "Paused") else tr("Удерживайте для паузы", "Hold to pause"), color = Color.White.copy(alpha = .5f), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    val last = pager.currentPage == slides.size - 1
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(BrandBrush).pointerInput(last) {
                            detectTapGestures { if (last) onClose() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
                        }.padding(horizontal = 22.dp, vertical = 12.dp),
                    ) { Text(if (last) tr("Готово", "Done") else tr("Далее", "Next"), color = Color.White, fontWeight = FontWeight.Bold, 1) }
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
        Text(tr(s.ru, s.en), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(tr(s.ruText, s.enText), color = Color.White.copy(alpha = .75f), fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
    }
}

private val navIcons = listOf(Icons.Rounded.Receipt, Icons.Rounded.AccountBalanceWallet, Icons.Rounded.QrCode2, Icons.Rounded.Link, Icons.Rounded.Settings)

@Composable
private fun MockPhone(index: Int, target: Int, p: Float) {
    val W = 232; val H = 300
    // Палец: 0–.2 подъезжает к вкладке, .2–.35 нажатие, .35–.6 едет к элементу на экране, .6–.75 второе нажатие.
    val navX = 23f + 46f * target - 11f
    val navY = H - 30f
    val inX = 116f; val inY = when (index) { 0 -> 146f; 1 -> 190f; 2 -> 180f; 3 -> 176f; 4 -> 112f; else -> 190f }
    val fx = when { p < 0.2f -> lerp(190f, navX, p / 0.2f); p < 0.35f -> navX; p < 0.6f -> lerp(navX, inX, (p - 0.35f) / 0.25f); else -> inX }
    val fy = when { p < 0.2f -> lerp(H + 40f, navY, p / 0.2f); p < 0.35f -> navY; p < 0.6f -> lerp(navY, inY, (p - 0.35f) / 0.25f); else -> inY }
    val tap1 = if (p in 0.2f..0.35f) 1f - kotlin.math.abs((p - 0.275f) / 0.075f) else 0f
    val tap2 = if (p in 0.6f..0.78f) 1f - kotlin.math.abs((p - 0.69f) / 0.09f) else 0f
    val navOn = p > 0.25f
    val done = ((p - 0.62f) / 0.2f).coerceIn(0f, 1f) // «результат» действия на экране
    Box(Modifier.size(W.dp, H.dp).clip(RoundedCornerShape(30.dp)).background(Color(0xFF10132A)).border(2.dp, BrandBrush, RoundedCornerShape(30.dp))) {
        // Шапка.
        Row(Modifier.fillMaxWidth().padding(14.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.logo_mark_dark), null, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.height(8.dp).width(60.dp).clip(CircleShape).background(Color.White.copy(alpha = .25f)))
        }
        // Содержимое раздела.
        Box(Modifier.padding(top = 44.dp, bottom = 58.dp).fillMaxSize().padding(horizontal = 14.dp)) { MockContent(index, p, done) }
        // Нижняя панель.
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(52.dp).background(Color(0xFF181C3A)), verticalAlignment = Alignment.CenterVertically) {
            navIcons.forEachIndexed { i, ic ->
                val on = i == target && navOn
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(if (on) Accent.copy(alpha = .35f) else Color.Transparent), contentAlignment = Alignment.Center) {
                        Icon(ic, null, tint = if (on) Color.White else Color.White.copy(alpha = .45f), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        // Кольцо нажатия и палец.
        val tap = maxOf(tap1, tap2)
        if (tap > 0f) Box(Modifier.offset((fx - 2f).dp, (fy - 2f).dp).size((26f + 14f * (1f - tap)).dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f * tap)))
        Icon(Icons.Rounded.TouchApp, null, tint = Color.White, modifier = Modifier.offset((fx - 4f).dp, (fy - 2f).dp).size(34.dp).scale(1f - 0.12f * tap))
    }
}

@Composable
private fun Bar(w: Int, h: Int = 9, color: Color = Color.White.copy(alpha = .22f), r: Int = 5) =
    Box(Modifier.width(w.dp).height(h.dp).clip(RoundedCornerShape(r.dp)).background(color))

@Composable
private fun MockContent(index: Int, p: Float, done: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxSize()) {
        when (index) {
            0 -> { // Ключи: поля заполняются точками, потом замок.
                Bar(90, 10, Color.White.copy(alpha = .4f))
                repeat(3) { i ->
                    Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = .08f)).padding(horizontal = 9.dp), contentAlignment = Alignment.CenterStart) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { repeat(((p * 1.6f - i * 0.2f).coerceIn(0f, 1f) * 16).toInt()) { Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = .8f))) } }
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
                    Row(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(9.dp)).background(if (on) Accent.copy(alpha = .35f) else Color.White.copy(alpha = .08f)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(Color.White), contentAlignment = Alignment.Center) { if (on) Icon(Icons.Rounded.Check, null, tint = Accent, modifier = Modifier.size(14.dp)) }
                        Spacer(Modifier.width(8.dp)); Bar(70 - i * 10)
                    }
                }
                Row(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(10.dp)).background(BrandBrush), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(if (done > 0.5f) Icons.Rounded.Share else Icons.Rounded.Link, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            3 -> { // Услуги: карточка выбирается и раскрывается.
                repeat(3) { i ->
                    val sel = i == 1 && p > 0.3f
                    Row(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) Accent.copy(alpha = .35f) else Color.White.copy(alpha = .08f)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Storefront, null, tint = if (sel) Color.White else GradA, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp)); Bar(56); Spacer(Modifier.weight(1f)); Bar(26, 9, GradC.copy(alpha = .8f))
                    }
                }
                Row(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = if (done > 0.5f) .2f else .08f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Rounded.Visibility, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
            4 -> { // Заявки: строки со статусами, крестик отмены и скрепка чека.
                repeat(3) { i ->
                    val paid = i == 0
                    Row(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = .08f)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Bar(44); Spacer(Modifier.weight(1f))
                        Box(Modifier.clip(RoundedCornerShape(50)).background((if (paid) Ok else Warn).copy(alpha = .3f)).padding(horizontal = 7.dp, vertical = 2.dp)) { Box(Modifier.size(width = 22.dp, height = 5.dp).clip(CircleShape).background(if (paid) Ok else Warn)) }
                        if (!paid) { Spacer(Modifier.width(6.dp)); Box(Modifier.size(20.dp).clip(CircleShape).background(Bad.copy(alpha = if (i == 1 && done > 0.4f) .9f else .3f)), contentAlignment = Alignment.Center) { Icon(if (i == 1) Icons.Rounded.Close else Icons.Rounded.AttachFile, null, tint = Color.White, modifier = Modifier.size(13.dp)) } }
                    }
                }
            }
            else -> { // Вывод: кошелек, сумма растет, стрелка.
                Row(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(10.dp)).background(Accent.copy(alpha = .3f)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccountBalanceWallet, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Bar(70)
                }
                Text(String.format(java.util.Locale.US, "%.0f USDT", 250f * ((p * 1.5f).coerceIn(0f, 1f))), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Row(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(10.dp)).background(if (done > 0.5f) Ok else Accent), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(if (done > 0.5f) Icons.Rounded.Check else Icons.Rounded.ArrowUpward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
