package com.nodera.pay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Запись истории: версия, дата и список правок. */
@Composable
fun ReleaseBlock(n: ReleaseNote, current: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Версия", "Version") + " ${n.name}", fontWeight = FontWeight.ExtraBold, color = pal.text)
            if (current) StatusPill(tr("установлена", "installed"), Ok)
            Spacer(Modifier.weight(1f))
            Muted(n.date)
        }
        Spacer(Modifier.height(4.dp))
        n.lines.forEach { Text("•  $it", color = pal.muted, fontSize = 13.sp, lineHeight = 18.sp) }
    }
}

/** Окно при запуске: «Доступно обновление» со списком изменений, скачиванием и установкой. */
@Composable
fun UpdatePrompt(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val n = Updater.latest() ?: return
    var msg by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!Updater.installing) onClose() },
        icon = { Icon(Icons.Rounded.SystemUpdate, null, tint = Accent) },
        title = { Text(tr("Доступно обновление", "Update available")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Новая версия ${n.name}, у вас ${Updater.currentName(ctx)}. Обновитесь: данные и ключи сохранятся.", "New version ${n.name}, you have ${Updater.currentName(ctx)}. Please update: your data and keys stay."))
                ReleaseBlock(n)
                if (Updater.progress >= 0f) LinearProgressIndicator(progress = { Updater.progress }, Modifier.fillMaxWidth(), color = Accent)
                ErrorNote(msg)
            }
        },
        confirmButton = {
            TextButton({ scope.launch { msg = Updater.update(ctx) } }, enabled = !Updater.installing) {
                Text(if (Updater.installing) tr("Скачиваю…", "Downloading…") else tr("Обновить", "Update"))
            }
        },
        dismissButton = { TextButton(onClose, enabled = !Updater.installing) { Text(tr("Позже", "Later")) } },
    )
}

/** Раздел настроек «Обновления»: проверка вручную, установка и история изменений. */
@Composable
fun UpdatesSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var all by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val code = Updater.currentCode(ctx)
    val hist = Updater.history(ctx)
    val avail = Updater.available(ctx)
    Text(tr("ОБНОВЛЕНИЯ", "UPDATES"), color = pal.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
    Card(tint = if (avail) Accent else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconBadge(if (avail) Icons.Rounded.SystemUpdate else Icons.Rounded.CheckCircle, if (avail) Accent else Ok)
            Column(Modifier.weight(1f)) {
                Text(
                    if (avail) tr("Доступна версия ${Updater.latest()?.name}", "Version ${Updater.latest()?.name} is available") else tr("Установлена последняя версия", "You have the latest version"),
                    fontWeight = FontWeight.Bold, color = pal.text,
                )
                Muted(tr("Сейчас ${Updater.currentName(ctx)}. Приложение само проверяет релиз раз в полчаса и при запуске.", "Now ${Updater.currentName(ctx)}. The app checks the release every half hour and on launch."))
            }
        }
        Updater.error?.let { Spacer(Modifier.height(6.dp)); ErrorNote(it) }
        msg?.let { Spacer(Modifier.height(6.dp)); ErrorNote(it) }
        if (Updater.progress >= 0f) { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress = { Updater.progress }, Modifier.fillMaxWidth(), color = Accent) }
        Spacer(Modifier.height(10.dp))
        if (avail) PrimaryButton(tr("Обновить до ${Updater.latest()?.name}", "Update to ${Updater.latest()?.name}"), icon = Icons.Rounded.Download, loading = Updater.installing) {
            scope.launch { msg = Updater.update(ctx) }
        } else SoftButton(if (Updater.checking) tr("Проверяю…", "Checking…") else tr("Проверить обновления", "Check for updates"), Modifier.fillMaxWidth(), Icons.Rounded.Refresh) {
            if (!Updater.checking) scope.launch { msg = null; Updater.check(ctx) }
        }
        Spacer(Modifier.height(14.dp))
        Text(tr("История изменений", "Changelog"), fontWeight = FontWeight.Bold, color = pal.text)
        (if (all) hist else hist.take(2)).forEach { ReleaseBlock(it, it.code == code) }
        if (hist.size > 2) {
            Spacer(Modifier.height(6.dp))
            SoftButton(if (all) tr("Свернуть", "Collapse") else tr("Показать все версии", "Show all versions"), Modifier.fillMaxWidth(), if (all) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore) { all = !all }
        }
    }
}
