package com.nodera.pay

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    var toCancel by remember { mutableStateOf<JSONObject?>(null) }
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
                    if (tab == "appeals") AppealRow(r) else TxRow(r, currency, if (canCancel(r)) ({ toCancel = r }) else null) { open = r }
                }
            }
        }
    }
    open?.let { TxDetail(it, currency, onClose = { open = null }, onChanged = { refreshKey++ }) }
    toCancel?.let { CancelConfirm(it, onClose = { toCancel = null }) { toCancel = null; refreshKey++ } }
}

/** Отменить можно заявку, которая еще ждет оплаты и без чека (на проверке отмена недоступна). */
private fun canCancel(r: JSONObject) = r.optString("status") in listOf("created", "offer", "accepted")

@Composable
private fun CancelConfirm(r: JSONObject, onClose: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(Icons.Rounded.Close, null, tint = Bad) },
        title = { Text(tr("Отменить заявку", "Cancel order") + " #${r.optInt("id")}?") },
        text = { Text(tr("Используйте, если оплата не прошла или клиент передумал. Ссылка и QR перестанут работать.", "Use it if the payment failed or the customer changed their mind. The link and QR stop working.")) },
        confirmButton = {
            TextButton({
                busy = true
                scope.launch {
                    try { Api.cancelTransaction(r.optInt("id")); toast(ctx, tr("Заявка отменена", "Order canceled")); onDone() }
                    catch (e: ApiException) { toast(ctx, e.message ?: ""); onClose() }
                }
            }, enabled = !busy) { Text(tr("Отменить заявку", "Cancel order"), color = Bad) }
        },
        dismissButton = { TextButton(onClose) { Text(tr("Назад", "Back")) } },
    )
}

@Composable
private fun TxRow(r: JSONObject, currency: String, onCancel: (() -> Unit)?, onClick: () -> Unit) {
    val (label, color) = statusLabel(r.optString("status"))
    Card(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MethodLogo(fixUrl(r.optString("methodLogo")), Modifier.size(48.dp), radius = 14, pad = 5, bg = r.optStr("methodBg"), cover = r.optBoolean("methodCover"))
            Column(Modifier.weight(1f)) {
                Text("#${r.optInt("id")}  ·  " + money(r.optDouble("amount"), r.optString("currency", currency)), fontWeight = FontWeight.Bold, color = pal.text)
                Muted(shortDate(r.optString("createdAt")) + (r.optString("method").takeIf { it.isNotBlank() && it != "null" }?.let { "  ·  $it" } ?: ""))
                if (r.optString("status") == "completed") Text(tr("Зачислено ", "Credited ") + money(r.optDouble("amount") - r.optDouble("fee", 0.0), r.optString("currency", currency)), color = Ok, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            StatusPill(label, color)
            if (onCancel != null) Box(
                Modifier.size(34.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Bad.copy(alpha = .14f)).clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, tr("Отменить заявку", "Cancel order"), tint = Bad, modifier = Modifier.size(20.dp)) }
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
        val why = r.optString("reject_reason").takeIf { it.isNotBlank() && it != "null" }
            ?: r.optJSONObject("last_event")?.optString("comment")?.takeIf { st == "rejected" && it.isNotBlank() && it != "null" }
        if (st == "rejected") Text(tr("Причина отказа: ", "Rejection reason: ") + (why ?: tr("не указана", "not specified")), color = Bad, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun cancelReasonLabel(code: String?): String? = when (code) {
    null, "", "null" -> null
    "canceled_by_merchant" -> tr("Отменена мерчантом", "Canceled by merchant")
    "receipt_rejected" -> tr("Чек отклонён", "Receipt rejected")
    "expired", "timeout" -> tr("Истекло время оплаты", "Payment time expired")
    "canceled_by_customer" -> tr("Отменена клиентом", "Canceled by customer")
    else -> code
}

private fun eventLabel(type: String): Pair<String, androidx.compose.ui.graphics.vector.ImageVector> = when (type) {
    "created" -> tr("Заявка создана", "Order created") to Icons.Rounded.Add
    "offer", "accepted" -> tr("Выданы реквизиты для оплаты", "Payment details issued") to Icons.Rounded.CreditCard
    "proof" -> tr("Чек отправлен на проверку", "Receipt sent for review") to Icons.Rounded.AttachFile
    "completed" -> tr("Оплата подтверждена", "Payment confirmed") to Icons.Rounded.CheckCircle
    "canceled" -> tr("Заявка закрыта без оплаты", "Order closed unpaid") to Icons.Rounded.Close
    "change" -> tr("Изменены сумма или метод", "Amount or method changed") to Icons.Rounded.Edit
    "appeal" -> tr("Открыт диспут", "Dispute opened") to Icons.Rounded.Gavel
    else -> type to Icons.Rounded.Info
}

private fun roleLabel(by: String) = when (by) {
    "merchant" -> tr("мерчант", "merchant"); "trader" -> tr("исполнитель", "provider"); "operator" -> tr("оператор", "operator"); "customer" -> tr("клиент", "customer"); else -> tr("система", "system")
}

@Composable
private fun DetailRow(label: String, value: String?, valueColor: Color? = null) {
    if (value.isNullOrBlank() || value == "null") return
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = pal.muted, fontSize = 13.sp, modifier = Modifier.weight(0.42f))
        Text(value, color = valueColor ?: pal.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.58f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

/** Карточка заявки в духе админки: статус, суммы, метод и получатель, ход заявки по этапам, действия. */
@Composable
private fun TxDetail(r: JSONObject, currency: String, onClose: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val id = r.optInt("id")
    val full = rememberLoader(id) { Api.transaction(id) }
    val hist = rememberLoader(id) { Api.history(id).objects() }
    val f = full.data ?: r
    val status = f.optString("status", r.optString("status"))
    val (label, color) = statusLabel(status)
    val cur = f.optString("currency", r.optString("currency", currency))
    val amount = f.optDouble("amount", r.optDouble("amount"))
    val fee = f.optDouble("fee", r.optDouble("fee", 0.0)).let { if (it.isNaN()) 0.0 else it }
    val link = r.optString("link")
    var dispute by remember { mutableStateOf(false) }
    var cancel by remember { mutableStateOf(false) }
    var proof by remember { mutableStateOf(false) }
    // Диспут — только по закрытой без оплаты заявке: пока она в работе, он недоступен.
    val closedUnpaid = status in listOf("canceled", "expired", "failed")
    val canDispute = closedUnpaid && !r.optBoolean("hasAppeal")
    val inWork = status in listOf("created", "offer", "accepted", "proof")
    Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(24.dp)).background(pal.bg).padding(18.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MethodLogo(fixUrl(r.optString("methodLogo")), Modifier.size(48.dp), radius = 14, pad = 5, bg = r.optStr("methodBg"), cover = r.optBoolean("methodCover"))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) { Title(tr("Заявка", "Order") + " #$id"); Muted(shortDate(f.optString("createdAt", r.optString("createdAt")))) }
                StatusPill(label, color)
            }
            Card {
                Text(money(amount, cur), fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = pal.text)
                if (status == "completed") Text(tr("Зачислено ", "Credited ") + money(amount - fee, cur), color = Ok, fontWeight = FontWeight.Bold)
                else if (inWork) Muted(tr("Ожидает оплаты клиентом", "Waiting for the customer to pay"))
            }
            Card {
                DetailRow(tr("Метод оплаты", "Payment method"), r.optString("method"))
                DetailRow(tr("Банк получателя", "Receiving bank"), f.optString("bankReceiver"))
                DetailRow(tr("Получатель", "Recipient"), f.optString("cardOwner"))
                DetailRow("Order ID", f.optString("orderId", r.optString("orderId")))
                DetailRow(tr("Сумма заявки", "Order amount"), money(amount, cur))
                if (fee > 0) DetailRow(tr("Комиссия", "Fee"), money(fee, cur))
                DetailRow(tr("Причина закрытия", "Closing reason"), cancelReasonLabel(f.optString("cancelReason", r.optString("cancelReason"))), Bad)
                DetailRow(tr("Создана", "Created"), shortDate(f.optString("createdAt", r.optString("createdAt"))))
                DetailRow(tr("Обновлена", "Updated"), shortDate(f.optString("updatedAt", r.optString("updatedAt"))))
            }
            val h = hist.data
            if (!h.isNullOrEmpty()) Card {
                Text(tr("Ход заявки", "Order timeline"), fontWeight = FontWeight.Bold, color = pal.text)
                Spacer(Modifier.height(8.dp))
                h.forEach { e ->
                    val (txt, ic) = eventLabel(e.optString("type"))
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconBadge(ic, if (e.optString("type") == "completed") Ok else if (e.optString("type") == "canceled") Bad else Accent, 32)
                        Column(Modifier.weight(1f)) {
                            Text(txt, color = pal.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Muted(shortDate(e.optString("at")) + "  ·  " + roleLabel(e.optString("by")))
                        }
                    }
                }
            } else if (hist.loading || full.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
            ErrorNote(full.error)
            if (status == "created" && link.isNotBlank()) {
                QrView(link, Modifier.fillMaxWidth(0.6f).align(Alignment.CenterHorizontally))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton(tr("Копировать", "Copy"), Modifier.weight(1f), Icons.Rounded.ContentCopy) { copy(ctx, link) }
                    SoftButton(tr("Поделиться", "Share"), Modifier.weight(1f), Icons.Rounded.Share) { share(ctx, link) }
                }
            }
            if (canCancel(r.put("status", status))) SoftButton(tr("Отменить заявку", "Cancel order"), Modifier.fillMaxWidth(), Icons.Rounded.Close, Bad) { cancel = true }
            if (status in listOf("offer", "accepted")) SoftButton(tr("Добавить чек (пруф)", "Add receipt (proof)"), Modifier.fillMaxWidth(), Icons.Rounded.AttachFile) { proof = true }
            if (canDispute) SoftButton(tr("Открыть диспут", "Open dispute"), Modifier.fillMaxWidth(), Icons.Rounded.Gavel, Warn) { dispute = true }
            else if (inWork) Muted(tr("Диспут можно открыть после того, как заявка закроется без оплаты.", "A dispute can be opened once the order closes unpaid."))
            if (r.optBoolean("hasAppeal")) Muted(tr("По заявке уже есть диспут — смотрите вкладку «Диспуты».", "A dispute already exists — see the “Disputes” tab."))
            SoftButton(tr("Закрыть", "Close"), Modifier.fillMaxWidth()) { onClose() }
        }
    }
    if (cancel) CancelConfirm(r, { cancel = false }) { cancel = false; onChanged(); onClose() }
    if (proof) ProofDialog(id, { proof = false }) { proof = false; onChanged(); onClose() }
    if (dispute) DisputeDialog(id, cur, { dispute = false }) { dispute = false; onChanged(); onClose() }
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
            Muted(tr("Заявка закрыта, а клиент уверяет, что оплатил. Приложите чек — без него диспут не создаётся.", "The order is closed but the customer says they paid. Attach the receipt — a dispute cannot be created without it."))
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

/** Чек от клиента, который мерчант добавляет к заявке: она уходит на проверку перевода. */
@Composable
private fun ProofDialog(txId: Int, onClose: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
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
                else -> { error = null; base64 = Base64.encodeToString(bytes, Base64.NO_WRAP); fileName = uri.lastPathSegment?.substringAfterLast('/') ?: tr("чек", "receipt") }
            }
        }
    }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(pal.bg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Title(tr("Чек по заявке", "Receipt for order") + " #$txId")
            Muted(tr("Приложите чек о переводе от клиента (фото или PDF до 5 МБ). Заявка уйдёт на проверку.", "Attach the customer's transfer receipt (photo or PDF up to 5 MB). The order goes to review."))
            SoftButton(fileName ?: tr("Выбрать файл", "Choose file"), Modifier.fillMaxWidth(), if (base64 == null) Icons.Rounded.AttachFile else Icons.Rounded.CheckCircle, if (base64 == null) Accent else Ok) { picker.launch(arrayOf("image/*", "application/pdf")) }
            ErrorNote(error)
            PrimaryButton(tr("Отправить чек", "Send receipt"), enabled = base64 != null, loading = busy) {
                busy = true; error = null
                scope.launch {
                    try { Api.addProof(txId, base64!!); toast(ctx, tr("Чек отправлен", "Receipt sent")); onDone() } catch (e: ApiException) { error = e.message }
                    busy = false
                }
            }
            SoftButton(tr("Отмена", "Cancel"), Modifier.fillMaxWidth()) { onClose() }
        }
    }
}
