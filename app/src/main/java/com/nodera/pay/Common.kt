package com.nodera.pay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.foundation.border
import coil.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

/** Значок Telegram (бумажный самолетик) в том же скруглённом стиле, что и остальные иконки. */
val TelegramIcon: ImageVector by lazy {
    ImageVector.Builder("Telegram", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(20.6f, 4.1f)
            lineTo(3.4f, 10.7f)
            curveTo(2.6f, 11.0f, 2.6f, 11.6f, 3.3f, 11.8f)
            lineTo(7.6f, 13.2f)
            lineTo(9.3f, 18.4f)
            curveTo(9.5f, 19.0f, 9.8f, 19.1f, 10.3f, 18.7f)
            lineTo(12.4f, 16.7f)
            lineTo(16.7f, 19.9f)
            curveTo(17.5f, 20.3f, 18.1f, 20.1f, 18.3f, 19.2f)
            lineTo(21.1f, 5.6f)
            curveTo(21.4f, 4.5f, 20.7f, 4.0f, 20.6f, 4.1f)
            close()
            moveTo(17.2f, 7.9f)
            lineTo(10.0f, 14.4f)
            lineTo(9.7f, 17.0f)
            lineTo(8.7f, 13.0f)
            lineTo(17.0f, 7.8f)
            curveTo(17.3f, 7.6f, 17.4f, 7.8f, 17.2f, 7.9f)
            close()
        }
    }.build()
}

/** Логотип в левом углу: наш, без логотипа или загруженный. */
@Composable
fun AppLogo(size: Int = 32, modifier: Modifier = Modifier) {
    val dark = pal.dark
    when (Store.logoMode) {
        "none" -> Spacer(modifier.size(0.dp))
        "custom" -> {
            val bmp = remember(Store.logoVersion) { runCatching { BitmapFactory.decodeFile(Store.logoFile.path)?.asImageBitmap() }.getOrNull() }
            if (bmp != null) Image(bmp, null, modifier.size(size.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit)
            else Image(painterResource(R.drawable.logo_mark), null, modifier.size(size.dp))
        }
        else -> Image(painterResource(if (dark) R.drawable.logo_mark else R.drawable.logo_mark), null, modifier.size(size.dp))
    }
}

fun qrBitmap(text: String, size: Int = 720): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
    val px = IntArray(size * size) { if (m[it % size, it / size]) 0xFF10121C.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

@Composable
fun QrView(link: String, modifier: Modifier = Modifier) {
    val img = remember(link) { qrBitmap(link).asImageBitmap() }
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp)) {
        Image(img, tr("QR-код для оплаты", "Payment QR code"), Modifier.fillMaxWidth().aspectRatio(1f))
    }
}

fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Nodera Pay", text))
    Toast.makeText(ctx, tr("Скопировано", "Copied"), Toast.LENGTH_SHORT).show()
}

fun share(ctx: Context, text: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(i, tr("Отправить ссылку", "Share link")))
}

fun openUrl(ctx: Context, url: String) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

/** Сумма: до двух знаков после запятой, пробел между разрядами. */
fun money(v: Double, currency: String = ""): String {
    val s = String.format(Locale.US, "%,.2f", v).replace(',', ' ').removeSuffix(".00")
    return if (currency.isBlank()) s else "$s $currency"
}

fun parseAmount(s: String): Double? = s.trim().replace(',', '.').replace(" ", "").toDoubleOrNull()?.takeIf { it > 0 }

fun shortDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val d = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(iso.take(19))!!
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(d)
    }.getOrDefault(iso.take(16).replace('T', ' '))
}

fun maskKey(k: String): String = if (k.length <= 12) "••••••••" else k.take(8) + "••••••••••••" + k.takeLast(4)

fun statusLabel(s: String): Pair<String, Color> = when (s) {
    "completed" -> tr("Оплачена", "Paid") to Ok
    "canceled" -> tr("Отменена", "Canceled") to Bad
    "failed" -> tr("Ошибка", "Failed") to Bad
    "expired" -> tr("Истекла", "Expired") to Bad
    "created" -> tr("Ожидает оплаты", "Awaiting payment") to Warn
    "proof" -> tr("Проверка перевода", "Checking transfer") to Accent
    "offer", "accepted" -> tr("В обработке", "Processing") to Accent
    else -> s to Accent
}

fun withdrawalStatus(s: String): Pair<String, Color> = when (s) {
    "completed", "done", "success" -> tr("Выполнен", "Done") to Ok
    "canceled", "rejected" -> tr("Отменён", "Canceled") to Bad
    else -> tr("В обработке", "Pending") to Warn
}

fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

/** Простая загрузка данных: состояние, ошибка и перезагрузка. */
class Loader<T>(private val scope: CoroutineScope, private val fetch: suspend () -> T) {
    var data by mutableStateOf<T?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    fun reload() {
        scope.launch {
            loading = true; error = null
            try { data = fetch() } catch (e: ApiException) { error = e.message } catch (e: Exception) { error = e.message }
            loading = false
        }
    }
}

@Composable
fun <T> rememberLoader(key: Any? = null, fetch: suspend () -> T): Loader<T> {
    val scope = rememberCoroutineScope()
    val l = remember(key) { Loader(scope, fetch) }
    LaunchedEffect(key) { l.reload() }
    return l
}

/** Логотипы приходят с адресом, который знает сервер (может быть внутренним): берем путь и подставляем адрес сервера из настроек. */
fun fixUrl(u: String?): String? {
    if (u.isNullOrBlank() || u == "null") return null
    val i = u.indexOf("/assets/")
    return if (i >= 0 && Store.baseUrl.isNotBlank()) Store.baseUrl.trimEnd('/') + u.substring(i) else u
}

/** Название «NODERA» и «PAY» одним шрифтом бренда (Lexend Exa), как в логотипе. */
@Composable
fun BrandTitle(height: Int = 14, modifier: Modifier = Modifier) {
    val pay = height * 0.903f
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(if (pal.dark) R.drawable.wordmark_light else R.drawable.wordmark_dark), "Nodera", Modifier.height(height.dp).width((height * 8.26f).dp), contentScale = ContentScale.Fit)
        Spacer(Modifier.width((height * 0.9f).dp))
        Image(painterResource(if (pal.dark) R.drawable.pay_light else R.drawable.pay_dark), "Pay", Modifier.height(pay.dp).width((pay * 4.27f).dp), contentScale = ContentScale.Fit)
    }
}

/** Светлый ли логотип (белый на прозрачном): тогда под него нужна темная подложка, иначе его не видно. */
private fun isLightLogo(d: android.graphics.drawable.Drawable): Boolean = runCatching {
    val w = 32; val h = 32
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bmp); d.setBounds(0, 0, w, h); d.draw(cv)
    var n = 0; var sum = 0.0; var opaque = 0
    for (y in 0 until h) for (x in 0 until w) {
        val p = bmp.getPixel(x, y); val a = p ushr 24
        if (a > 200) opaque++
        if (a > 60) { n++; sum += (0.299 * ((p shr 16) and 255) + 0.587 * ((p shr 8) and 255) + 0.114 * (p and 255)) / 255.0 }
    }
    // Логотип со сплошным фоном (почти весь кадр непрозрачен) подложки не требует.
    n > 0 && opaque < w * h * 0.85 && sum / n > 0.72
}.getOrDefault(false)

/** Логотип метода на подложке, подобранной по цвету логотипа: светлый логотип — на тёмной, тёмный — на белой. */
@Composable
fun MethodLogo(url: String?, modifier: Modifier = Modifier, radius: Int = 12, pad: Int = 6) {
    val ctx = LocalContext.current
    var dark by remember(url) { mutableStateOf(false) }
    Box(
        modifier.clip(RoundedCornerShape(radius.dp)).background(if (dark) Color(0xFF2A315C) else Color.White).border(1.dp, if (dark) Color(0xFF3C4580) else Color(0xFFE3E5F0), RoundedCornerShape(radius.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            val req = remember(url) { coil.request.ImageRequest.Builder(ctx).data(url).allowHardware(false).listener(onSuccess = { _, r -> dark = isLightLogo(r.drawable) }).build() }
            AsyncImage(model = req, contentDescription = null, modifier = Modifier.fillMaxSize().padding(pad.dp), contentScale = ContentScale.Fit)
        } else Icon(Icons.Rounded.CreditCard, null, tint = Accent, modifier = Modifier.size(26.dp))
    }
}

/**
 * Выбор методов оплаты из доступных в кабинете: крупные карточки с логотипами по два в ряд.
 * Пустой выбор = «все методы, клиент выберет сам».
 */
@Composable
fun MethodPicker(methods: List<JSONObject>, selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val all = selected.isEmpty()
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (all) Accent.copy(alpha = .14f) else pal.surface)
                .border(1.5.dp, if (all) Accent else pal.line, RoundedCornerShape(16.dp)).clickable { onChange(emptySet()) }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(Icons.Rounded.DoneAll)
            Column(Modifier.weight(1f)) {
                Text(tr("Все методы", "All methods"), fontWeight = FontWeight.Bold, color = pal.text)
                Muted(tr("Клиент выберет сам на платёжной странице", "The customer picks on the payment page"))
            }
            if (all) Icon(Icons.Rounded.CheckCircle, null, tint = Accent)
        }
        if (!all) Muted(tr("Выбрано: ${selected.size}. Клиент увидит только их.", "Selected: ${selected.size}. The customer sees only these."))
        methods.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { m ->
                    val id = m.getInt("id")
                    val on = id in selected
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (on) Accent.copy(alpha = .14f) else pal.surface)
                            .border(1.5.dp, if (on) Accent else pal.line, RoundedCornerShape(16.dp)).clickable { onChange(if (on) selected - id else selected + id) }
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box {
                            MethodLogo(fixUrl(m.optString("logo")), Modifier.fillMaxWidth().height(68.dp))
                            if (on) Icon(Icons.Rounded.CheckCircle, null, tint = Accent, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp))
                        }
                        Text(m.getString("name"), fontWeight = FontWeight.SemiBold, color = pal.text, fontSize = 13.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, minLines = 2)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
