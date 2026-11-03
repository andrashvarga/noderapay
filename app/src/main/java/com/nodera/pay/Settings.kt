package com.nodera.pay

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
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
            val side = 256
            val k = minOf(1f, side / maxOf(src.width, src.height).toFloat())
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
            Spacer(Modifier.height(6.dp))
            Muted(tr("Сервер", "Server")); Text(Store.baseUrl, color = pal.text, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Muted(tr("Ключи хранятся зашифрованно (Android Keystore) и не показываются полностью. Их можно только удалить и ввести новые.", "Keys are stored encrypted (Android Keystore) and never shown in full. You can only delete them and enter new ones."))
            Spacer(Modifier.height(10.dp))
            SoftButton(tr("Удалить ключи", "Delete keys"), Modifier.fillMaxWidth(), Icons.Rounded.Delete, Bad) { confirmDelete = true }
        }

        Section(tr("Оформление", "Appearance")) {
            Muted(tr("Тема", "Theme"))
            Choice(listOf("system" to tr("Системная", "System"), "light" to tr("Светлая", "Light"), "dark" to tr("Тёмная", "Dark")), Store.theme) { Store.applyTheme(it) }
            Spacer(Modifier.height(12.dp))
            Muted(tr("Язык", "Language"))
            Choice(listOf("ru" to "Русский", "en" to "English"), Store.lang) { Store.applyLang(it) }
            Spacer(Modifier.height(12.dp))
            Muted(tr("Иконка приложения", "App icon"))
            Choice(listOf("dark" to tr("Тёмная", "Dark"), "light" to tr("Светлая", "Light")), Store.icon) { Store.applyIcon(it); toast(ctx, tr("Иконка сменится на экране приложений через несколько секунд", "The icon updates on the launcher in a few seconds")) }
            Spacer(Modifier.height(12.dp))
            Muted(tr("Логотип слева вверху", "Top-left logo"))
            Choice(listOf("nodera" to "Nodera", "none" to tr("Без логотипа", "None"), "custom" to tr("Свой", "Custom")), Store.logoMode) {
                if (it == "custom" && !Store.logoFile.exists()) logoPicker.launch("image/*") else Store.applyLogoMode(it)
            }
            if (Store.logoMode == "custom") { Spacer(Modifier.height(8.dp)); SoftButton(tr("Загрузить другой логотип", "Upload another logo"), icon = Icons.Rounded.Upload) { logoPicker.launch("image/*") } }
        }

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

        Muted("Nodera Pay 1.2.0")
        Spacer(Modifier.height(8.dp))
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(tr("Удалить ключи?", "Delete keys?")) },
        text = { Text(tr("Приложение перестанет работать, пока вы не введёте новые ключи.", "The app will stop working until you enter new keys.")) },
        confirmButton = { TextButton({ confirmDelete = false; Store.deleteKeys(); syncWatcher(ctx); onDisconnected() }) { Text(tr("Удалить", "Delete"), color = Bad) } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(tr("Отмена", "Cancel")) } },
    )
}
