package com.nodera.pay

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Сколько активных заявок и выплат показываем сверху: новая вытесняет самую старую, остальные видны в архиве после завершения. */
const val ACTIVE_LIMIT = 3

/**
 * «Выплаты»: создание выплаты (сумма, карта или телефон, банк, ФИО), активные и архив.
 * Карточка окрашена цветом статуса, сверху горит плашка «активная выплата».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayoutsScreen() {
    val kinds = listOf("active", "archive")
    val pager = rememberPagerState { kinds.size }
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<JSONObject?>(null) }
    var creating by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var csv by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { Api.resolveFiat() }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Title(tr("Выплаты", "Payouts"))
            if (Store.activePayouts > 0) ActiveBanner(
                if (Store.activePayouts == 1) tr("Активная выплата", "Active payout") else tr("Активных выплат: ${Store.activePayouts}", "Active payouts: ${Store.activePayouts}"), Accent,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(tr("Активные", "Active"), pager.currentPage == 0) { scope.launch { pager.animateScrollToPage(0) } }
                Chip(tr("Архив", "Archive"), pager.currentPage == 1) { scope.launch { pager.animateScrollToPage(1) } }
            }
        }
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            PayoutsPage(kinds[page], refreshKey, onNew = { creating = true }, onCsv = { csv = true }, onOpen = { open = it })
        }
    }
    if (creating) PayoutForm(onClose = { creating = false }) { creating = false; refreshKey++; scope.launch { pager.animateScrollToPage(0) } }
    open?.let { PayoutDetail(it, onClose = { open = null }, onChanged = { refreshKey++ }) }
    if (csv) CsvDialog("payouts", onClose = { csv = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayoutsPage(kind: String, refreshKey: Int, onNew: () -> Unit, onCsv: () -> Unit, onOpen: (JSONObject) -> Unit) {
    val list = rememberLoader(kind to refreshKey) { Api.payoutRows(kind) }
    AutoRefresh(if (kind == "active") 5000 else 15000) { list.reload(true) }
    PullToRefreshBox(isRefreshing = list.loading, onRefresh = { list.reload() }, modifier = Modifier.fillMaxSize()) {
        val all = list.data
        val rows = if (kind == "active") all?.take(ACTIVE_LIMIT) else all
        val empty = rows != null && rows.isEmpty()
        if (empty) TumbleweedScene(Modifier.align(Alignment.BottomCenter))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            if (kind == "active") { if (!empty) item { PrimaryButton(tr("Новая выплата", "New payout"), icon = Icons.Rounded.Add, onClick = onNew) } }
            else item { SoftButton(tr("Выгрузить CSV", "Export CSV"), Modifier.fillMaxWidth(), Icons.Rounded.Download, onClick = onCsv) }
            if (list.error != null) item { ErrorNote(list.error) }
            items(rows ?: emptyList()) { r -> PayoutRow(r) { onOpen(r) } }
            if (kind == "active" && (all?.size ?: 0) > ACTIVE_LIMIT) item {
                Muted(tr("Показаны ${ACTIVE_LIMIT} последние из ${all!!.size}. Остальные появятся в архиве, когда завершатся.", "Showing the latest $ACTIVE_LIMIT of ${all!!.size}. The rest move to the archive when finished."))
            }
        }
        if (empty) {
            if (kind == "active") EmptyNote(tr("Активных выплат нет", "No active payouts"), tr("Создайте выплату кнопкой ниже.", "Create a payout with the button below."), Modifier.fillMaxSize()) {
                PrimaryButton(tr("Новая выплата", "New payout"), icon = Icons.Rounded.Add, onClick = onNew)
            } else EmptyNote(tr("Архив пуст", "The archive is empty"), tr("Завершённые выплаты появятся здесь.", "Finished payouts will appear here."), Modifier.fillMaxSize())
        }
    }
}

private fun maskNumber(n: String): String {
    val d = n.filter { it.isLetterOrDigit() }
    return if (d.length <= 4) n else "•••• " + d.takeLast(4)
}

@Composable
private fun PayoutRow(r: JSONObject, onClick: () -> Unit) {
    val (label, color) = payoutStatusLabel(r.optString("status"))
    Card(onClick = onClick, tint = color) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(when (r.optString("status")) { "Success" -> Icons.Rounded.CheckCircle; "Canceled" -> Icons.Rounded.Close; else -> Icons.Rounded.NorthEast }, color, 48)
            Column(Modifier.weight(1f)) {
                Text("#${r.optInt("id")}  ·  " + money(r.optDouble("amount"), curOf(r, true)), fontWeight = FontWeight.Bold, color = pal.text)
                Muted(listOfNotNull(r.optStr("bankname"), r.optStr("number")?.let { maskNumber(it) }).joinToString("  ·  "))
                Muted(shortDate(r.optString("createdAt")))
            }
            StatusPill(label, color)
        }
    }
}

/** Карточка выплаты: все поля, подтверждение и отмена, пока выплата в очереди. */
@Composable
private fun PayoutDetail(initial: JSONObject, onClose: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val id = initial.optInt("id")
    var r by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(id) {
        while (true) {
            runCatching { r = Api.payout(id) }
            if (r.optString("status") !in setOf("Pending", "Processing")) break
            kotlinx.coroutines.delay(4000)
        }
    }
    val status = r.optString("status")
    val (label, color) = payoutStatusLabel(status)
    val cur = curOf(r, true)
    val fee = r.optDouble("fee", 0.0)
    val amount = r.optDouble("amount")
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(pal.bg).systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(pal.surface2).clickable(onClick = onClose), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ArrowBack, null, tint = pal.text) }
                Text(tr("Выплата", "Payout") + " #$id", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = pal.text, modifier = Modifier.weight(1f))
                StatusPill(label, color)
            }
            Card(tint = color) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Muted(tr("Сумма выплаты", "Payout amount"))
                    Text(money(amount, cur), fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, color = pal.text)
                    Spacer(Modifier.height(8.dp))
                    if (status == "Success") ResultBadge(true, 72.dp) else if (status == "Canceled") ResultBadge(false, 72.dp) else Spinner(40.dp, color)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when (status) { "Success" -> tr("Выплата выполнена", "Payout completed"); "Canceled" -> tr("Выплата отменена", "Payout canceled"); "Processing" -> tr("Исполнитель переводит деньги", "A provider is sending the money"); else -> tr("Ждёт исполнителя", "Waiting for a provider") },
                        color = color, fontWeight = FontWeight.Bold,
                    )
                }
            }
            Card {
                PRow(tr("Получатель", "Recipient"), r.optStr("owner"))
                PRow(tr("Карта или телефон", "Card or phone"), r.optStr("number"))
                PRow(tr("Банк", "Bank"), r.optStr("bankname"))
                PRow(tr("Комиссия", "Fee"), if (r.has("fee")) "${money(fee)} %" else null)
                PRow(tr("Сумма с комиссией", "Amount with fee"), if (r.has("fee")) money(amount * (1 + fee / 100), cur) else null)
                PRow(tr("Курс", "Rate"), r.optStr("rate")?.takeIf { it != "1" && it != "1.0" })
                PRow(tr("Ваш ID (uid)", "Your ID (uid)"), r.optStr("uid"))
                PRow(tr("Комментарий", "Comment"), r.optStr("comment"))
                PRow(tr("Создана", "Created"), shortDate(r.optString("createdAt")))
                PRow(tr("Завершена", "Closed"), r.optStr("closeDate")?.let { shortDate(it) })
            }
            r.optStr("proof")?.let { p -> SoftButton(tr("Открыть чек выплаты", "Open payout receipt"), Modifier.fillMaxWidth(), Icons.Rounded.Receipt) { openUrl(ctx, p) } }
            if (status == "Pending") SoftButton(tr("Отменить выплату", "Cancel payout"), Modifier.fillMaxWidth(), Icons.Rounded.Close, Bad) { confirm = true }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        icon = { Icon(Icons.Rounded.Close, null, tint = Bad) },
        title = { Text(tr("Отменить выплату", "Cancel payout") + " #$id?") },
        text = { Text(tr("Отмена возможна, пока выплату не взял исполнитель. Деньги вернутся на баланс.", "You can cancel until a provider takes it. The money returns to your balance.")) },
        confirmButton = {
            TextButton({
                busy = true
                scope.launch {
                    try { r = Api.cancelPayout(id); toast(ctx, tr("Выплата отменена", "Payout canceled")); onChanged() } catch (e: ApiException) { toast(ctx, e.message ?: "") }
                    busy = false; confirm = false
                }
            }, enabled = !busy) { Text(tr("Отменить выплату", "Cancel payout"), color = Bad) }
        },
        dismissButton = { TextButton({ confirm = false }) { Text(tr("Назад", "Back")) } },
    )
}

@Composable
private fun PRow(label: String, value: String?) {
    if (value.isNullOrBlank() || value == "null") return
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = pal.muted, fontSize = 13.sp, modifier = Modifier.weight(0.42f))
        Text(value, color = pal.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.58f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

/** Форма новой выплаты: сумма, карта или телефон, банк, ФИО; перед отправкой — подтверждение. */
@Composable
private fun PayoutForm(onClose: () -> Unit, onCreated: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val cur = Store.payoutCur
    val a = parseAmount(amount)
    val ok = a != null && number.count { it.isDigit() } >= 6 && bank.isNotBlank() && owner.isNotBlank()

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(pal.bg).systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(pal.surface2).clickable(onClick = onClose), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, null, tint = pal.text) }
                Text(tr("Новая выплата", "New payout"), fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = pal.text)
            }
            Muted(tr("Выплату клиенту на карту или телефон выполнит исполнитель Nodera. Комиссия и курс появятся в карточке после создания.", "A Nodera provider sends the payout to the customer's card or phone. The fee and rate appear in the card after creation."))
            Field(amount, { amount = it }, tr("Сумма", "Amount") + if (cur.isNotBlank()) ", $cur" else "", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Field(number, { number = it.filter { c -> c.isDigit() || c in "+ -" }.take(32) }, tr("Номер карты или телефона", "Card or phone number"), keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone))
            Field(bank, { bank = it.take(60) }, tr("Банк получателя", "Recipient bank"))
            Field(owner, { owner = it.take(80) }, tr("ФИО получателя", "Recipient full name"))
            ErrorNote(error)
            PrimaryButton(tr("Создать выплату", "Create payout"), icon = Icons.Rounded.NorthEast, enabled = ok, loading = busy) { confirm = true }
        }
    }
    if (confirm && a != null) AlertDialog(
        onDismissRequest = { if (!busy) confirm = false },
        title = { Text(tr("Отправить выплату?", "Send the payout?")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(money(a, cur), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(owner.trim()); Text(number.trim()); Text(bank.trim())
                Spacer(Modifier.height(4.dp))
                Text(tr("Проверьте реквизиты: деньги уйдут реальному получателю.", "Check the details: the money goes to a real recipient."), fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton({
                if (busy) return@TextButton
                busy = true; error = null
                scope.launch {
                    try { Api.createPayout(a, number.trim(), bank.trim(), owner.trim()); toast(ctx, tr("Выплата создана", "Payout created")); onCreated() }
                    catch (e: ApiException) { error = e.message; confirm = false }
                    busy = false
                }
            }, enabled = !busy) { Text(tr("Отправить", "Send")) }
        },
        dismissButton = { TextButton({ confirm = false }, enabled = !busy) { Text(tr("Назад", "Back")) } },
    )
}

/** Выгрузка CSV за период: /export/payouts или /export/transactions (до 31 дня), файл сохраняется туда, куда укажет пользователь. */
@Composable
fun CsvDialog(kind: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var days by remember { mutableStateOf(7) }
    var field by remember { mutableStateOf("created") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val data = pending; pending = null
        if (uri != null && data != null) {
            runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(data) } }
                .onSuccess { toast(ctx, tr("Файл сохранён", "File saved")); onClose() }
                .onFailure { error = tr("Не удалось сохранить файл", "Could not save the file") }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        icon = { Icon(Icons.Rounded.Download, null, tint = Accent) },
        title = { Text(if (kind == "payouts") tr("CSV: выплаты", "CSV: payouts") else tr("CSV: заявки", "CSV: orders")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Muted(tr("Период", "Period"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to tr("Сутки", "1 day"), 7 to tr("7 дней", "7 days"), 31 to tr("31 день", "31 days")).forEach { (d, l) -> Chip(l, days == d) { days = d } }
                }
                Muted(tr("Считать по дате", "Date field"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(tr("Создания", "Created"), field == "created") { field = "created" }
                    Chip(tr("Закрытия", "Closed"), field == "closed") { field = "closed" }
                }
                Muted(tr("Файл в формате CSV (разделитель «;», UTF-8). За больший период выгружайте по частям.", "CSV file (delimiter “;”, UTF-8). For longer periods export in parts."))
                ErrorNote(error)
            }
        },
        confirmButton = {
            TextButton({
                busy = true; error = null
                scope.launch {
                    try {
                        val now = java.time.Instant.now()
                        pending = Api.exportCsv(kind, now.minusSeconds(days * 86400L - 120), now, field, null)
                        saver.launch("${kind}_${java.time.LocalDate.now()}.csv")
                    } catch (e: ApiException) { error = e.message }
                    busy = false
                }
            }, enabled = !busy) { Text(if (busy) tr("Готовлю…", "Preparing…") else tr("Скачать", "Download")) }
        },
        dismissButton = { TextButton(onClose, enabled = !busy) { Text(tr("Закрыть", "Close")) } },
    )
}
