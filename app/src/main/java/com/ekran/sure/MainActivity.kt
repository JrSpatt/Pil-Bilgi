package com.ekran.sure

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF080D0A)
private val Card = Color(0xFF111915)
private val Track = Color(0xFF1B2620)
private val Green = Color(0xFF3DDC84)
private val GreenDark = Color(0xFF1B8F53)
private val Gold = Color(0xFFE8B84A)
private val Muted = Color(0xFF86948B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

// ---------- Veri ----------

data class Stats(
    val onBattery: Long,
    val screenOff: Long,
    val doze: Long,
    val screenOn: Long,
    val screenOnCount: Int,
    val screenOnMah: Int,
    val pctOn: Int,
    val pctOff: Int,
    val estimated: Long
)

sealed interface UiState {
    data object Loading : UiState
    data class Error(val msg: String) : UiState
    data class Ok(val s: Stats) : UiState
}

private fun dur(s: String): Long {
    var t = 0L
    Regex("(\\d+)(ms|d|h|m|s)").findAll(s).forEach {
        val v = it.groupValues[1].toLong()
        t += when (it.groupValues[2]) {
            "d" -> v * 86_400_000
            "h" -> v * 3_600_000
            "m" -> v * 60_000
            "s" -> v * 1_000
            else -> v
        }
    }
    return t
}

private fun durAfterColon(line: String) = dur(line.substringAfter(":").substringBefore("("))
private fun intAfterColon(line: String) =
    Regex("\\d+").find(line.substringAfter(":"))?.value?.toInt() ?: 0

fun readStats(): UiState {
    return try {
        val p = ProcessBuilder("su", "-c", "dumpsys batterystats --charged")
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        if (out.isBlank()) return UiState.Error("Çıktı boş. Root izni verildi mi?")

        var onBat = 0L; var off = 0L; var doze = 0L; var on = 0L; var cnt = 0
        var mah = 0; var pOn = -1; var pOff = -1; var est = 0L

        for (raw in out.lineSequence()) {
            val l = raw.trim()
            when {
                l.startsWith("Time on battery:") && onBat == 0L -> onBat = durAfterColon(l)
                l.startsWith("Time on battery screen off:") && off == 0L -> off = durAfterColon(l)
                l.startsWith("Time on battery screen doze:") && doze == 0L -> doze = durAfterColon(l)
                l.startsWith("Screen on:") && on == 0L -> {
                    on = durAfterColon(l)
                    cnt = Regex("\\)\\s*(\\d+)x").find(l)?.groupValues?.get(1)?.toInt() ?: 0
                }
                l.startsWith("Screen on discharge:") && mah == 0 -> mah = intAfterColon(l)
                l.startsWith("Amount discharged while screen on:") && pOn < 0 -> pOn = intAfterColon(l)
                l.startsWith("Amount discharged while screen off:") && pOff < 0 -> pOff = intAfterColon(l)
                l.startsWith("Estimated screen on time:") && est == 0L -> est = durAfterColon(l)
            }
        }
        if (on == 0L && onBat == 0L) UiState.Error("Veri okunamadı. Root izni verildiğinden emin olun.")
        else UiState.Ok(Stats(onBat, off, doze, on, cnt, mah, maxOf(pOn, 0), maxOf(pOff, 0), est))
    } catch (e: Exception) {
        UiState.Error("Root erişimi alınamadı: ${e.message}")
    }
}

private fun batteryLevel(ctx: Context): Int {
    val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val lvl = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    return if (lvl < 0) 0 else lvl * 100 / scale
}

private fun fmt(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms % 3_600_000) / 60_000
    return if (h > 0) "$h sa $m dk" else "$m dk"
}

// ---------- Arayüz ----------

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<UiState>(UiState.Loading) }
    var level by remember { mutableIntStateOf(batteryLevel(ctx)) }

    fun load() {
        scope.launch {
            state = UiState.Loading
            level = batteryLevel(ctx)
            state = withContext(Dispatchers.IO) { readStats() }
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Header(level) { load() }
        Spacer(Modifier.height(28.dp))

        when (val s = state) {
            UiState.Loading -> Box(Modifier.height(300.dp), contentAlignment = Alignment.Center) {
                Text("Okunuyor…", color = Muted, fontSize = 15.sp)
            }
            is UiState.Error -> Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Card).padding(24.dp)
            ) { Text(s.msg, color = Gold, fontSize = 15.sp, textAlign = TextAlign.Center) }
            is UiState.Ok -> Content(s.s)
        }
    }
}

@Composable
fun Header(level: Int, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Ekran Süresi", color = Gold, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text("Son şarjdan beri", color = Muted, fontSize = 13.sp)
        }
        BatteryIcon(level)
        Spacer(Modifier.width(6.dp))
        Text("%$level", color = Green, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Card).clickable { onRefresh() },
            contentAlignment = Alignment.Center
        ) { Text("↻", color = Green, fontSize = 20.sp) }
    }
}

@Composable
fun BatteryIcon(level: Int) {
    Canvas(Modifier.size(width = 34.dp, height = 16.dp)) {
        val nub = 3.dp.toPx()
        val bodyW = size.width - nub
        val stroke = 1.6.dp.toPx()
        drawRoundRect(
            Green.copy(alpha = 0.85f), Offset(stroke / 2, stroke / 2),
            Size(bodyW - stroke, size.height - stroke), CornerRadius(5.dp.toPx()), style = Stroke(stroke)
        )
        val pad = 3.5.dp.toPx()
        drawRoundRect(
            Green, Offset(pad, pad),
            Size(((bodyW - 2 * pad) * level / 100f).coerceAtLeast(0f), size.height - 2 * pad),
            CornerRadius(2.5.dp.toPx())
        )
        drawRoundRect(
            Green.copy(alpha = 0.85f), Offset(bodyW + 0.5.dp.toPx(), size.height * 0.3f),
            Size(2.dp.toPx(), size.height * 0.4f), CornerRadius(1.dp.toPx())
        )
    }
}

@Composable
fun Content(s: Stats) {
    val frac = if (s.onBattery > 0) s.screenOn.toFloat() / s.onBattery else 0f
    Ring(frac, fmt(s.screenOn), "ekran açık")
    Spacer(Modifier.height(10.dp))
    Text(
        "${s.screenOnCount} kez açıldı  ·  pilde geçen sürenin %${(frac * 100).toInt()}'i",
        color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(26.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("Pilde geçen süre", fmt(s.onBattery), Green, Modifier.weight(1f))
        StatCard("Ekran kapalı", fmt(s.screenOff), Gold, Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("Doze / AOD", fmt(s.doze), Green, Modifier.weight(1f))
        StatCard("Ekran açıkken harcanan", "${s.screenOnMah} mAh", Gold, Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("Düşen pil (ekran açık)", "%${s.pctOn}", Green, Modifier.weight(1f))
        StatCard("Düşen pil (ekran kapalı)", "%${s.pctOff}", Gold, Modifier.weight(1f))
    }
    if (s.estimated > 0) {
        Spacer(Modifier.height(12.dp))
        StatCard("Tahmini toplam ekran süresi (projeksiyon)", fmt(s.estimated), Muted, Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
fun StatCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        Text(value, color = if (accent == Green) Color(0xFFEAF5EE) else accent,
            fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = Muted, fontSize = 12.sp, lineHeight = 15.sp)
    }
}

@Composable
fun Ring(fraction: Float, center: String, sub: String) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(1000), label = "ring")
    Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val sw = 20.dp.toPx()
            val tl = Offset(sw / 2, sw / 2)
            val sz = Size(size.width - sw, size.height - sw)
            drawArc(Track, 0f, 360f, false, tl, sz, style = Stroke(sw))
            rotate(-90f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to GreenDark, 0.55f to Green, 1f to Gold
                    ),
                    startAngle = 0f, sweepAngle = 360f * anim, useCenter = false,
                    topLeft = tl, size = sz, style = Stroke(sw, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(center, color = Gold, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Muted, fontSize = 14.sp)
        }
    }
}
