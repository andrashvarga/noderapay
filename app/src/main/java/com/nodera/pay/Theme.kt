package com.nodera.pay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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

val LightPal = Pal(Color(0xFFF3F4FA), Color.White, Color(0xFFF0F1F8), Color(0xFF1B1D2E), Color(0xFF6C7090), Color(0xFFE3E5F0), false)
val DarkPal = Pal(Color(0xFF12141D), Color(0xFF1B1E2B), Color(0xFF242838), Color(0xFFECEEF8), Color(0xFF8D92B0), Color(0xFF2C3045), true)
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

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val base = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pal.surface).border(1.dp, pal.line, RoundedCornerShape(18.dp))
    Column(if (onClick != null) base.clickable(onClick = onClick).padding(16.dp) else base.padding(16.dp), content = content)
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.fillMaxWidth().height(52.dp).clip(shape)
            .background(if (enabled) BrandBrush else Brush.linearGradient(listOf(pal.line, pal.line)))
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
        else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(text, color = if (enabled) Color.White else pal.muted, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun SoftButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = Accent, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .12f)).border(BorderStroke(1.dp, color.copy(alpha = .25f)), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) Accent else pal.surface2).border(1.dp, if (selected) Accent else pal.line, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, color = if (selected) Color.White else pal.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
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
        shape = RoundedCornerShape(14.dp),
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
