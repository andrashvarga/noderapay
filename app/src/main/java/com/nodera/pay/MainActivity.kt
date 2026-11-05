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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notices.watchNetwork(this)
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
        NoticeHost()
    }
}

private enum class Tab(val ru: String, val en: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Orders("Заявки", "Orders", Icons.Rounded.Receipt),
    Payouts("Выплаты", "Payouts", Icons.Rounded.NorthEast),
    Withdrawals("Выводы", "Withdraw", Icons.Rounded.AccountBalanceWallet),
    Qr("QR", "QR", Icons.Rounded.QrCode2),
    Settings("Настройки", "Settings", Icons.Rounded.Settings),
}

@Composable
private fun MainShell() {
    var tab by rememberSaveable { mutableStateOf(Tab.Orders) }
    var help by remember { mutableStateOf(!Store.onboardingSeen) }
    var updatePrompt by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { if (Updater.check(ctx)) Notices.showUpdate(Updater.latest()?.name ?: "") { updatePrompt = true } }
    val askNotify = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { syncWatcher(ctx) }
    LaunchedEffect(Unit) {
        ensureChannels(ctx)
        Api.resolveFiat()
        if (android.os.Build.VERSION.SDK_INT >= 33 && !canNotify(ctx) && Store.notify) askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS) else syncWatcher(ctx)
    }
    Scaffold(
        containerColor = pal.bg,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    HeaderBrand(15)
                    if (Store.isTest) Text(tr("ТЕСТОВЫЙ РЕЖИМ", "TEST MODE"), color = Warn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                val dark = pal.dark
                RoundIcon(Icons.Rounded.HelpOutline) { help = true }
                Spacer(Modifier.width(8.dp))
                RoundIcon(if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode) { Store.applyTheme(if (dark) "light" else "dark") }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = pal.surface, tonalElevation = 0.dp,
                modifier = Modifier.shadow(10.dp, ambientColor = Color.Black.copy(alpha = if (pal.dark) .6f else .12f), spotColor = Color.Black.copy(alpha = if (pal.dark) .6f else .18f)),
            ) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t }, alwaysShowLabel = true,
                        icon = {
                            val n = when (t) { Tab.Orders -> Store.activeOrders; Tab.Payouts -> Store.activePayouts; else -> 0 }
                            if (n > 0) BadgedBox(badge = { Badge(containerColor = Accent) { Text("$n", color = Color.White) } }) { Icon(t.icon, null) } else Icon(t.icon, null)
                        }, label = { Text(tr(t.ru, t.en), fontSize = 11.sp, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent, indicatorColor = Accent.copy(alpha = .16f), unselectedIconColor = pal.muted, unselectedTextColor = pal.muted),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().imePadding()) {
            when (tab) {
                Tab.Orders -> OrdersScreen()
                Tab.Payouts -> PayoutsScreen()
                Tab.Withdrawals -> WithdrawalsScreen()
                Tab.Qr -> CheckoutScreen()
                Tab.Settings -> SettingsScreen { }
            }
        }
    }
    if (updatePrompt && !help) UpdatePrompt { updatePrompt = false }
    if (help) OnboardingDialog { help = false; Store.setOnboardingSeen() }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(pal.surface2).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp))
    }
}
