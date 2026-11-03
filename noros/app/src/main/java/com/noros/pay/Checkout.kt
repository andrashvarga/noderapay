package com.noros.pay

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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
 * Раздел «QR»: QR и ссылка на оплату — одно целое. Сверху активные заявки с таймерами, ниже форма новой заявки;
 * заявка открывается экраном с суммой, таймером, выбранным методом, QR с логотипом и кнопкой «Поделиться».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen() {
    val scope = rememberCoroutineScope()
    val balance = rememberLoader(Store.apiKey) { Api.balance() }
    val active = rememberLoader(Store.apiKey) { Api.transactions("active", 1).getJSONArray("rows").objects() }
    val currency = balance.data?.optString("currency").orEmpty()
    AutoRefresh(5000) { active.reload(true) }

    var source by rememberSaveable { mutableStateOf("amount") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var service by remember { mutableStateOf<Service?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var openRow by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var showCard by remember { mutableStateOf<Service?>(null) }
    var editor by remember { mutableStateOf<Service?>(null) }
    var newService by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    // Заявки, которые только что ушли из активных: показываем итог (успех или отмена) и через несколько секунд убираем.
    val finished = remember { mutableStateMapOf<Int, Pair<org.json.JSONObject, Long>>() }
    var prevIds by remember { mutableStateOf<Set<Int>?>(null) }
    LaunchedEffect(active.data) {
        val cur = active.data?.map { it.optInt("id") }?.toSet() ?: return@LaunchedEffect
        val prev = prevIds
        prevIds = cur
        if (prev != null) (prev - cur).forEach { id ->
            runCatching { Api.live(id) }.onSuccess { finished[id] = it to (System.currentTimeMillis() + 9000) }
        }
    }
    val now = rememberNow()
    LaunchedEffect(now) { finished.keys.toList().forEach { k -> if ((finished[k]?.second ?: 0L) < now) finished.remove(k) } }
    val totals = remember { mutableStateMapOf<Int, Long>() }

    val open = openRow
    if (open != null) {
        OrderPanel(open, currency, onBack = { openRow = null; active.reload(true) })
        return
    }

    val amount = parseAmount(amountText)
    val canCreate = amount != null

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; active.reload(); balance.reload(); kotlinx.coroutines.delay(700); refreshing = false } },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Title(tr("Приём оплаты", "Accept payment"))
            Muted(tr("Создайте заявку: клиент сканирует QR или открывает ссылку и попадает на платёжную страницу.", "Create an order: the customer scans the QR or opens the link and lands on the payment page."))

            val rows = active.data ?: emptyList()
            if (rows.isNotEmpty() || finished.isNotEmpty()) {
                Text(tr("Текущие заявки", "Current orders"), fontWeight = FontWeight.Bold, color = pal.text)
                finished.entries.sortedByDescending { it.value.second }.forEach { (id, v) -> FinishedRow(v.first, currency) { openRow = v.first } }
                rows.forEach { r -> ActiveRow(r, currency, now, totals) { openRow = r } }
            }

            Text(tr("Новая заявка", "New order"), fontWeight = FontWeight.Bold, color = pal.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(tr("Своя сумма", "Custom amount"), source == "amount") { source = "amount"; service = null }
                Chip(tr("Услуги и товары", "Services"), source == "services") { source = "services" }
            }

            if (source == "services") {
                if (Store.services.isEmpty()) Card { Muted(tr("Добавьте услуги с готовыми суммами, например «Ремонт ПК — 1000».", "Add services with preset prices, e.g. “PC repair — 1000”.")) }
                Store.services.forEach { s ->
                    val on = service?.id == s.id
                    Card(onClick = { service = s; amountText = s.amount }, tint = if (on) Accent else null) {
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
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge(Icons.Rounded.DoneAll)
                        Column(Modifier.weight(1f)) {
                            Text(tr("Метод выберет клиент", "The customer picks the method"), fontWeight = FontWeight.Bold, color = pal.text)
                            Muted(tr("Платёжная страница Noros покажет все доступные методы.", "The Noros payment page shows all available methods."))
                        }
                    }
                }
                ErrorNote(error)
                PrimaryButton(tr("Создать QR и ссылку", "Create QR and link"), icon = Icons.Rounded.QrCode2, enabled = canCreate, loading = busy) {
                    busy = true; error = null
                    scope.launch {
                        try {
                            val r = Api.createLink(amount!!, "app-" + UUID.randomUUID().toString().take(12))
                            val row = org.json.JSONObject().put("id", r.getInt("id")).put("link", r.getString("link")).put("amount", amount).put("currency", currency).put("status", "created")
                            openRow = row; amountText = ""; service = null; active.reload(true)
                        } catch (e: ApiException) { error = e.message }
                        busy = false
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
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

/** Активная заявка в списке: кольцо таймера, сумма, метод, статус; вся карточка окрашена в цвет статуса. */
@Composable
private fun ActiveRow(r: org.json.JSONObject, currency: String, now: Long, totals: MutableMap<Int, Long>, onClick: () -> Unit) {
    val id = r.optInt("id")
    val (label, color) = statusLabel(r.optString("status"))
    val remaining = (parseIsoMs(r.optString("expiresAt")) ?: now) - now
    if (remaining > (totals[id] ?: 0L)) totals[id] = remaining
    Card(onClick = onClick, tint = color) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimerRing(remaining.coerceAtLeast(0), totals[id] ?: remaining, 58.dp, 4.5.dp, 12)
            Column(Modifier.weight(1f)) {
                Text("#$id  ·  " + money(r.optDouble("amount"), r.optString("currency", currency)), fontWeight = FontWeight.Bold, color = pal.text)
                Muted(r.optStr("method") ?: tr("Клиент выберет метод", "Customer picks the method"))
            }
            StatusPill(label, color)
        }
    }
}

/** Заявка только что закрылась: большая карточка «Оплачено» или «Отменена», затем исчезает. */
@Composable
private fun FinishedRow(r: org.json.JSONObject, currency: String, onClick: () -> Unit) {
    val ok = r.optString("status") == "completed"
    val color = if (ok) Ok else Bad
    Card(onClick = onClick, tint = color) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ResultBadge(ok, 54.dp)
            Column(Modifier.weight(1f)) {
                Text(if (ok) tr("Оплата прошла успешно", "Payment successful") else tr("Заявка отменена", "Order canceled"), fontWeight = FontWeight.ExtraBold, color = color)
                Text("#${r.optInt("id")}  ·  " + money(r.optDouble("amount"), r.optString("currency", currency)), color = pal.text, fontSize = 14.sp)
            }
        }
    }
}

/** Экран заявки: сумма, метод, таймер, ход статусов, QR с логотипом и кнопка «Поделиться ссылкой». */
@Composable
private fun OrderPanel(initial: org.json.JSONObject, currency: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val id = initial.optInt("id")
    val link = initial.getString("link")
    var live by remember(id) { mutableStateOf(initial) }
    LaunchedEffect(id) {
        while (true) {
            runCatching { live = Api.live(id) }
            if (live.optString("status") in FINAL_STATUSES) break
            delay(3000)
        }
    }
    val status = live.optString("status", "created")
    val final = status in FINAL_STATUSES
    val now = rememberNow()
    val remaining = ((parseIsoMs(live.optString("expiresAt")) ?: now) - now).coerceAtLeast(0)
    var total by remember(id) { mutableStateOf(0L) }
    if (remaining > total) total = remaining
    val logo = live.optStr("methodLogo") ?: initial.optStr("_logo")
    val bg = if (live.optStr("methodLogo") != null) live.optStr("methodBg") else initial.optStr("_bg")
    val cover = if (live.optStr("methodLogo") != null) live.optBoolean("methodCover") else initial.optBoolean("_cover")
    val methodName = live.optStr("method") ?: initial.optStr("_name")
    val amount = if (live.has("amount")) live.optDouble("amount") else initial.optDouble("amount")
    val (label, color) = statusLabel(status)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(pal.surface2).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ArrowBack, null, tint = pal.text)
            }
            Text(tr("Заявка", "Order") + " #$id", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = pal.text, modifier = Modifier.weight(1f))
            StatusPill(label, color)
        }
        Card(tint = if (final) color else null) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Muted(tr("Сумма к оплате", "Amount due"))
                Text(money(amount, live.optString("currency", currency)), fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, color = pal.text)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (logo != null) MethodLogo(fixUrl(logo), Modifier.size(40.dp), radius = 12, pad = 4, bg = bg, cover = cover)
                    else IconBadge(Icons.Rounded.CreditCard, size = 40)
                    Column {
                        Muted(tr("Метод оплаты", "Payment method"))
                        Text(methodName ?: tr("Клиент выберет на странице", "Customer picks on the page"), fontWeight = FontWeight.SemiBold, color = pal.text)
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (final) {
                    ResultBadge(status == "completed", 96.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (status == "completed") tr("Оплата прошла успешно", "Payment successful") else tr("Заявка отменена", "Order canceled"),
                        fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = if (status == "completed") Ok else Bad, textAlign = TextAlign.Center,
                    )
                } else {
                    TimerRing(remaining, total, 112.dp, 8.dp, 22)
                    Spacer(Modifier.height(4.dp))
                    Muted(tr("Осталось на оплату", "Time left to pay"))
                }
                Spacer(Modifier.height(18.dp))
                StatusSteps(status)
            }
        }
        if (!final) {
            Card {
                QrWithLogo(link, fixUrl(logo), bg, cover, Modifier.fillMaxWidth(0.82f).align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(10.dp))
                Text(link, color = Accent, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { openUrl(ctx, link) })
            }
            PrimaryButton(tr("Поделиться ссылкой на оплату", "Share payment link"), icon = Icons.Rounded.Share) { share(ctx, link) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SoftButton(tr("Копировать", "Copy"), Modifier.weight(1f), Icons.Rounded.ContentCopy) { copy(ctx, link) }
                SoftButton(tr("Открыть страницу", "Open page"), Modifier.weight(1f), Icons.Rounded.OpenInNew) { openUrl(ctx, link) }
            }
        } else PrimaryButton(tr("Новая заявка", "New order"), icon = Icons.Rounded.Add) { onBack() }
    }
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
