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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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

class Pal(val bg: Color, val surface: Color, val surface2: Color, val text: Color, val muted: Color, val line: Color, val dark: Boolean)

// Material 3: тональные поверхности и аккуратные тени; фон светлее/темнее карточек, радиусы крупные.
val LightPal = Pal(Color(0xFFF5F6FB), Color(0xFFFFFFFF), Color(0xFFECEEF7), Color(0xFF1A1C2A), Color(0xFF656A87), Color(0xFFE0E3F0), false)
val DarkPal = Pal(Color(0xFF0F1117), Color(0xFF191C25), Color(0xFF232734), Color(0xFFECEEF8), Color(0xFF8D92B0), Color(0xFF2A2E40), true)
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


/** Поверхность Material 3: тональная заливка, тонкая обводка и мягкая реалистичная тень (две: рассеянная и направленная). */
fun Modifier.raised(radius: Dp = 24.dp, elevation: Dp = 2.dp, fill: Color? = null, outline: Boolean = true): Modifier = composed {
    val p = pal
    val shape = RoundedCornerShape(radius)
    this.shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = if (p.dark) .5f else .10f), spotColor = Color.Black.copy(alpha = if (p.dark) .6f else .22f))
        .background(fill ?: p.surface, shape)
        .then(if (outline) Modifier.border(1.dp, p.line, shape) else Modifier)
}

/** Плитка выбора: выбранная подсвечена тоном акцента и рамкой. */
fun Modifier.choice(on: Boolean, radius: Dp = 20.dp, onClick: () -> Unit): Modifier = composed {
    val shape = RoundedCornerShape(radius)
    val tint = Accent.copy(alpha = .12f).compositeOver(pal.surface)
    this.raised(radius, elevation = if (on) 0.dp else 1.dp, fill = if (on) tint else null, outline = !on)
        .then(if (on) Modifier.border(2.dp, Accent, shape) else Modifier).clip(shape).clickable(onClick = onClick)
}

@Composable
fun Spinner(size: Dp = 32.dp, color: Color = Accent) {
    CircularProgressIndicator(Modifier.size(size), color = color, strokeWidth = (size.value / 9f).coerceAtLeast(2.5f).dp, trackColor = color.copy(alpha = .18f))
}

/** Карточка; tint окрашивает заливку и обводку в цвет состояния (успех, отмена, ожидание). */
@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, tint: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val fill = tint?.let { it.copy(alpha = if (pal.dark) .16f else .10f).compositeOver(pal.surface) }
    var base = modifier.fillMaxWidth().raised(24.dp, 1.dp, fill = fill, outline = tint == null)
    if (tint != null) base = base.border(1.5.dp, tint.copy(alpha = .6f), shape)
    base = base.clip(shape)
    Column(if (onClick != null) base.clickable(onClick = onClick).padding(18.dp) else base.padding(18.dp), content = content)
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.fillMaxWidth().height(56.dp)
            .then(if (enabled) Modifier.shadow(4.dp, shape, clip = false, ambientColor = Accent.copy(alpha = .3f), spotColor = Accent.copy(alpha = .5f)) else Modifier)
            .clip(shape)
            .background(if (enabled) Brush.verticalGradient(listOf(Color(0xFF5A85FF), Accent)) else Brush.verticalGradient(listOf(pal.surface2, pal.surface2)))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) Spinner(26.dp, Color.White)
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, tint = if (enabled) Color.White else pal.muted, modifier = Modifier.size(20.dp))
            Text(text, color = if (enabled) Color.White else pal.muted, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/** Тональная кнопка: контейнер цвета акцента с низкой насыщенностью. */
@Composable
fun SoftButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = Accent, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = .13f)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(if (selected) Accent.copy(alpha = .16f) else Color.Transparent).border(1.dp, if (selected) Color.Transparent else pal.line, shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = Accent, modifier = Modifier.size(16.dp))
        Text(text, color = if (selected) Accent else pal.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color = Accent, size: Int = 40) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((size * 0.52f).dp))
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true, keyboard: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default, trailing: @Composable (() -> Unit)? = null) {
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine, keyboardOptions = keyboard, trailingIcon = trailing,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = pal.line, focusedLabelColor = Accent, unfocusedContainerColor = pal.surface, focusedContainerColor = pal.surface),
    )
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
