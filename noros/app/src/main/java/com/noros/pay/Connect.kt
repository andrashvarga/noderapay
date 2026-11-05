package com.noros.pay

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

/** Экран подключения: без api_key и api_secret_key приложение не работает. */
@Composable
fun ConnectScreen(onConnected: () -> Unit) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(8.dp))
        BrandTitle(24)
        Muted(tr("Терминал приёма платежей для мерчантов Noros. Введите ключи из личного кабинета my.noros.org — без них приложение не работает.",
            "Payment terminal for Noros merchants. Enter the keys from your my.noros.org account — the app does not work without them."))
        Field(key, { key = it.trim() }, "api_key", keyboard = KeyboardOptions(keyboardType = KeyboardType.Password))
        Field(secret, { secret = it.trim() }, "api_secret_key", keyboard = KeyboardOptions(keyboardType = KeyboardType.Password))
        Muted(tr("Ключи хранятся на устройстве в зашифрованном виде и после сохранения показываются только частично.", "Keys are stored encrypted on the device and shown only partially after saving."))
        ErrorNote(error)
        PrimaryButton(tr("Подключить", "Connect"), icon = Icons.Rounded.Key, enabled = key.length > 8 && secret.length > 8, loading = busy) {
            busy = true; error = null
            scope.launch {
                try {
                    Api.verify(Store.SERVER, key, secret)
                    Store.saveKeys(Store.SERVER, key, secret); onConnected()
                } catch (e: ApiException) { error = e.message }
                busy = false
            }
        }
    }
}
