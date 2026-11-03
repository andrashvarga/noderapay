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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Заставка при запуске: логотип «вырастает», вокруг бежит градиентное кольцо, под ним название. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val appear = remember { Animatable(0f) }
    val t = rememberInfiniteTransition(label = "splash")
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "sweep")
    val glow by t.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        delay(900)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B0D18), Color(0xFF151A36)))), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().scale(glow).alpha(appear.value)) {
                    val w = 6.dp.toPx()
                    drawArc(Brush.sweepGradient(listOf(Color.Transparent, GradA, GradB, GradC, Color.Transparent)), sweep, 300f, false, Offset(w, w), Size(size.width - 2 * w, size.height - 2 * w), style = Stroke(w, cap = StrokeCap.Round))
                }
                Image(painterResource(R.drawable.logo_mark_dark), null, Modifier.size(104.dp).scale(0.6f + 0.4f * appear.value).alpha(appear.value))
            }
            Spacer(Modifier.height(20.dp))
            Text("Nodera Pay", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, modifier = Modifier.alpha(appear.value))
            Text("payment terminal", color = Color.White.copy(alpha = .55f), fontSize = 14.sp, modifier = Modifier.alpha(appear.value))
        }
    }
}
