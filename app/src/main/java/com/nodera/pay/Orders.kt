package com.nodera.pay

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONObject

/** «Заявки»: активные транзакции, архив и диспуты. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen() {
    var tab by rememberSaveable { mutableStateOf("active") }
    var open by remember { mutableStateOf<JSONObject?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val list = rememberLoader(tab to refreshKey) {
        if (tab == "appeals") Api.appeals().getJSONArray("rows").objects() else Api.transactions(tab, 1).getJSONArray("rows").objects()
    }
    val balance = rememberLoader(Store.apiKey) { Api.balance() }
    val currency = balance.data?.optString("currency").orEmpty()

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Title(tr("Заявки", "Orders"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(tr("Активные", "Active"), tab == "active") { tab = "active" }
                Chip(tr("Архив", "Archive"), tab == "archive") { tab = "archive" }
                Chip(tr("Диспуты", "Disputes"), tab == "appeals") { tab = "appeals" }
            }
        }
        PullToRefreshBox(isRefreshing = list.loading, onRefresh = { list.reload() }, modifier = Modifier.fillMaxSize()) {
            val rows = list.data
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (list.error != null) item { ErrorNote(list.error) }
                if (rows != null && rows.isEmpty()) item {
                    Card { Muted(when (tab) { "active" -> tr("Активных заявок нет.", "No active orders."); "archive" -> tr("Архив пуст.", "Archive is empty."); else -> tr("Диспутов нет.", "No disputes.") }) }
                }
                items(rows ?: emptyList()) { r ->
                    if (tab == "appeals") AppealRow(r) else TxRow(r, currency) { open = r }
                }
            }
        }
    }
    open?.let { TxDetail(it, currency, onClose = { open = null }, onChanged = { refreshKey++ }) }
}

@Composable
private fun TxRow(r: JSONObject, currency: String, onClick: () -> Unit) {
    val (label, color) = statusLabel(r.optString("status"))
    Card(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val logo = r.optString("methodLogo").takeIf { it.isNotBlank() && it != "null" }
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(pal.surface2), contentAlignment = Alignment.Center) {
                if (logo != null) AsyncImage(logo, null, Modifier.size(32.dp), contentScale = ContentScale.Fit) else Icon(Icons.Rounded.Receipt, null, tint = Accent)
            }
            Column(Modifier.weight(1f)) {
                Text("#${r.optInt("id")}  ·  " + money(r.optDouble("amount"), r.optString("currency", currency)), fontWeight = FontWeight.Bold, color = pal.text)
                Muted(shortDate(r.optString("createdAt")) + (r.optString("method").takeIf { it.isNotBlank() && it != "null" }?.let { "  ·  $it" } ?: ""))
            }
            StatusPill(label, color)
        }
    }
}

@Composable
private fun AppealRow(r: JSONObject) {
    val st = r.optString("status")
    val (label, color) = when (st) {
        "approved" -> tr("Принят", "Approved") to Ok
        "rejected" -> tr("Отклонён", "Rejected") to Bad
        "canceled" -> tr("Отменён", "Canceled") to Bad
        else -> tr("В работе", "In progress") to Warn
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(Icons.Rounded.Gavel, color)
            Column(Modifier.weight(1f)) {
                Text(tr("Диспут", "Dispute") + " #" + r.optString("num") + "  ·  " + tr("заявка", "order") + " #" + r.optString("payment_num"), fontWeight = FontWeight.Bold, color = pal.text)
                Muted(shortDate(r.optString("created_at")))
            }
            StatusPill(label, color)
        }
    }
}

@Composable
private fun TxDetail(r: JSONObject, currency: String, onClose: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val (label, color) = statusLabel(r.optString("status"))
    val link = r.optString("link")
    var dispute by remember { mutableStateOf(false) }
    // Диспут имеет смысл, если платеж отправлен клиентом, но не подтвержден: заявка еще не оплачена и не закрыта без оплаты.
    val canDispute = r.optString("status") != "completed" && !r.optBoolean("hasAppeal")
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(pal.bg).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Title(tr("Заявка", "Order") + " #${r.optInt("id")}"); Spacer(Modifier.weight(1f)); StatusPill(label, color)
            }
            Text(money(r.optDouble("amount"), r.optString("currency", currency)), fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = pal.text)
            Muted(shortDate(r.optString("createdAt")))
            r.optString("method").takeIf { it.isNotBlank() && it != "null" }?.let { Muted(tr("Метод: ", "Method: ") + it) }
            r.optString("cancelReason").takeIf { it.isNotBlank() && it != "null" }?.let { Muted(tr("Причина: ", "Reason: ") + it) }
            if (r.optString("status") == "created" && link.isNotBlank()) {
                QrView(link, Modifier.fillMaxWidth(0.6f).align(Alignment.CenterHorizontally))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton(tr("Копировать", "Copy"), Modifier.weight(1f), Icons.Rounded.ContentCopy) { copy(ctx, link) }
                    SoftButton(tr("Поделиться", "Share"), Modifier.weight(1f), Icons.Rounded.Share) { share(ctx, link) }
                }
            }
            if (canDispute) SoftButton(tr("Открыть диспут", "Open dispute"), Modifier.fillMaxWidth(), Icons.Rounded.Gavel, Warn) { dispute = true }
            if (r.optBoolean("hasAppeal")) Muted(tr("По заявке уже есть диспут — смотрите вкладку «Диспуты».", "A dispute already exists — see the “Disputes” tab."))
            SoftButton(tr("Закрыть", "Close"), Modifier.fillMaxWidth()) { onClose() }
        }
    }
    if (dispute) DisputeDialog(r.optInt("id"), r.optString("currency", currency), { dispute = false }) { dispute = false; onChanged(); onClose() }
}

/** Диспут по отправленному, но не подтвержденному платежу. Чек обязателен. */
@Composable
private fun DisputeDialog(txId: Int, currency: String, onClose: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var claimed by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf<String?>(null) }
    var base64 by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val bytes = runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            when {
                bytes == null -> error = tr("Не удалось прочитать файл", "Cannot read the file")
                bytes.size > 5 * 1024 * 1024 -> error = tr("Чек больше 5 МБ", "Receipt is larger than 5 MB")
                else -> {
                    error = null; base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    fileName = uri.lastPathSegment?.substringAfterLast('/') ?: tr("чек", "receipt")
                }
            }
        }
    }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(pal.bg).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Title(tr("Диспут по заявке", "Dispute") + " #$txId")
            Muted(tr("Платёж отправлен, но не подтверждён. Приложите чек — без него диспут не создаётся.", "The payment was sent but not confirmed. Attach the receipt — a dispute cannot be created without it."))
            SoftButton(fileName ?: tr("Прикрепить чек (фото или PDF)", "Attach receipt (photo or PDF)"), Modifier.fillMaxWidth(), if (base64 == null) Icons.Rounded.AttachFile else Icons.Rounded.CheckCircle,
                if (base64 == null) Accent else Ok) { picker.launch(arrayOf("image/*", "application/pdf")) }
            Field(claimed, { claimed = it }, tr("Фактически переведённая сумма, $currency (если отличается)", "Amount actually sent, $currency (if different)"),
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Field(reason, { reason = it.take(1000) }, tr("Комментарий", "Comment"), singleLine = false)
            ErrorNote(error)
            PrimaryButton(tr("Отправить диспут", "Submit dispute"), enabled = base64 != null, loading = busy) {
                busy = true; error = null
                scope.launch {
                    try { Api.createAppeal(txId, reason, parseAmount(claimed), base64!!); toast(ctx, tr("Диспут создан", "Dispute created")); onDone() }
                    catch (e: ApiException) { error = e.message }
                    busy = false
                }
            }
            SoftButton(tr("Отмена", "Cancel"), Modifier.fillMaxWidth()) { onClose() }
        }
    }
}
