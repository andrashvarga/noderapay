package com.nodera.pay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.net.URI

/** Адрес должен быть https; обычный http допустим только для локальной сети (проверка на своём компьютере). */
private fun normalizeUrl(raw: String): String? {
    var s = raw.trim().trimEnd('/')
    if (s.isEmpty()) return null
    if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
    val u = runCatching { URI(s) }.getOrNull() ?: return null
    val host = u.host ?: return null
    val local = host == "localhost" || Regex("^(10\\.|192\\.168\\.|172\\.(1[6-9]|2\\d|3[01])\\.|127\\.)").containsMatchIn(host)
    return if (s.startsWith("http://") && !local) null else s
}

/** Экран подключения: без api_key и api_secret_key приложение не работает. */
@Composable
fun ConnectScreen(onConnected: () -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(Store.baseUrl) }
    var key by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(8.dp))
        BrandTitle(20)
        Muted(tr("Терминал приёма платежей. Введите ключи из личного кабинета мерчанта Nodera (раздел «Ключи API») — без них приложение не работает.",
            "Payment terminal. Enter the keys from your Nodera merchant account (“API keys”) — the app does not work without them."))
        Field(url, { url = it }, tr("Адрес сервера Nodera", "Nodera server address"), keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri))
        Field(key, { key = it.trim() }, "api_key", keyboard = KeyboardOptions(keyboardType = KeyboardType.Password))
        Field(secret, { secret = it.trim() }, "api_secret_key", keyboard = KeyboardOptions(keyboardType = KeyboardType.Password))
        Muted(tr("Ключи хранятся на устройстве в зашифрованном виде и после сохранения показываются только частично.", "Keys are stored encrypted on the device and shown only partially after saving."))
        ErrorNote(error)
        PrimaryButton(tr("Подключить", "Connect"), icon = Icons.Rounded.Key, enabled = url.isNotBlank() && key.length > 8 && secret.length > 8, loading = busy) {
            val base = normalizeUrl(url)
            when {
                base == null -> error = tr("Укажите адрес с https:// (http допустим только в локальной сети)", "Use an https:// address (http only on a local network)")
                !key.startsWith("ak_") -> error = tr("api_key начинается с ak_", "api_key starts with ak_")
                !secret.startsWith("sk_") -> error = tr("api_secret_key начинается с sk_", "api_secret_key starts with sk_")
                key.removePrefix("ak_").substringBefore('_') != secret.removePrefix("sk_").substringBefore('_') -> error = tr("Ключи из разных режимов (test/live)", "Keys are from different modes (test/live)")
                else -> {
                    busy = true; error = null
                    scope.launch {
                        try {
                            val info = Api.verify(base, key, secret)
                            val testMerchant = info.optBoolean("testMerchant")
                            // Тестовые ключи обычного мерчанта не пускаем: нужны боевые. Тестовому мерчанту test_p2p_* тестовые ключи можно.
                            if (key.startsWith("ak_test_") && !testMerchant) error = tr("Это тестовые ключи (test): через них деньги не принимаются. Возьмите боевые ключи (live) в кабинете мерчанта, раздел «Ключи API».", "These are test keys: no real payments can be accepted. Use the live keys from your merchant account (“API keys”).")
                            else { Store.saveKeys(base, key, secret, testMerchant); onConnected() }
                        }
                        catch (e: ApiException) { error = e.message }
                        busy = false
                    }
                }
            }
        }
    }
}
