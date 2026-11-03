package com.nodera.pay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Экран «QR» (mode = "qr") и «Ссылка на оплату» (mode = "link"): сумма или услуга → метод(ы) → заявка на депозит → QR/ссылка.
 * QR и ссылка ведут на платежную страницу Nodera; заявка создается в кабинете мерчанта как обычный депозит.
 */
@Composable
fun CheckoutScreen(mode: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isQr = mode == "qr"
    val methods = rememberLoader(Store.apiKey) { Api.methods().objects() }
    val balance = rememberLoader(Store.apiKey) { Api.balance() }
    val currency = balance.data?.optString("currency").orEmpty()

    var source by rememberSaveable(mode) { mutableStateOf("amount") }
    var amountText by rememberSaveable(mode) { mutableStateOf("") }
    var selected by remember(mode) { mutableStateOf(setOf<Int>()) }
    var service by remember(mode) { mutableStateOf<Service?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember(mode) { mutableStateOf<org.json.JSONObject?>(null) }
    var showCard by remember { mutableStateOf<Service?>(null) }
    var editor by remember { mutableStateOf<Service?>(null) }
    var newService by remember { mutableStateOf(false) }

    val amount = parseAmount(amountText)
    val canCreate = amount != null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Title(if (isQr) tr("QR для оплаты", "Payment QR") else tr("Ссылка на оплату", "Payment link"))
        Muted(if (isQr) tr("Покажите QR клиенту: он откроет платёжную страницу Nodera.", "Show the QR to your customer: it opens the Nodera payment page.")
        else tr("Отправьте ссылку клиенту удалённо. Методы можно ограничить или оставить на выбор клиента.", "Send the link remotely. Limit the methods or let the customer choose."))

        val r = result
        if (r != null) {
            ResultCard(r, isQr, currency) { result = null; amountText = ""; service = null; selected = emptySet() }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(tr("Своя сумма", "Custom amount"), source == "amount") { source = "amount"; service = null }
            Chip(tr("Услуги и товары", "Services"), source == "services") { source = "services" }
        }

        if (source == "services") {
            if (Store.services.isEmpty()) Card { Muted(tr("Добавьте услуги с готовыми суммами, например «Ремонт ПК — 1000».", "Add services with preset prices, e.g. “PC repair — 1000”.")) }
            Store.services.forEach { s ->
                val on = service?.id == s.id
                Card(onClick = { service = s; amountText = s.amount }, modifier = Modifier.background(if (on) Accent.copy(alpha = .08f) else pal.surface)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge(Icons.Rounded.Storefront)
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontWeight = FontWeight.Bold, color = pal.text)
                            if (s.description.isNotBlank()) Muted(s.description.take(80))
                        }
                        Text(money(s.amount.toDoubleOrNull() ?: 0.0, currency), fontWeight = FontWeight.ExtraBold, color = Accent)
                    }
                    if (on) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftButton(tr("Показать клиенту", "Show customer"), icon = Icons.Rounded.Visibility) { showCard = s }
                        SoftButton(tr("Изменить", "Edit"), icon = Icons.Rounded.Edit) { editor = s }
                    }
                }
            }
            SoftButton(tr("Добавить услугу", "Add service"), Modifier.fillMaxWidth(), Icons.Rounded.Add) { newService = true }
        } else {
            Field(amountText, { amountText = it }, tr("Сумма", "Amount") + if (currency.isNotBlank()) ", $currency" else "",
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }

        if (source == "amount" || service != null) {
            Text(tr("Методы оплаты", "Payment methods"), fontWeight = FontWeight.Bold, color = pal.text)
            val list = methods.data
            when {
                methods.loading && list == null -> Spinner(32.dp)
                methods.error != null -> { ErrorNote(methods.error); SoftButton(tr("Повторить", "Retry"), icon = Icons.Rounded.Refresh) { methods.reload() } }
                list != null -> if (list.isEmpty()) Muted(tr("В кабинете нет доступных методов оплаты.", "No payment methods are available in your account."))
                else MethodPicker(list, selected) { selected = it }
            }
            ErrorNote(error)
            PrimaryButton(if (isQr) tr("Создать QR", "Create QR") else tr("Создать ссылку", "Create link"), icon = if (isQr) Icons.Rounded.QrCode2 else Icons.Rounded.Link,
                enabled = canCreate, loading = busy) {
                busy = true; error = null
                scope.launch {
                    try { result = Api.createLink(amount!!, selected.toList(), "app-" + UUID.randomUUID().toString().take(12)) }
                    catch (e: ApiException) { error = e.message }
                    busy = false
                }
            }
        }
    }

    showCard?.let { s -> ServiceCard(s, currency, onClose = { showCard = null }) }
    if (newService) ServiceEditor(null, { newService = false }) { Store.saveServices(Store.services + it); newService = false }
    editor?.let { e ->
        ServiceEditor(e, { editor = null }, onDelete = { Store.saveServices(Store.services.filter { it.id != e.id }); service = null; editor = null }) { upd ->
            Store.saveServices(Store.services.map { if (it.id == e.id) upd else it }); service = upd; amountText = upd.amount; editor = null
        }
    }
}

@Composable
private fun ResultCard(r: org.json.JSONObject, isQr: Boolean, currency: String, onNew: () -> Unit) {
    val ctx = LocalContext.current
    val link = r.getString("link")
    val id = r.getInt("id")
    var status by remember(id) { mutableStateOf("created") }
    LaunchedEffect(id) {
        while (true) {
            runCatching { status = Api.transaction(id).optString("status", status) }
            if (status in listOf("completed", "canceled", "failed", "expired")) break
            delay(4000)
        }
    }
    val (label, color) = statusLabel(status)
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Заявка", "Order") + " #$id", fontWeight = FontWeight.Bold, color = pal.text, modifier = Modifier.weight(1f))
            StatusPill(label, color)
        }
        Spacer(Modifier.height(12.dp))
        QrView(link, Modifier.fillMaxWidth(0.8f).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        Text(link, color = Accent, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { openUrl(ctx, link) })
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SoftButton(tr("Копировать", "Copy"), Modifier.weight(1f), Icons.Rounded.ContentCopy) { copy(ctx, link) }
            SoftButton(tr("Поделиться", "Share"), Modifier.weight(1f), Icons.Rounded.Share) { share(ctx, link) }
        }
    }
    PrimaryButton(tr("Новая заявка", "New order"), icon = Icons.Rounded.Add) { onNew() }
}

/** Карточка услуги для показа клиенту. */
@Composable
private fun ServiceCard(s: Service, currency: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(pal.surface).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppLogo(40)
            Text(s.name, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = pal.text, textAlign = TextAlign.Center)
            if (s.description.isNotBlank()) Text(s.description, color = pal.muted, textAlign = TextAlign.Center)
            Text(money(s.amount.toDoubleOrNull() ?: 0.0, currency), fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, color = Accent)
            PrimaryButton(tr("Закрыть", "Close")) { onClose() }
        }
    }
}

@Composable
private fun ServiceEditor(initial: Service?, onClose: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (Service) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var desc by remember { mutableStateOf(initial?.description ?: "") }
    var sum by remember { mutableStateOf(initial?.amount ?: "") }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(pal.bg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Title(if (initial == null) tr("Новая услуга", "New service") else tr("Услуга", "Service"))
            Field(name, { name = it.take(80) }, tr("Название", "Name"))
            Field(desc, { desc = it.take(400) }, tr("Описание (что сделано)", "Description (what was done)"), singleLine = false)
            Field(sum, { sum = it }, tr("Сумма", "Amount"), keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            PrimaryButton(tr("Сохранить", "Save"), enabled = name.isNotBlank() && parseAmount(sum) != null) {
                onSave(Service(initial?.id ?: UUID.randomUUID().toString(), name.trim(), desc.trim(), parseAmount(sum)!!.toString().removeSuffix(".0")))
            }
            if (onDelete != null) SoftButton(tr("Удалить", "Delete"), Modifier.fillMaxWidth(), Icons.Rounded.Delete, Bad) { onDelete() }
        }
    }
}
