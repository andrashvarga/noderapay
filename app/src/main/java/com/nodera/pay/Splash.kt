package com.nodera.pay

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipRect
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

/** Мягкий фон заставки: светлая/тёмная поверхность и два еле заметных плывущих свечения. */
@Composable
fun SoftBackground(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "softbg")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(8000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val p = pal
    Canvas(modifier.fillMaxSize().background(p.bg)) {
        val r = size.maxDimension * 0.6f
        val k = if (p.dark) 0.20f else 0.14f
        val c1 = Offset(size.width * lerp(0.1f, 0.6f, a), size.height * lerp(0.1f, 0.3f, a))
        val c2 = Offset(size.width * lerp(0.95f, 0.4f, a), size.height * lerp(0.95f, 0.7f, a))
        drawCircle(Brush.radialGradient(listOf(Accent.copy(alpha = k), Color.Transparent), c1, r), r, c1)
        drawCircle(Brush.radialGradient(listOf(GradC.copy(alpha = k * 0.8f), Color.Transparent), c2, r), r, c2)
    }
}

private val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")

/**
 * Платёжный терминал в мягком скевоморфизме: карта входит в слот, на стеклянном экране идёт обработка,
 * клавиши по очереди вдавливаются, в конце зелёная галочка и «Одобрено».
 */
@Composable
fun TerminalAnim(progress: Float, wave: Float, modifier: Modifier = Modifier) {
    val p = pal
    Box(modifier.size(230.dp, 360.dp)) {
        // Корпус: выпуклая мягкая плита.
        Box(
            Modifier.align(Alignment.BottomCenter).size(210.dp, 300.dp)
                .shadow(22.dp, RoundedCornerShape(40.dp), ambientColor = Color.Black.copy(alpha = if (p.dark) .6f else .14f), spotColor = Color.Black.copy(alpha = if (p.dark) .7f else .28f))
                .background(Brush.verticalGradient(listOf(p.surface, p.surface2)), RoundedCornerShape(40.dp))
                .border(1.dp, p.line, RoundedCornerShape(40.dp)),
        )
        // Щель для карты.
        Box(Modifier.align(Alignment.TopCenter).offset(y = 74.dp).size(130.dp, 11.dp).background(Color(0xFF0C1022), RoundedCornerShape(6.dp)).border(1.dp, p.line, RoundedCornerShape(6.dp)))
        // Стеклянный экран.
        val screenOn = ((progress - 0.20f) / 0.12f).coerceIn(0f, 1f)
        Box(
            Modifier.offset(30.dp, 102.dp).size(170.dp, 100.dp).background(Color(0xFF05060C), RoundedCornerShape(20.dp)).padding(5.dp).clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0C1022), Color(0xFF161C3C)))),
            contentAlignment = Alignment.Center,
        ) {
            val bar = ((progress - 0.30f) / 0.50f).coerceIn(0f, 1f)
            val done = ((progress - 0.82f) / 0.14f).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxSize().alpha(screenOn)) {
                val c = Offset(size.width / 2, size.height / 2 - 4.dp.toPx())
                val rad = 27.dp.toPx(); val sw = 4.5.dp.toPx()
                val tl = Offset(c.x - rad, c.y - rad); val sz = Size(rad * 2, rad * 2)
                drawArc(Color.White.copy(alpha = .10f), 0f, 360f, false, tl, sz, style = Stroke(sw))
                if (done < 1f) {
                    drawArc(Accent.copy(alpha = .30f), -90f, 360f * bar, false, Offset(tl.x - 1.5.dp.toPx(), tl.y - 1.5.dp.toPx()), Size(sz.width + 3.dp.toPx(), sz.height + 3.dp.toPx()), style = Stroke(sw + 4.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(Brush.sweepGradient(listOf(GradA, GradB, GradC, GradA), c), -90f, 360f * bar + 1f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                }
                // Бегущая точка по кольцу, пока идёт обработка.
                if (bar < 1f && screenOn > 0.5f) {
                    val ang = Math.toRadians((wave * 360.0) - 90.0)
                    drawCircle(Color.White.copy(alpha = .9f), 2.5.dp.toPx(), Offset(c.x + (rad + 9.dp.toPx()) * Math.cos(ang).toFloat(), c.y + (rad + 9.dp.toPx()) * Math.sin(ang).toFloat()))
                }
                if (done > 0f) {
                    val e = FastOutSlowInEasing.transform(done)
                    drawCircle(Ok.copy(alpha = .28f), (rad + 7.dp.toPx()) * e, c)
                    drawCircle(Ok, rad * e, c)
                    val ch = Path().apply { moveTo(c.x - 11.dp.toPx(), c.y + 1.dp.toPx()); lineTo(c.x - 3.dp.toPx(), c.y + 9.dp.toPx()); lineTo(c.x + 12.dp.toPx(), c.y - 9.dp.toPx()) }
                    drawPath(ch, Color.White, alpha = e, style = Stroke(4.5.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
            val markA = screenOn * (1f - ((progress - 0.80f) / 0.06f).coerceIn(0f, 1f))
            Image(painterResource(R.drawable.logo_mark), null, Modifier.offset(y = (-4).dp).size(30.dp).alpha(markA), contentScale = ContentScale.Fit)
            Text(
                if (done > 0.6f) tr("Одобрено", "Approved") else tr("Обработка…", "Processing…"),
                color = (if (done > 0.6f) Ok else Color.White.copy(alpha = .55f)).copy(alpha = screenOn), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
            )
            // Блик на стекле.
            Canvas(Modifier.fillMaxSize()) {
                val g = Path().apply { moveTo(0f, 0f); lineTo(size.width * 0.55f, 0f); lineTo(size.width * 0.25f, size.height); lineTo(0f, size.height); close() }
                drawPath(g, Brush.linearGradient(listOf(Color.White.copy(alpha = .09f), Color.Transparent), Offset.Zero, Offset(size.width * .5f, size.height * .6f)))
            }
        }
        // Клавиатура: клавиши выпуклые, нажатая вдавливается.
        val lit = if (progress in 0.30f..0.84f) (progress * 16f).toInt() % 9 else -1
        for (i in 0 until 9) {
            Box(
                Modifier.offset((30 + (i % 3) * 61).dp, (222 + (i / 3) * 42).dp).size(48.dp, 34.dp)
                    .shadow(if (i == lit) 0.dp else 3.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black.copy(alpha = .2f), spotColor = Color.Black.copy(alpha = if (p.dark) .6f else .3f))
                    .background(if (i == lit) Accent.copy(alpha = .22f).compositeOver(p.surface) else p.surface, RoundedCornerShape(14.dp))
                    .border(1.dp, if (i == lit) Accent else p.line, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(digits[i], color = if (i == lit) Accent else p.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }
        // Карта входит в щель сверху; видна только часть над щелью.
        Canvas(Modifier.fillMaxSize()) {
            val e = FastOutSlowInEasing.transform((progress / 0.28f).coerceIn(0f, 1f))
            val ty = lerp(2f, 82f, e).dp.toPx()
            val cw = 124.dp.toPx(); val ch = 78.dp.toPx(); val cx = (size.width - cw) / 2
            clipRect(0f, 0f, size.width, 80.dp.toPx()) {
                drawRoundRect(Color.Black.copy(alpha = .22f), Offset(cx + 3.dp.toPx(), ty + 5.dp.toPx()), Size(cw, ch), CornerRadius(12.dp.toPx()))
                drawRoundRect(Brush.linearGradient(listOf(GradA, GradB, GradC), Offset(cx, ty), Offset(cx + cw, ty + ch)), Offset(cx, ty), Size(cw, ch), CornerRadius(12.dp.toPx()))
                drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = .35f), Color.Transparent), Offset(cx, ty), Offset(cx + cw * .6f, ty + ch * .6f)), Offset(cx, ty), Size(cw, ch), CornerRadius(12.dp.toPx()))
                drawRoundRect(Color(0xFFFFD66B), Offset(cx + 14.dp.toPx(), ty + 18.dp.toPx()), Size(24.dp.toPx(), 18.dp.toPx()), CornerRadius(4.dp.toPx()))
                drawRoundRect(Color.White.copy(alpha = .55f), Offset(cx + 14.dp.toPx(), ty + ch - 16.dp.toPx()), Size(54.dp.toPx(), 4.dp.toPx()), CornerRadius(2.dp.toPx()))
            }
        }
    }
}

/** Заставка при запуске: терминал «принимает» карту, под ним название Nodera Pay и «Платёжный терминал». */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val t = rememberInfiniteTransition(label = "splash")
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "wave")
    val appear by animateFloatAsState(1f, tween(900), label = "appear")
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(3000, easing = LinearEasing))
        delay(500)
        onDone()
    }
    val light = pal.dark
    Box(Modifier.fillMaxSize()) {
        SoftBackground()
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            TerminalAnim(progress.value, wave)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.alpha(appear), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(if (light) R.drawable.wordmark_light else R.drawable.wordmark_dark), "Nodera", Modifier.height(20.dp).width(165.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.width(18.dp))
                Image(painterResource(if (light) R.drawable.pay_light else R.drawable.pay_dark), "Pay", Modifier.height(18.dp).width(77.dp), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.height(8.dp))
            Text(tr("Платёжный терминал", "Payment terminal"), color = pal.muted, fontSize = 15.sp, letterSpacing = 1.sp, modifier = Modifier.alpha(appear))
        }
    }
}
