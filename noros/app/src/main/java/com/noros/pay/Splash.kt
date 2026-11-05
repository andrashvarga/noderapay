package com.noros.pay

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay

/** Мягкий фон: поверхность в цвете Noros и два едва заметных плывущих свечения. */
@Composable
fun SoftBackground(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "softbg")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(8000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val p = pal
    Canvas(modifier.fillMaxSize().background(p.bg)) {
        val r = size.maxDimension * 0.6f
        val k = if (p.dark) 0.22f else 0.16f
        val c1 = Offset(size.width * lerp(0.1f, 0.6f, a), size.height * lerp(0.1f, 0.3f, a))
        val c2 = Offset(size.width * lerp(0.95f, 0.4f, a), size.height * lerp(0.95f, 0.7f, a))
        drawCircle(Brush.radialGradient(listOf(Accent.copy(alpha = k), Color.Transparent), c1, r), r, c1)
        drawCircle(Brush.radialGradient(listOf(GradC.copy(alpha = k * 0.7f), Color.Transparent), c2, r), r, c2)
    }
}

/**
 * Пиксельный логотип Noros «собирается» из блоков: каждый элемент выезжает на своё место по очереди.
 */
@Composable
private fun PixelLogo(progress: Float, modifier: Modifier = Modifier) {
    val paths = remember {
        NOROS_PATHS.map { (d, eo) -> PathParser().parsePathString(d).toPath().also { if (eo) it.fillType = PathFillType.EvenOdd } }
    }
    val order = remember { listOf(6, 0, 1, 2, 4, 3, 5) } // N, O, R, O, S: слева направо
    val color = Accent
    Canvas(modifier) {
        val s = size.width / NOROS_VB_W
        paths.forEachIndexed { idx, p ->
            val pos = order.indexOf(idx).coerceAtLeast(0)
            val local = ((progress * (order.size + 1.5f) - pos) / 1.5f).coerceIn(0f, 1f)
            if (local > 0f) {
                val dy = (1f - local) * 10.dp.toPx()
                withTransform({ translate(0f, dy); scale(s, s, Offset.Zero) }) { drawPath(p, color.copy(alpha = local)) }
            }
        }
    }
}

/** Полоса загрузки из пикселей, как логотип. */
@Composable
private fun PixelBar(progress: Float, modifier: Modifier = Modifier) {
    val color = Accent
    val track = pal.line
    Canvas(modifier) {
        val n = 12
        val gap = 4.dp.toPx()
        val cell = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val on = progress * n > i
            drawRoundRect(if (on) color else track, Offset(i * (cell + gap), 0f), Size(cell, size.height), CornerRadius(2.dp.toPx()))
        }
    }
}

/** Заставка при запуске: логотип Noros собирается из пикселей, под ним «Pay» и «Платёжный терминал». */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val appear by animateFloatAsState(1f, tween(1400, delayMillis = 900), label = "appear")
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(2600, easing = LinearEasing))
        delay(450)
        onDone()
    }
    Box(Modifier.fillMaxSize()) {
        SoftBackground()
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            PixelLogo((progress.value * 1.25f).coerceAtMost(1f), Modifier.width(220.dp).height((220f * NOROS_VB_H / NOROS_VB_W).dp))
            Spacer(Modifier.height(14.dp))
            Text("Pay", color = pal.text, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = 2.sp, modifier = Modifier.alpha(appear))
            Spacer(Modifier.height(6.dp))
            Text(tr("Платёжный терминал", "Payment terminal"), color = pal.muted, fontSize = 15.sp, letterSpacing = 1.sp, modifier = Modifier.alpha(appear))
            Spacer(Modifier.height(36.dp))
            PixelBar(progress.value, Modifier.width(200.dp).height(8.dp))
        }
    }
}
