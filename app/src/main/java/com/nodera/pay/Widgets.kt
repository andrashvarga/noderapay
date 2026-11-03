package com.nodera.pay

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp

val FINAL_STATUSES = listOf("completed", "canceled", "failed", "expired")

/** Круговой таймер: дуга сокращается по мере того, как уходит время; цвет желтеет и краснеет к концу. */
@Composable
fun TimerRing(remainingMs: Long, totalMs: Long, size: Dp, stroke: Dp = 5.dp, textSize: Int = 14) {
    val frac = if (totalMs <= 0) 0f else (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)
    val anim by animateFloatAsState(frac, tween(900, easing = LinearEasing), label = "ring")
    val color = when { frac > .35f -> Accent; frac > .15f -> Warn; else -> Bad }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx(); val inset = sw / 2
            val tl = Offset(inset, inset); val sz = Size(this.size.width - sw, this.size.height - sw)
            drawArc(color.copy(alpha = .16f), 0f, 360f, false, tl, sz, style = Stroke(sw))
            drawArc(color, -90f, 360f * anim, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text(clock(remainingMs), color = pal.text, fontWeight = FontWeight.ExtraBold, fontSize = textSize.sp)
    }
}

/** Итог заявки: круг с галочкой или крестиком «выпрыгивает» пружиной. */
@Composable
fun ResultBadge(ok: Boolean, size: Dp = 84.dp) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val s by animateFloatAsState(if (go) 1f else 0f, spring(dampingRatio = .45f, stiffness = 220f), label = "pop")
    val color = if (ok) Ok else Bad
    Box(Modifier.size(size).scale(s), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(color.copy(alpha = .16f)))
        Box(Modifier.fillMaxSize(0.72f).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(if (ok) Icons.Rounded.Check else Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.fillMaxSize(0.62f))
        }
    }
}

/** Ход заявки: создана → ожидает оплаты → проверка → результат. Текущий шаг пульсирует. */
@Composable
fun StatusSteps(status: String) {
    val failed = status in listOf("canceled", "failed", "expired")
    val cur = when (status) { "created", "offer", "accepted" -> 1; "proof" -> 2; "completed", "canceled", "failed", "expired" -> 3; else -> 1 }
    val labels = listOf(
        tr("Создана", "Created"), tr("Ждём оплату", "Awaiting"), tr("Проверка", "Checking"),
        if (failed) tr("Отмена", "Canceled") else tr("Успешно", "Success"),
    )
    val t = rememberInfiniteTransition(label = "steps")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "p")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        labels.forEachIndexed { i, l ->
            val done = i < cur || (i == 3 && cur == 3)
            val isCur = i == cur && cur < 3
            val col = if (i == 3 && cur == 3) (if (failed) Bad else Ok) else Accent
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(3.dp).background(if (i == 0) Color.Transparent else if (i <= cur) col.copy(alpha = .7f) else pal.line))
                    Box(
                        Modifier.size(if (isCur) (24 + 3 * pulse).dp else 24.dp).clip(CircleShape)
                            .background(if (done) col else if (isCur) col.copy(alpha = .18f) else pal.surface2)
                            .then(if (isCur) Modifier.border(2.dp, col, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) Icon(if (i == 3 && failed) Icons.Rounded.Close else Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                    Box(Modifier.weight(1f).height(3.dp).background(if (i == 3) Color.Transparent else if (i < cur) col.copy(alpha = .7f) else pal.line))
                }
                Spacer(Modifier.height(6.dp))
                Text(l, fontSize = 11.sp, color = if (done || isCur) pal.text else pal.muted, fontWeight = if (isCur || (i == 3 && cur == 3)) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

/** Пустой раздел: логотип-перекати-поле катится по пустыне слева направо и начинает заново. */
@Composable
fun Tumbleweed(title: String, hint: String) {
    val t = rememberInfiniteTransition(label = "weed")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(7500, easing = LinearEasing)), label = "x")
    val sand = if (pal.dark) Color(0xFF3A3326) else Color(0xFFE9DCC0)
    val sand2 = if (pal.dark) Color(0xFF2B261C) else Color(0xFFDCCBA5)
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(150.dp)) {
            val w = maxWidth
            val wd = 54.dp
            val x = lerp(-wd.value, w.value + wd.value, p).dp
            val bounce = (kotlin.math.abs(kotlin.math.sin(p * Math.PI * 7)) * 22).toFloat()
            Canvas(Modifier.fillMaxSize()) {
                val cw = this.size.width; val ch = this.size.height
                val gy = ch * 0.78f
                val d1 = Path().apply {
                    moveTo(0f, ch); lineTo(0f, gy + 6.dp.toPx())
                    var xx = 0f
                    while (xx <= cw) { lineTo(xx, gy + 6.dp.toPx() + 7.dp.toPx() * kotlin.math.sin(xx / 70.dp.toPx())); xx += 6.dp.toPx() }
                    lineTo(cw, ch); close()
                }
                drawPath(d1, sand2)
                val d2 = Path().apply {
                    moveTo(0f, ch); lineTo(0f, gy + 22.dp.toPx())
                    var xx = 0f
                    while (xx <= cw) { lineTo(xx, gy + 22.dp.toPx() + 6.dp.toPx() * kotlin.math.sin(xx / 45.dp.toPx() + 1.5f)); xx += 6.dp.toPx() }
                    lineTo(cw, ch); close()
                }
                drawPath(d2, sand)
                for (i in 1..4) {
                    val dx = (x.value - i * 14f - wd.value / 2f).dp.toPx()
                    drawCircle(sand2.copy(alpha = .8f - i * .17f), (6 - i).dp.toPx(), Offset(dx, gy + 4.dp.toPx() - i * 2.dp.toPx()))
                }
            }
            // Тень и сам знак.
            Box(Modifier.offset(x - wd / 2 + 6.dp, 150.dp * 0.78f - 3.dp).size(wd - 12.dp, 8.dp).clip(CircleShape).background(Color.Black.copy(alpha = .13f)))
            Image(
                painterResource(R.drawable.logo_mark), null,
                Modifier.offset(x - wd / 2, 150.dp * 0.78f - wd - bounce.dp + 4.dp).size(wd).rotate(p * 720f),
                contentScale = ContentScale.Fit,
            )
        }
        Text(title, fontWeight = FontWeight.Bold, color = pal.text, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(hint, color = pal.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}
