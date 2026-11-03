package com.nodera.pay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Палитра в духе кабинета Nodera: акцент #3a6bff, градиент логотипа фиолетовый → синий → голубой.
val Accent = Color(0xFF3A6BFF)
val GradA = Color(0xFF7A7CFF)
val GradB = Color(0xFF2F6BFF)
val GradC = Color(0xFF4FD8FF)
val Ok = Color(0xFF1FA45F)
val Warn = Color(0xFFE8890C)
val Bad = Color(0xFFD64550)
val BrandBrush = Brush.linearGradient(listOf(GradA, GradB, GradC))

class Pal(val bg: Color, val surface: Color, val surface2: Color, val text: Color, val muted: Color, val line: Color, val dark: Boolean, val hi: Color, val lo: Color)

// Мягкий скевоморфизм: одна базовая поверхность, объём даёт пара теней — светлая сверху-слева и тёмная снизу-справа.
val LightPal = Pal(Color(0xFFE6EAF3), Color(0xFFE6EAF3), Color(0xFFDCE1EE), Color(0xFF1F2236), Color(0xFF6A6F8C), Color(0xFFD2D8E7), false, Color(0xFFFFFFFF), Color(0xFF9AA5C4))
val DarkPal = Pal(Color(0xFF1C1F2B), Color(0xFF1C1F2B), Color(0xFF242836), Color(0xFFECEEF8), Color(0xFF8D92B0), Color(0xFF2C3045), true, Color(0xFF2E3446), Color(0xFF07080D))
val LocalPal = compositionLocalOf { LightPal }
val pal: Pal @Composable get() = LocalPal.current

@Composable
fun NoderaTheme(content: @Composable () -> Unit) {
    val dark = when (Store.theme) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val p = if (dark) DarkPal else LightPal
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = Accent, onPrimary = Color.White, background = p.bg, surface = p.surface, onSurface = p.text, onBackground = p.text,
        surfaceVariant = p.surface2, onSurfaceVariant = p.muted, outline = p.line, secondaryContainer = Accent.copy(alpha = .14f), onSecondaryContainer = Accent,
    )
    CompositionLocalProvider(LocalPal provides p) { MaterialTheme(colorScheme = scheme, content = content) }
}


/** Выпуклая (или вдавленная, inset) мягкая поверхность. Тени рисуются слоями, поэтому работает на любой версии Android. */
fun Modifier.neu(radius: Dp = 18.dp, inset: Boolean = false, depth: Dp = 5.dp, fill: Color? = null, hi: Color? = null, lo: Color? = null): Modifier = composed {
    val p = pal
    val base = fill ?: p.bg
    val light = hi ?: p.hi
    val dark = lo ?: p.lo
    drawBehind {
        val r = radius.toPx(); val d = depth.toPx()
        val corner = CornerRadius(r, r)
        if (!inset) {
            val layers = 7
            for (i in 1..layers) {
                val grow = i * d / 5f
                val a = (if (p.dark) 0.075f else 0.06f) * (1.15f - i / (layers + 1f))
                drawRoundRect(dark.copy(alpha = a), Offset(d * 0.55f - grow, d * 0.55f - grow), Size(size.width + grow * 2, size.height + grow * 2), CornerRadius(r + grow, r + grow))
                drawRoundRect(light.copy(alpha = a * (if (p.dark) 0.9f else 1.9f)), Offset(-d * 0.55f - grow, -d * 0.55f - grow), Size(size.width + grow * 2, size.height + grow * 2), CornerRadius(r + grow, r + grow))
            }
            drawRoundRect(base, Offset.Zero, size, corner)
            drawRoundRect(
                Brush.linearGradient(listOf(light.copy(alpha = if (p.dark) .5f else .75f), Color.Transparent, dark.copy(alpha = if (p.dark) .35f else .18f)), Offset.Zero, Offset(size.width, size.height)),
                Offset.Zero, size, corner,
            )
        } else {
            drawRoundRect(base, Offset.Zero, size, corner)
            drawRoundRect(
                Brush.linearGradient(listOf(dark.copy(alpha = if (p.dark) .75f else .38f), Color.Transparent, light.copy(alpha = if (p.dark) .45f else .85f)), Offset.Zero, Offset(size.width, size.height)),
                Offset.Zero, size, corner,
            )
            val sw = 1.6.dp.toPx()
            drawRoundRect(
                Brush.linearGradient(listOf(dark.copy(alpha = if (p.dark) .9f else .5f), light.copy(alpha = if (p.dark) .6f else .95f)), Offset.Zero, Offset(size.width, size.height)),
                Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), CornerRadius(r - sw / 2, r - sw / 2), style = Stroke(sw),
            )
        }
    }
}

/** Нажимаемая мягкая поверхность: при нажатии «вдавливается». */
fun Modifier.neuPress(radius: Dp = 18.dp, depth: Dp = 5.dp, enabled: Boolean = true, fill: Color? = null, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    this.neu(radius, inset = pressed, depth = depth, fill = fill).clip(RoundedCornerShape(radius)).clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** Переключатель-плитка: выбранная вдавлена и обведена акцентом. */
fun Modifier.neuToggle(on: Boolean, radius: Dp = 18.dp, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val shape = RoundedCornerShape(radius)
    this.neu(radius, inset = on || pressed).then(if (on) Modifier.border(1.5.dp, Accent, shape) else Modifier).clip(shape)
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

/** Крутилка в духе мягкого скевоморфизма: утопленный жёлоб и светящаяся дуга с «головой». */
@Composable
fun NeuSpinner(size: Dp = 40.dp, color: Color = Accent) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "neuspin")
    val ang by t.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.LinearEasing)), label = "a")
    val p = pal
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        val sw = this.size.minDimension * 0.17f
        val inset = sw / 2f + 1f
        val tl = Offset(inset, inset); val sz = Size(this.size.width - inset * 2, this.size.height - inset * 2)
        drawArc(p.lo.copy(alpha = if (p.dark) .8f else .35f), 0f, 360f, false, tl, sz, style = Stroke(sw))
        drawArc(p.hi.copy(alpha = if (p.dark) .25f else .9f), 215f, 160f, false, Offset(tl.x - sw * .15f, tl.y - sw * .15f), sz, style = Stroke(sw * .35f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawArc(Brush.sweepGradient(listOf(color.copy(alpha = 0f), color, GradC)), ang - 90f, 250f, false, tl, sz, style = Stroke(sw * .72f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val base = modifier.fillMaxWidth()
    Column(if (onClick != null) base.neuPress(22.dp, onClick = onClick).padding(16.dp) else base.neu(22.dp).padding(16.dp), content = content)
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val on = enabled
    Box(
        modifier.fillMaxWidth().height(54.dp).scale(if (pressed && on) 0.985f else 1f)
            .neu(18.dp, inset = !on, depth = if (pressed) 3.dp else 6.dp, fill = if (on) GradB else null, lo = if (on) Color(0xFF1B3FB0) else null)
            .clip(shape)
            .then(if (on) Modifier.background(Brush.linearGradient(if (pressed) listOf(GradB, GradA) else listOf(GradA, GradB, GradC.copy(alpha = .9f)))) else Modifier)
            .clickable(interactionSource = src, indication = null, enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f), Color.Transparent, Color.Black.copy(alpha = .12f)))))
        if (loading) NeuSpinner(26.dp, Color.White)
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, tint = if (on) Color.White else pal.muted, modifier = Modifier.size(20.dp))
            Text(text, color = if (on) Color.White else pal.muted, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun SoftButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = Accent, onClick: () -> Unit) {
    Row(
        modifier.neuPress(16.dp, depth = 4.dp, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.neuToggle(selected, 20.dp, onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) Accent else pal.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color = Accent, size: Int = 40) {
    Box(Modifier.size(size.dp).neu((size / 2).dp, depth = 3.dp), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true, keyboard: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default, trailing: @Composable (() -> Unit)? = null) {
    Box(modifier.fillMaxWidth().neu(18.dp, inset = true, depth = 4.dp)) {
        OutlinedTextField(
            value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, keyboardOptions = keyboard, trailingIcon = trailing,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent.copy(alpha = .7f), unfocusedBorderColor = Color.Transparent, focusedLabelColor = Accent,
                unfocusedContainerColor = Color.Transparent, focusedContainerColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun Title(text: String) = Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = pal.text)

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) = Text(text, color = pal.muted, fontSize = 13.sp, modifier = modifier)

@Composable
fun StatusPill(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .14f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ErrorNote(text: String?) {
    if (text != null) Text(text, color = Bad, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}
