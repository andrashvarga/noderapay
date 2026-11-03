package com.nodera.pay

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.delay

/** Живой фон: два светящихся градиентных пятна медленно плывут, как на сайте Nodera. */
@Composable
fun LiveBackground(modifier: Modifier = Modifier, tint: Color = Accent) {
    val t = rememberInfiniteTransition(label = "bg")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    Canvas(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B0D18), Color(0xFF151A36))))) {
        val r = size.maxDimension * 0.55f
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = .38f), Color.Transparent), Offset(size.width * lerp(0.1f, 0.8f, a), size.height * lerp(0.15f, 0.4f, b)), r), r, Offset(size.width * lerp(0.1f, 0.8f, a), size.height * lerp(0.15f, 0.4f, b)))
        drawCircle(Brush.radialGradient(listOf(GradC.copy(alpha = .26f), Color.Transparent), Offset(size.width * lerp(0.9f, 0.2f, b), size.height * lerp(0.9f, 0.55f, a)), r), r, Offset(size.width * lerp(0.9f, 0.2f, b), size.height * lerp(0.9f, 0.55f, a)))
        // Плывущие «частицы»: тонкие точки.
        for (i in 0 until 14) {
            val x = (i * 0.137f + 0.05f) % 1f
            val y = ((i * 0.291f) + a * 0.06f * (if (i % 2 == 0) 1 else -1)) % 1f
            drawCircle(Color.White.copy(alpha = 0.10f + 0.06f * (i % 3)), 1.5.dp.toPx() + (i % 3), Offset(size.width * x, size.height * y))
        }
    }
}

/** Платёжный терминал: карта въезжает в слот, на экране идёт загрузка, клавиши подсвечиваются, в конце галочка. */
@Composable
fun TerminalAnim(progress: Float, wave: Float, modifier: Modifier = Modifier) {
    Box(modifier.size(200.dp, 270.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val dp = 1.dp.toPx()
            fun rr(x: Float, y: Float, w: Float, h: Float, r: Float, brush: Brush? = null, color: Color = Color.Unspecified, stroke: Float = 0f) {
                if (brush != null && stroke == 0f) drawRoundRect(brush, Offset(x * dp, y * dp), Size(w * dp, h * dp), CornerRadius(r * dp))
                else if (brush != null) drawRoundRect(brush, Offset(x * dp, y * dp), Size(w * dp, h * dp), CornerRadius(r * dp), style = Stroke(stroke * dp))
                else drawRoundRect(color, Offset(x * dp, y * dp), Size(w * dp, h * dp), CornerRadius(r * dp))
            }
            // Волны бесконтактной оплаты над терминалом.
            val waveA = ((progress - 0.45f) / 0.2f).coerceIn(0f, 1f)
            for (i in 0 until 3) {
                val ph = (wave + i / 3f) % 1f
                val rad = (16 + ph * 34) * dp
                drawArc(Brush.linearGradient(listOf(GradA, GradC)), 235f, 70f, false, Offset(100 * dp - rad, 34 * dp - rad), Size(rad * 2, rad * 2),
                    alpha = (1f - ph) * 0.9f * waveA, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            // Карта въезжает сверху в слот (рисуется до корпуса, чтобы нижняя часть пряталась).
            val cp = FastOutSlowInEasing.transform((progress / 0.4f).coerceIn(0f, 1f))
            rr(52f, lerp(-70f, 40f, cp), 96f, 62f, 9f, BrandBrush)
            rr(60f, lerp(-70f, 40f, cp) + 14f, 22f, 15f, 3f, color = Color.White.copy(alpha = .55f))
            // Корпус терминала.
            rr(22f, 62f, 156f, 202f, 22f, Brush.verticalGradient(listOf(Color(0xFF232A55), Color(0xFF12162E))))
            rr(22f, 62f, 156f, 202f, 22f, BrandBrush, stroke = 1.6f)
            rr(60f, 66f, 80f, 4f, 2f, color = Color.White.copy(alpha = .25f)) // слот
            // Экран.
            rr(36f, 82f, 128f, 74f, 12f, color = Color(0xFF0B0F24))
            rr(36f, 82f, 128f, 74f, 12f, Brush.linearGradient(listOf(GradA.copy(alpha = .6f), GradC.copy(alpha = .6f))), stroke = 1f)
            // Полоса загрузки на экране.
            val bar = ((progress - 0.35f) / 0.5f).coerceIn(0f, 1f)
            rr(52f, 136f, 96f, 6f, 3f, color = Color.White.copy(alpha = .12f))
            rr(52f, 136f, 96f * bar, 6f, 3f, BrandBrush)
            // Клавиатура 3x3: подсвечивается по очереди.
            val lit = (progress * 12f).toInt()
            for (i in 0 until 9) {
                val cx = (66 + (i % 3) * 34) * dp
                val cy = (184 + (i / 3) * 26) * dp
                val on = i == lit % 9 && progress < 0.9f
                drawCircle(if (on) Brush.linearGradient(listOf(GradA, GradC), Offset(cx - 10 * dp, cy - 10 * dp), Offset(cx + 10 * dp, cy + 10 * dp)) else Brush.linearGradient(listOf(Color(0xFF2B3262), Color(0xFF2B3262))), 9 * dp, Offset(cx, cy))
            }
            // Галочка успеха поверх экрана в конце.
            val ok = ((progress - 0.88f) / 0.12f).coerceIn(0f, 1f)
            if (ok > 0f) {
                val c = Offset(100 * dp, 108 * dp)
                drawCircle(Ok, 24 * dp * FastOutSlowInEasing.transform(ok), c)
                val p = Path().apply { moveTo(c.x - 10 * dp, c.y); lineTo(c.x - 3 * dp, c.y + 8 * dp); lineTo(c.x + 11 * dp, c.y - 8 * dp) }
                drawPath(p, Color.White, alpha = ok, style = Stroke(4 * dp, cap = StrokeCap.Round))
            }
        }
        // Знак Nodera на экране терминала, пока идет загрузка.
        val markAlpha = (1f - ((progress - 0.8f) / 0.1f).coerceIn(0f, 1f)) * ((progress - 0.25f) / 0.15f).coerceIn(0f, 1f)
        Image(painterResource(R.drawable.logo_mark), null, Modifier.offset(76.dp, 88.dp).size(48.dp).alpha(markAlpha), contentScale = ContentScale.Fit)
    }
}

/** Заставка при запуске: терминал «принимает» карту, под ним название Nodera Pay и «Платёжный терминал». */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val t = rememberInfiniteTransition(label = "splash")
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "wave")
    val appear by animateFloatAsState(1f, tween(800), label = "appear")
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(2300, easing = LinearEasing))
        delay(450)
        onDone()
    }
    Box(Modifier.fillMaxSize()) {
        LiveBackground()
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            TerminalAnim(progress.value, wave)
            Spacer(Modifier.height(24.dp))
            Row(Modifier.alpha(appear), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.wordmark_light), "Nodera", Modifier.height(20.dp).width(165.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.width(18.dp))
                Image(painterResource(R.drawable.pay_light), "Pay", Modifier.height(18.dp).width(77.dp), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.height(8.dp))
            Text(tr("Платёжный терминал", "Payment terminal"), color = Color.White.copy(alpha = .6f), fontSize = 15.sp, letterSpacing = 1.sp, modifier = Modifier.alpha(appear))
        }
    }
}
