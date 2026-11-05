package com.nodera.pay

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), color = pal.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
    Card(content = content)
}

@Composable
private fun Choice(options: List<Pair<String, String>>, value: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { (k, v) -> Chip(v, value == k) { onPick(k) } } }
}

@Composable
fun SettingsScreen(onDisconnected: () -> Unit) {
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val src = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
            // Горизонтальный логотип до 720 × 240 px, прозрачность сохраняется.
            val k = minOf(1f, 720f / src.width, 240f / src.height)
            val bmp = Bitmap.createScaledBitmap(src, (src.width * k).toInt().coerceAtLeast(1), (src.height * k).toInt().coerceAtLeast(1), true)
            val out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            Store.saveCustomLogo(out.toByteArray())
        }.onFailure { toast(ctx, tr("Не удалось загрузить логотип", "Could not load the logo")) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Title(tr("Настройки", "Settings"))

        Section(tr("API-ключи", "API keys")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Lock, null, tint = Ok, modifier = Modifier.size(18.dp))
                Text(if (Store.isTest) tr("Подключено · тестовый режим", "Connected · test mode") else tr("Подключено · боевой режим", "Connected · live mode"), fontWeight = FontWeight.Bold, color = pal.text)
            }
            Spacer(Modifier.height(10.dp))
            Muted("api_key"); Text(maskKey(Store.apiKey), fontFamily = FontFamily.Monospace, color = pal.text)
            Spacer(Modifier.height(6.dp))
            Muted("api_secret_key"); Text(maskKey(Store.apiSecret), fontFamily = FontFamily.Monospace, color = pal.text)
            Spacer(Modifier.height(8.dp))
            Muted(tr("Ключи хранятся зашифрованно (Android Keystore) и не показываются полностью. Их можно только удалить и ввести новые.", "Keys are stored encrypted (Android Keystore) and never shown in full. You can only delete them and enter new ones."))
            Spacer(Modifier.height(10.dp))
            SoftButton(tr("Удалить ключи", "Delete keys"), Modifier.fillMaxWidth(), Icons.Rounded.Delete, Bad) { confirmDelete = true }
        }

        Section(tr("Оформление", "Appearance")) {
            Muted(tr("Тема", "Theme"))
            Spacer(Modifier.height(8.dp))
            PreviewRow(
                listOf(
                    PreviewItem("system", tr("Системная", "System")) { MiniScreen(LightPal, DarkPal) },
                    PreviewItem("light", tr("Светлая", "Light")) { MiniScreen(LightPal, null) },
                    PreviewItem("dark", tr("Тёмная", "Dark")) { MiniScreen(DarkPal, null) },
                ), Store.theme,
            ) { Store.applyTheme(it) }
            Spacer(Modifier.height(16.dp))
            Muted(tr("Язык", "Language"))
            Spacer(Modifier.height(8.dp))
            Choice(listOf("ru" to "Русский", "en" to "English"), Store.lang) { Store.applyLang(it) }
            Spacer(Modifier.height(16.dp))
            Muted(tr("Иконка приложения", "App icon"))
            Spacer(Modifier.height(8.dp))
            PreviewRow(
                listOf(
                    PreviewItem("dark", tr("Тёмная", "Dark")) { IconPreview(Color(0xFF0B0D18)) },
                    PreviewItem("light", tr("Светлая", "Light")) { IconPreview(Color(0xFFF3F4FA)) },
                ), Store.icon,
            ) { Store.applyIcon(it); toast(ctx, tr("Иконка сменится на экране приложений через несколько секунд", "The icon updates on the launcher in a few seconds")) }
            Spacer(Modifier.height(16.dp))
            Muted(tr("Логотип слева вверху", "Top-left logo"))
            Spacer(Modifier.height(8.dp))
            PreviewRow(
                listOf(
                    PreviewItem("nodera", "Nodera Pay") { LogoPreview("nodera") },
                    PreviewItem("none", tr("Без логотипа", "None")) { LogoPreview("none") },
                    PreviewItem("custom", tr("Свой", "Custom")) { LogoPreview("custom") },
                ), Store.logoMode,
            ) {
                if (it == "custom" && !Store.logoFile.exists()) logoPicker.launch("image/*") else Store.applyLogoMode(it)
            }
            Spacer(Modifier.height(8.dp))
            Muted(tr("Для своего логотипа лучше всего подходит горизонтальный PNG с прозрачным фоном, 600 × 160 px (соотношение примерно 4 : 1), до 1 МБ. Слишком большой файл приложение само уменьшит до 720 × 240 px. Выбирайте нейтральный цвет или логотип с собственным фоном, чтобы он читался и на светлой, и на тёмной теме.",
                "For your own logo a horizontal PNG with a transparent background works best: 600 × 160 px (about 4 : 1), under 1 MB. A larger file is shrunk to 720 × 240 px. Use a neutral colour or a logo with its own background so it reads on both light and dark themes."))
            if (Store.logoMode == "custom") { Spacer(Modifier.height(8.dp)); SoftButton(tr("Загрузить другой логотип", "Upload another logo"), icon = Icons.Rounded.Upload) { logoPicker.launch("image/*") } }
        }

        UpdatesSection()

        Section(tr("Уведомления", "Notifications")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Статусы заявок, выводов и диспутов", "Order, withdrawal and dispute updates"), fontWeight = FontWeight.Bold, color = pal.text)
                    Muted(tr("Пуш с номером: оплачена, отменена, вывод выполнен, диспут принят или отклонён (с причиной).", "Push with the number: paid, canceled, withdrawal done, dispute approved or rejected (with the reason)."))
                }
                Switch(Store.notify, { Store.applyNotify(it); syncWatcher(ctx); if (it && !canNotify(ctx)) toast(ctx, tr("Разрешите уведомления в настройках телефона", "Allow notifications in the phone settings")) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Accent))
            }
            Spacer(Modifier.height(8.dp))
            Muted(tr("Без Firebase приложение само опрашивает сервер раз в 30 секунд, поэтому в шторке висит служебная заметка. На некоторых телефонах (Xiaomi, Huawei, Samsung) систему нужно попросить не усыплять приложение.", "Without Firebase the app polls the server every 30 seconds, so a service note stays in the shade. On some phones (Xiaomi, Huawei, Samsung) you need to exclude the app from battery optimization."))
            Spacer(Modifier.height(10.dp))
            SoftButton(tr("Настройки батареи", "Battery settings"), Modifier.fillMaxWidth(), Icons.Rounded.BatteryChargingFull) {
                runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            }
        }

        Section(tr("Поддержка", "Support")) {
            Muted(tr("Вопросы по чекам и платежам — в Telegram.", "Questions about receipts and payments — in Telegram."))
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(BrandBrush).clickable { openUrl(ctx, "https://t.me/tm_chain") },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Icon(TelegramIcon, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Поддержка в Telegram", "Telegram support"), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Muted("Nodera Pay 1.6.0")
        Spacer(Modifier.height(8.dp))
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(tr("Удалить ключи?", "Delete keys?")) },
        text = { Text(tr("Приложение перестанет работать, пока вы не введёте новые ключи.", "The app will stop working until you enter new keys.")) },
        confirmButton = { TextButton({ confirmDelete = false; Push.forget(); Store.deleteKeys(); syncWatcher(ctx); onDisconnected() }) { Text(tr("Удалить", "Delete"), color = Bad) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(tr("Отмена", "Cancel")) } },
    )
}

private class PreviewItem(val key: String, val label: String, val preview: @Composable () -> Unit)

/** Ряд плиток-превью: выбранная обведена акцентом, под ней подпись. */
@Composable
private fun PreviewRow(items: List<PreviewItem>, value: String, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { it0 ->
            val on = value == it0.key
            val shape = RoundedCornerShape(18.dp)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().height(116.dp).clip(shape).border(if (on) 2.5.dp else 1.dp, if (on) Accent else pal.line, shape)
                        .clickable { onPick(it0.key) },
                    contentAlignment = Alignment.Center,
                ) {
                    it0.preview()
                    if (on) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(it0.label, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Accent else pal.text, maxLines = 1)
            }
        }
    }
}

/** Мини-экран приложения в палитре; с двумя палитрами показывает половинки (системная тема). */
@Composable
private fun MiniScreen(left: Pal, right: Pal?) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        fun draw(p: Pal, clipLeft: Float, clipRight: Float) {
            clipRect(clipLeft * size.width, 0f, clipRight * size.width, size.height) {
                drawRect(p.bg)
                val u = size.width / 100f
                drawCircle(Accent, 5 * u, androidx.compose.ui.geometry.Offset(14 * u, 12 * u))
                drawRoundRect(p.muted.copy(alpha = .45f), androidx.compose.ui.geometry.Offset(24 * u, 9 * u), androidx.compose.ui.geometry.Size(30 * u, 6 * u), androidx.compose.ui.geometry.CornerRadius(3 * u))
                for (i in 0..1) {
                    val y = (26 + i * 26) * u
                    drawRoundRect(p.surface, androidx.compose.ui.geometry.Offset(8 * u, y), androidx.compose.ui.geometry.Size(84 * u, 22 * u), androidx.compose.ui.geometry.CornerRadius(6 * u))
                    drawRoundRect(p.line, androidx.compose.ui.geometry.Offset(8 * u, y), androidx.compose.ui.geometry.Size(84 * u, 22 * u), androidx.compose.ui.geometry.CornerRadius(6 * u), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
                    drawRoundRect(p.muted.copy(alpha = .5f), androidx.compose.ui.geometry.Offset(13 * u, y + 6 * u), androidx.compose.ui.geometry.Size(34 * u, 4 * u), androidx.compose.ui.geometry.CornerRadius(2 * u))
                    drawRoundRect(if (i == 0) Ok else Warn, androidx.compose.ui.geometry.Offset(66 * u, y + 7 * u), androidx.compose.ui.geometry.Size(20 * u, 7 * u), androidx.compose.ui.geometry.CornerRadius(4 * u))
                }
                drawRect(p.surface, androidx.compose.ui.geometry.Offset(0f, size.height - 18 * u), androidx.compose.ui.geometry.Size(size.width, 18 * u))
                for (i in 0..3) drawCircle(if (i == 2) Accent else p.muted.copy(alpha = .5f), 3 * u, androidx.compose.ui.geometry.Offset((14 + i * 24) * u, size.height - 9 * u))
            }
        }
        if (right == null) draw(left, 0f, 1f) else { draw(left, 0f, .5f); draw(right, .5f, 1f) }
    }
}

/** Иконка приложения на выбранном фоне, как на экране приложений. */
@Composable
private fun IconPreview(bg: Color) {
    Box(Modifier.fillMaxSize().background(pal.surface2), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(64.dp).shadow(6.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black.copy(alpha = .3f), spotColor = Color.Black.copy(alpha = .35f)).clip(RoundedCornerShape(18.dp)).background(bg),
            contentAlignment = Alignment.Center,
        ) { androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.ic_fg_mark), null, Modifier.size(54.dp)) }
    }
}

/** Шапка приложения с выбранным вариантом логотипа. */
@Composable
private fun LogoPreview(kind: String) {
    Box(Modifier.fillMaxSize().background(pal.bg), contentAlignment = Alignment.TopStart) {
        Row(Modifier.fillMaxWidth().padding(10.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            when (kind) {
                "nodera" -> BrandTitle(7)
                "custom" -> {
                    val bmp = remember(Store.logoVersion) { runCatching { BitmapFactory.decodeFile(Store.logoFile.path)?.asImageBitmap() }.getOrNull() }
                    if (bmp != null) androidx.compose.foundation.Image(bmp, null, Modifier.height(22.dp).widthIn(max = 80.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit, alignment = Alignment.CenterStart)
                    else Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Accent, modifier = Modifier.size(24.dp))
                }
                else -> {}
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(10.dp).fillMaxWidth().height(34.dp).clip(RoundedCornerShape(10.dp)).background(pal.surface))
    }
}
