package com.noros.pay

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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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

/** Текст и (по желанию) кнопка пустого раздела: по центру области над пустыней. */
@Composable
fun EmptyNote(title: String, hint: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = SCENE_H), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, fontWeight = FontWeight.Bold, color = pal.text, fontSize = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(hint, color = pal.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(18.dp)); action() }
    }
}

private val SCENE_H = 190.dp

/**
 * Пустыня на всю ширину и вплотную к нижней панели, по ней катится логотип-перекати-поле с шлейфом из пыли.
 * Высота одна и та же на всех вкладках; всё, что не влезает, обрезается по границам страницы.
 */
@Composable
fun TumbleweedScene(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "weed")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "x")
    val dark = pal.dark
    val skyHaze = if (dark) Color(0xFF2A2A3A) else Color(0xFFF1E6CF)
    val far = if (dark) Color(0xFF332E27) else Color(0xFFE6D3AE)
    val farLit = if (dark) Color(0xFF453E32) else Color(0xFFF3E6C8)
    val mid = if (dark) Color(0xFF3B3427) else Color(0xFFDEC693)
    val midLit = if (dark) Color(0xFF52472F) else Color(0xFFEFD9A6)
    val near = if (dark) Color(0xFF2C261C) else Color(0xFFCDAE72)
    val nearLit = if (dark) Color(0xFF433923) else Color(0xFFE3C98F)
    val speck = if (dark) Color(0xFF6B5C3E) else Color(0xFFA98A50)
    val sceneH = SCENE_H
    BoxWithConstraints(modifier.fillMaxWidth().height(sceneH).clipToBounds()) {
        val w = maxWidth
        val wd = 56.dp
        val gyFrac = 0.66f
        val gy = sceneH * gyFrac
        fun xAt(q: Float) = lerp(-wd.value, w.value + wd.value, q).dp
        fun bounceAt(q: Float) = (kotlin.math.abs(kotlin.math.sin(q * Math.PI * 7)) * 24).toFloat()
        val x = xAt(p)
        val bounce = bounceAt(p)
        Canvas(Modifier.fillMaxSize()) {
            val cw = size.width; val ch = size.height
            val g = ch * gyFrac
            fun ridge(base: Float, a1: Float, l1: Float, a2: Float, l2: Float, ph: Float): Path {
                val path = Path(); path.moveTo(0f, ch)
                var xx = 0f
                while (xx <= cw + 4f) {
                    val y = base + a1.dp.toPx() * kotlin.math.sin(xx / l1.dp.toPx() + ph) + a2.dp.toPx() * kotlin.math.sin(xx / l2.dp.toPx() + ph * 2.3f)
                    path.lineTo(xx, y); xx += 4.dp.toPx()
                }
                path.lineTo(cw, ch); path.close(); return path
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, skyHaze.copy(alpha = .75f)), startY = ch * 0.15f, endY = g - 14.dp.toPx()), size = Size(cw, g))
            drawPath(ridge(g - 26.dp.toPx(), 9f, 110f, 4f, 47f, 0.6f), Brush.verticalGradient(listOf(farLit, far), startY = g - 40.dp.toPx(), endY = ch))
            drawPath(ridge(g + 2.dp.toPx(), 4f, 90f, 2f, 33f, 2.1f), Brush.verticalGradient(listOf(midLit, mid), startY = g - 4.dp.toPx(), endY = ch))
            val crest = Path(); var cx = 0f
            while (cx <= cw) {
                val y = g + 2.dp.toPx() + 4.dp.toPx() * kotlin.math.sin(cx / 90.dp.toPx() + 2.1f) + 2.dp.toPx() * kotlin.math.sin(cx / 33.dp.toPx() + 4.83f)
                if (cx == 0f) crest.moveTo(cx, y) else crest.lineTo(cx, y); cx += 4.dp.toPx()
            }
            drawPath(crest, midLit.copy(alpha = .9f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
            for (k in 1..12) {
                val q = p - k * 0.0105f
                if (q < 0f) continue
                val fade = 1f - k / 13f
                val px = xAt(q).toPx()
                val py = g - bounceAt(q).dp.toPx()
                drawCircle(near.copy(alpha = .20f * fade), (wd.toPx() * .20f * fade) + 2.dp.toPx(), Offset(px, py - wd.toPx() * .45f))
                drawOval(near.copy(alpha = .30f * fade), Offset(px - 9.dp.toPx() * (1.4f - fade), g + 2.dp.toPx() - 2.dp.toPx() * fade), Size(18.dp.toPx() * (1.4f - fade), 5.dp.toPx() * (1.4f - fade)))
            }
            drawPath(ridge(g + 24.dp.toPx(), 6f, 70f, 3f, 28f, 4.0f), Brush.verticalGradient(listOf(nearLit, near), startY = g + 14.dp.toPx(), endY = ch))
            var seed = 7L
            fun rnd(): Float { seed = (seed * 1103515245L + 12345L) and 0x7fffffffL; return (seed % 10000L) / 10000f }
            for (i in 0 until 46) {
                val sx = rnd() * cw; val sy = g + 18.dp.toPx() + rnd() * (ch - g - 20.dp.toPx())
                drawCircle(speck.copy(alpha = .25f + rnd() * .3f), (.6f + rnd() * 1.1f).dp.toPx(), Offset(sx, sy))
            }
            for (i in 0 until 4) {
                val sx = cw * (0.12f + i * 0.27f + rnd() * 0.08f); val sy = g + 34.dp.toPx() + rnd() * 18.dp.toPx()
                drawOval(speck.copy(alpha = .55f), Offset(sx, sy), Size((6f + rnd() * 6f).dp.toPx(), 3.5f.dp.toPx()))
            }
        }
        val shadowW = (wd.value * (0.9f - bounce / 90f)).dp
        Box(Modifier.offset(x - shadowW / 2, gy + 1.dp).size(shadowW, 7.dp).clip(CircleShape).background(Color.Black.copy(alpha = (0.16f - bounce / 300f).coerceAtLeast(.05f))))
        Image(
            painterResource(R.drawable.logo_mark), null,
            Modifier.offset(x - wd / 2, gy - wd - bounce.dp + 6.dp).size(wd).rotate(p * 720f),
            contentScale = ContentScale.Fit,
        )
    }
}
