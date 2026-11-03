package com.nodera.pay

import androidx.compose.animation.core.*
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

private class Slide(val icon: ImageVector, val ru: String, val en: String, val ruText: String, val enText: String)

private val slides = listOf(
    Slide(Icons.Rounded.Key, "Подключите ключи", "Connect your keys", "Введите адрес сервера, api_key и api_secret_key из кабинета Nodera. Без них терминал не работает.", "Enter the server address, api_key and api_secret_key from your Nodera account. The terminal does not work without them."),
    Slide(Icons.Rounded.QrCode2, "Принимайте по QR", "Accept by QR", "Введите сумму, выберите метод — приложение создаст заявку и покажет QR. Клиент сканирует и попадает на платёжную страницу.", "Enter the amount, pick a method — the app creates an order and shows a QR. The customer scans it and lands on the payment page."),
    Slide(Icons.Rounded.Link, "Ссылка на оплату", "Payment link", "Отправьте ссылку клиенту удалённо. Оставьте все методы на выбор клиента или отметьте нужные галочками.", "Send the link remotely. Let the customer choose any method or tick the ones you allow."),
    Slide(Icons.Rounded.Storefront, "Услуги и товары", "Services", "Сохраните услуги с готовой суммой, например «Ремонт ПК — 1000». Покажите карточку клиенту и создайте QR или ссылку.", "Save services with preset prices, e.g. “PC repair — 1000”. Show the card to the customer and create a QR or a link."),
    Slide(Icons.Rounded.Receipt, "Заявки и диспуты", "Orders and disputes", "Следите за статусом активных заявок и архивом. Если клиент оплатил, а платёж не подтверждён — откройте диспут и приложите чек.", "Track active orders and the archive. If the customer paid but it is not confirmed — open a dispute and attach the receipt."),
    Slide(Icons.Rounded.AccountBalanceWallet, "Вывод в USDT", "USDT withdrawals", "Сохраните кошелёк TRC-20 или BEP-20 и выводите баланс целиком или частью.", "Save a TRC-20 or BEP-20 wallet and withdraw all of your balance or part of it."),
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
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0D18))) {
            Column(Modifier.fillMaxSize().padding(top = 18.dp, bottom = 24.dp)) {
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
                ) { i -> SlideView(slides[i], pager.currentPage == i) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (paused) tr("Пауза", "Paused") else tr("Удерживайте для паузы", "Hold to pause"), color = Color.White.copy(alpha = .5f), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    val last = pager.currentPage == slides.size - 1
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(BrandBrush).pointerInput(last) {
                            detectTapGestures { if (last) onClose() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
                        }.padding(horizontal = 22.dp, vertical = 12.dp),
                    ) { Text(if (last) tr("Готово", "Done") else tr("Далее", "Next"), color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun SlideView(s: Slide, active: Boolean) {
    val t = rememberInfiniteTransition(label = "slide")
    val pulse by t.animateFloat(0.94f, 1.08f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val ring by t.animateFloat(0.9f, 1.55f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "ring")
    val ringAlpha by t.animateFloat(0.45f, 0f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "ringAlpha")
    val tilt by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tilt")
    val appear by animateFloatAsState(if (active) 1f else 0f, tween(500), label = "appear")
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(120.dp).scale(ring).clip(CircleShape).background(Accent.copy(alpha = ringAlpha)))
            Box(Modifier.size(120.dp).scale(pulse).clip(CircleShape).background(BrandBrush), contentAlignment = Alignment.Center) {
                Icon(s.icon, null, tint = Color.White, modifier = Modifier.size(58.dp).rotate(tilt))
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(tr(s.ru, s.en), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, textAlign = TextAlign.Center, modifier = Modifier.scale(0.9f + 0.1f * appear))
        Spacer(Modifier.height(12.dp))
        Text(tr(s.ruText, s.enText), color = Color.White.copy(alpha = .72f), fontSize = 16.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
    }
}
