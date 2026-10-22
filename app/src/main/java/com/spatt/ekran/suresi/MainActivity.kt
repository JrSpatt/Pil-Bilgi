package com.spatt.ekran.suresi

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.PullToRefreshDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App(this) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(activity: ComponentActivity) {
    val ctx = LocalContext.current
    val lifecycle = activity.lifecycle
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("ui", Context.MODE_PRIVATE) }

    var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var screenState by remember { mutableStateOf<UiState>(UiState.Loading) }
    var live by remember { mutableStateOf<Live?>(null) }
    var rootBat by remember { mutableStateOf<RootBat?>(null) }
    var level by remember { mutableIntStateOf(batteryLevel(ctx)) }

    suspend fun refreshOnce() {
        level = batteryLevel(ctx)
        if (tab == 0) {
            screenState = withContext(Dispatchers.IO) { readStats() }
        } else {
            live = withContext(Dispatchers.IO) { readLive(ctx) }
            rootBat = withContext(Dispatchers.IO) { readRootBat() }
        }
    }

    // Sekme değişince, yenile düğmesine basılınca ve uygulamaya dönülünce çalışır.
    LaunchedEffect(tab, tick) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (tab == 0) {
                refreshOnce()
            } else {
                launch { rootBat = withContext(Dispatchers.IO) { readRootBat() } }
                while (true) {
                    live = withContext(Dispatchers.IO) { readLive(ctx) }
                    delay(1000)
                }
            }
        }
    }

    // Durum çubuğu ve gezinme çubuğu simgelerini temaya uydur.
    DisposableEffect(dark) {
        val t = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }

    CompositionLocalProvider(LocalPalette provides (if (dark) DarkPalette else LightPalette)) {
        val accent by animateColorAsState(if (tab == 0) Green else RubyLight, tween(250), label = "accent")
        val pullState = rememberPullToRefreshState()

        Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
            Header(
                if (tab == 0) "Ekran Süresi" else "Pil Bilgileri",
                if (tab == 0) "Son şarjdan beri" else "Canlı · her saniye",
                live?.level ?: level, accent, dark,
                onToggleTheme = {
                    dark = !dark
                    prefs.edit().putBoolean("dark", dark).apply()
                },
                onRefresh = { tick++ }
            )

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true
                        refreshOnce()
                        delay(350)
                        refreshing = false
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = pullState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = refreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = Card,
                        color = accent
                    )
                }
            ) {
                Crossfade(tab, Modifier.fillMaxSize(), tween(250), label = "tab") { t ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(12.dp))
                        if (t == 0) ScreenTab(screenState) else BatteryTab(live, rootBat)
                    }
                }
            }
            BottomBar(tab) { tab = it }
        }
    }
}

@Composable
fun ScreenTab(state: UiState) {
    when (state) {
        UiState.Loading -> Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
            Text("Okunuyor…", color = Muted, fontSize = 15.sp)
        }
        is UiState.Error -> Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Card).padding(24.dp)
        ) { Text(state.msg, color = Gold, fontSize = 15.sp, textAlign = TextAlign.Center) }
        is UiState.Ok -> {
            val s = state.s
            val frac = if (s.onBattery > 0) s.screenOn.toFloat() / s.onBattery else 0f
            Ring(frac, fmt(s.screenOn), "ekran açık", Green, Gold, Gold)
            Spacer(Modifier.height(10.dp))
            Text(
                "${s.screenOnCount} kez açıldı  ·  pilde geçen sürenin %${(frac * 100).toInt()}'i",
                color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(26.dp))
            InfoGrid(
                listOf(
                    "Pilde geçen süre" to fmt(s.onBattery),
                    "Ekran kapalı" to fmt(s.screenOff),
                    "Doze / AOD" to fmt(s.doze),
                    "Ekran açıkken harcanan" to "${s.screenOnMah} mAh",
                    "Düşen pil (ekran açık)" to "%${s.pctOn}",
                    "Düşen pil (ekran kapalı)" to "%${s.pctOff}"
                ),
                Green, Ink, Gold
            )
            if (s.estimated > 0) {
                Spacer(Modifier.height(12.dp))
                StatCard(
                    "Tahmini toplam ekran süresi (projeksiyon)", fmt(s.estimated),
                    Muted, Muted, Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun f(fmt: String, v: Any) = String.format(Locale.getDefault(), fmt, v)

@Composable
fun BatteryTab(live: Live?, root: RootBat?) {
    if (live == null) {
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
            Text("Okunuyor…", color = Muted, fontSize = 15.sp)
        }
        return
    }
    Ring(live.level / 100f, "%${live.level}", live.status, Ruby, RubyLight, RubyLight)
    Spacer(Modifier.height(10.dp))
    Text("${live.plug}  ·  ${f("%.1f", live.tempC)} °C", color = Muted, fontSize = 13.sp)
    Spacer(Modifier.height(24.dp))

    val sign = if (live.charging) "+" else "−"
    SectionTitle("Canlı değerler", "root gerekmez", Ruby)
    InfoGrid(
        listOf(
            "Anlık akım" to (live.ma?.let { "$sign$it mA" } ?: "—"),
            "Güç" to (live.ma?.let { f("%.2f W", live.mv / 1000f * it / 1000f) } ?: "—"),
            "Voltaj" to f("%.2f V", live.mv / 1000f),
            "Sıcaklık" to f("%.1f °C", live.tempC),
            "Durum" to live.status,
            "Güç kaynağı" to live.plug,
            "Sağlık durumu" to live.health,
            "Teknoloji" to live.tech,
            "Ortalama akım" to (live.avgMa?.let { "$it mA" } ?: "—"),
            "Şarj sayacı" to (live.chargeMah?.let { "$it mAh" } ?: "—"),
            "Tahmini kapasite" to (live.estCapMah?.let { "$it mAh" } ?: "—")
        ),
        Ruby, RubyLight, Gold
    )

    Spacer(Modifier.height(24.dp))
    SectionTitle("Kapasite ve döngü", "root", Gold)
    val cycle = root?.cycle ?: live.cycleIntent
    val design = root?.designMah
    val full = root?.fullMah ?: live.estCapMah
    val healthPct = if (design != null && full != null && design > 0) "%${(full * 100 / design).coerceAtMost(100)}" else "—"
    InfoGrid(
        listOf(
            "Döngü sayısı" to (cycle?.toString() ?: "—"),
            "Pil sağlığı" to healthPct,
            "Tasarım kapasitesi" to (design?.let { "$it mAh" } ?: "—"),
            "Mevcut kapasite" to (full?.let { "$it mAh" } ?: "—")
        ),
        Ruby, RubyLight, Gold
    )
    if (root != null && !root.ok) {
        Spacer(Modifier.height(10.dp))
        Text("Root verisi okunamadı, bazı alanlar boş.", color = Muted, fontSize = 12.sp)
    }
    Spacer(Modifier.height(20.dp))
}
