package com.nodera.pay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch

private const val TRC = "TRX (TRC-20)"
private const val BEP = "BEP-20 (BSC)"

/** «Выводы»: вывод баланса в USDT на сохранённый кошелёк. Закрывает вывод сотрудник Nodera вручную. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithdrawalsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    val balance = rememberLoader(tick) { Api.balance() }
    val rules = rememberLoader(Store.apiKey) { Api.withdrawalRules() }
    val wallets = rememberLoader(tick) { Api.wallets().objects() }
    val history = rememberLoader(tick) { Api.withdrawals().objects() }

    var walletId by remember { mutableStateOf<String?>(null) }
    var amountText by remember { mutableStateOf("") }
    var addWallet by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val avail = balance.data?.optDouble("balance") ?: 0.0
    val currency = balance.data?.optString("currency").orEmpty()
    val amount = parseAmount(amountText)

    AutoRefresh(8000) { balance.reload(true); wallets.reload(true); history.reload(true) }
    var refreshing by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; balance.reload(); wallets.reload(); history.reload(); kotlinx.coroutines.delay(700); refreshing = false } },
        modifier = Modifier.fillMaxSize(),
    ) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Title(tr("Выводы", "Withdrawals"))
        ErrorNote(balance.error ?: wallets.error ?: history.error)
        if (Store.testMerchant) Card { Muted(tr("Тестовый мерчант: выводы тестовые и не двигают баланс.", "Test merchant: withdrawals are test-only and do not move the balance.")) }
        Card {
            Muted(tr("Доступно к выводу", "Available"))
            Text(money(avail, currency), fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = pal.text)
            val fz = balance.data?.optDouble("frozenInWithdrawals") ?: 0.0
            if (fz > 0) Muted(tr("В выводах: ", "Held in withdrawals: ") + money(fz, currency))
            rules.data?.let { r ->
                val min = r.optDouble("minUsdt")
                if (!min.isNaN() && min > 0) Muted(tr("Вывод в USDT. Мин. сумма: ", "USDT withdrawal. Minimum: ") + money(min, "USDT") + tr(" · комиссия: ", " · fee: ") + money(r.optDouble("feeUsdt", 0.0), "USDT"))
                else Muted(tr("Вывод в USDT", "USDT withdrawal"))
            }
        }

        Text(tr("Кошелёк", "Wallet"), fontWeight = FontWeight.Bold, color = pal.text)
        val ws = wallets.data
        if (ws != null && ws.isEmpty()) Card { Muted(tr("Сохранённых кошельков нет. Добавьте адрес USDT (TRC-20 или BEP-20).", "No saved wallets. Add a USDT address (TRC-20 or BEP-20).")) }
        ws?.forEach { w ->
            val on = walletId == w.getString("id")
            Card(onClick = { walletId = w.getString("id") }, tint = if (on) Accent else null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(Icons.Rounded.AccountBalanceWallet)
                    Column(Modifier.weight(1f)) {
                        Text(w.optString("label").ifBlank { w.getString("network") }, fontWeight = FontWeight.Bold, color = pal.text)
                        Muted(w.getString("network") + " · " + w.getString("address").let { it.take(6) + "…" + it.takeLast(6) })
                    }
                    if (on) Icon(Icons.Rounded.CheckCircle, null, tint = Accent)
                }
                if (on) Row(Modifier.padding(top = 8.dp)) {
                    SoftButton(tr("Удалить кошелёк", "Delete wallet"), icon = Icons.Rounded.Delete, color = Bad) {
                        scope.launch { try { Api.deleteWallet(w.getString("id")); walletId = null; tick++ } catch (e: ApiException) { error = e.message } }
                    }
                }
            }
        }
        SoftButton(tr("Добавить кошелёк", "Add wallet"), Modifier.fillMaxWidth(), Icons.Rounded.Add) { addWallet = true }

        Field(amountText, { amountText = it }, tr("Сумма", "Amount") + if (currency.isNotBlank()) ", $currency" else "", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailing = { TextButton({ amountText = money(avail).replace(" ", "") }) { Text(tr("Вся сумма", "All"), color = Accent) } })
        ErrorNote(error)
        PrimaryButton(tr("Вывести USDT", "Withdraw USDT"), icon = Icons.Rounded.ArrowUpward, enabled = amount != null && walletId != null, loading = busy) {
            busy = true; error = null
            scope.launch {
                try { val r = Api.withdraw(amount!!, walletId!!); toast(ctx, tr("Заявка на вывод #", "Withdrawal request #") + r.optInt("id")); amountText = ""; tick++ }
                catch (e: ApiException) { error = e.message }
                busy = false
            }
        }

        Text(tr("История выводов", "History"), fontWeight = FontWeight.Bold, color = pal.text)
        val h = history.data
        if (h != null && h.isEmpty()) Card { Muted(tr("Выводов пока не было.", "No withdrawals yet.")) }
        h?.forEach { w ->
            val (label, color) = withdrawalStatus(w.optString("status"))
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("#${w.optInt("num")}  ·  " + money(w.optDouble("amountUsdt", 0.0), "USDT"), fontWeight = FontWeight.Bold, color = pal.text)
                        Muted(shortDate(w.optString("createdAt")) + " · " + money(w.optDouble("amount"), w.optString("currency")))
                    }
                    StatusPill(label, color)
                }
                w.optString("txHash").takeIf { it.isNotBlank() && it != "null" }?.let { Muted("TX: " + it.take(10) + "…" + it.takeLast(6)) }
                if (w.optString("status") in listOf("created", "pending", "new")) Row(Modifier.padding(top = 8.dp)) {
                    SoftButton(tr("Отменить", "Cancel"), icon = Icons.Rounded.Close, color = Bad) {
                        scope.launch { try { Api.cancelWithdrawal(w.getString("id")); tick++ } catch (e: ApiException) { error = e.message } }
                    }
                }
            }
        }
    }
    }

    if (addWallet) AddWalletDialog({ addWallet = false }) { id -> addWallet = false; walletId = id; tick++ }
}

@Composable
private fun AddWalletDialog(onClose: () -> Unit, onAdded: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var network by remember { mutableStateOf(TRC) }
    var address by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(pal.bg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Title(tr("Новый кошелёк USDT", "New USDT wallet"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("TRC-20", network == TRC) { network = TRC }
                Chip("BEP-20", network == BEP) { network = BEP }
            }
            Field(address, { address = it.trim() }, tr("Адрес кошелька", "Wallet address"))
            Field(label, { label = it.take(40) }, tr("Название (необязательно)", "Label (optional)"))
            Muted(tr("Новый кошелёк может быть недоступен для вывода некоторое время — это защита от угнанного доступа.", "A new wallet may be locked for a short time — this protects against account takeover."))
            ErrorNote(error)
            PrimaryButton(tr("Сохранить", "Save"), enabled = address.length > 20, loading = busy) {
                busy = true; error = null
                scope.launch {
                    try { onAdded(Api.addWallet(network, address, label).getString("id")) } catch (e: ApiException) { error = e.message }
                    busy = false
                }
            }
        }
    }
}
