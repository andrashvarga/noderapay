package com.nodera.pay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { NoderaTheme { Root() } }
    }
}

@Composable
private fun Root() {
    var splash by rememberSaveable { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().background(pal.bg)) {
        when {
            splash -> SplashScreen { splash = false }
            !Store.connected -> Box(Modifier.fillMaxSize().systemBarsPadding().imePadding()) { ConnectScreen { } }
            else -> MainShell()
        }
    }
}

private enum class Tab(val ru: String, val en: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Orders("Заявки", "Orders", Icons.Rounded.Receipt),
    Withdrawals("Выводы", "Withdraw", Icons.Rounded.AccountBalanceWallet),
    Qr("QR", "QR", Icons.Rounded.QrCode2),
    Link("Ссылка", "Link", Icons.Rounded.Link),
    Settings("Настройки", "Settings", Icons.Rounded.Settings),
}

@Composable
private fun MainShell() {
    var tab by rememberSaveable { mutableStateOf(Tab.Orders) }
    var help by remember { mutableStateOf(!Store.onboardingSeen) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val askNotify = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { syncWatcher(ctx) }
    LaunchedEffect(Unit) {
        ensureChannels(ctx)
        if (android.os.Build.VERSION.SDK_INT >= 33 && !canNotify(ctx) && Store.notify) askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS) else syncWatcher(ctx)
    }
    Scaffold(
        containerColor = pal.bg,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppLogo(34)
                if (Store.logoMode != "none") Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    BrandTitle(13)
                    if (Store.isTest) Text(tr("ТЕСТОВЫЙ РЕЖИМ", "TEST MODE"), color = Warn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                val dark = pal.dark
                RoundIcon(Icons.Rounded.HelpOutline) { help = true }
                Spacer(Modifier.width(8.dp))
                RoundIcon(if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode) { Store.applyTheme(if (dark) "light" else "dark") }
            }
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp).neu(28.dp, depth = 6.dp).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Tab.entries.forEach { t ->
                    val sel = tab == t
                    Column(
                        Modifier.weight(1f).neu(22.dp, inset = sel, depth = 3.dp).clip(RoundedCornerShape(22.dp))
                            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { tab = t }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(t.icon, null, tint = if (sel) Accent else pal.muted, modifier = Modifier.size(24.dp))
                        Text(tr(t.ru, t.en), fontSize = 10.5.sp, maxLines = 1, color = if (sel) Accent else pal.muted, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().imePadding()) {
            when (tab) {
                Tab.Orders -> OrdersScreen()
                Tab.Withdrawals -> WithdrawalsScreen()
                Tab.Qr -> CheckoutScreen("qr")
                Tab.Link -> CheckoutScreen("link")
                Tab.Settings -> SettingsScreen { }
            }
        }
    }
    if (help) OnboardingDialog { help = false; Store.setOnboardingSeen() }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).neuPress(21.dp, depth = 4.dp, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp))
    }
}
