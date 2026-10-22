package com.spatt.ekran.suresi

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.abs

/** Tek bir kalıcı root kabuğu: Magisk uyarısı uygulama başına bir kez çıkar. */
object RootShell {
    private var proc: Process? = null
    private var writer: java.io.BufferedWriter? = null
    private var reader: java.io.BufferedReader? = null
    private const val END = "__EOC__"

    @Synchronized
    fun exec(cmd: String): String? {
        return try {
            if (proc?.isAlive != true) {
                val p = ProcessBuilder("su").redirectErrorStream(true).start()
                proc = p
                writer = p.outputStream.bufferedWriter()
                reader = p.inputStream.bufferedReader()
            }
            val w = writer!!
            val r = reader!!
            w.write(cmd + "\necho " + END + "\n")
            w.flush()
            val sb = StringBuilder()
            while (true) {
                val line = r.readLine()
                if (line == null) { close(); return null }
                if (line.trim() == END) break
                sb.append(line).append('\n')
            }
            sb.toString()
        } catch (e: Exception) {
            close(); null
        }
    }

    private fun close() {
        try { proc?.destroy() } catch (_: Exception) {}
        proc = null; writer = null; reader = null
    }
}

// ---------- Ekran süresi (root) ----------

data class Stats(
    val onBattery: Long, val screenOff: Long, val doze: Long, val screenOn: Long,
    val screenOnCount: Int, val screenOnMah: Int, val pctOn: Int, val pctOff: Int, val estimated: Long
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
    val out = RootShell.exec(
        "dumpsys batterystats --charged | grep -E \"Time on battery|Screen on|Amount discharged while screen|Estimated screen on time\""
    ) ?: return UiState.Error("Root erişimi alınamadı. Root izni verdiğinizden emin olun.")
    if (out.isBlank()) return UiState.Error("Veri okunamadı.")

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
    return if (on == 0L && onBat == 0L) UiState.Error("Veri okunamadı. Root izni verildiğinden emin olun.")
    else UiState.Ok(Stats(onBat, off, doze, on, cnt, mah, maxOf(pOn, 0), maxOf(pOff, 0), est))
}

fun fmt(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms % 3_600_000) / 60_000
    return if (h > 0) "$h sa $m dk" else "$m dk"
}

// ---------- Pil: root gerektirmeyen canlı değerler ----------

data class Live(
    val level: Int, val status: String, val charging: Boolean, val plug: String,
    val health: String, val tech: String, val tempC: Float, val mv: Int,
    val ma: Int?, val avgMa: Int?, val chargeMah: Int?, val estCapMah: Int?, val cycleIntent: Int?
)

fun batteryLevel(ctx: Context): Int {
    val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val lvl = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    return if (lvl < 0) 0 else lvl * 100 / scale
}

fun readLive(ctx: Context): Live {
    val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val level = batteryLevel(ctx)
    val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val status = when (st) {
        2 -> "Şarj oluyor"; 3 -> "Deşarj oluyor"; 4 -> "Şarj olmuyor"; 5 -> "Dolu"; else -> "Bilinmiyor"
    }
    val p = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    val plug = when {
        (p and 4) != 0 -> "Kablosuz"
        (p and 2) != 0 -> "USB"
        (p and 1) != 0 -> "AC (priz)"
        p == 0 -> "Takılı değil"
        else -> "Dock"
    }
    val health = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 1) ?: 1) {
        2 -> "İyi"; 3 -> "Aşırı ısınmış"; 4 -> "Bozuk"; 5 -> "Aşırı voltaj"
        6 -> "Arıza"; 7 -> "Çok soğuk"; else -> "Bilinmiyor"
    }
    val tech = i?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.ifBlank { null } ?: "—"
    val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    val mv = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
    val cyc = i?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1

    // Cihazlar birimi farklı verir: çoğu µA/µAh, bazıları (ör. bazı tabletler) doğrudan mA/mAh.
    fun ua(prop: Int): Int? {
        val v = bm.getIntProperty(prop)
        if (v == Int.MIN_VALUE) return null
        val a = abs(v)
        return if (a < 5000) a else a / 1000
    }
    val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
    val chargeMah = when {
        counter == Int.MIN_VALUE || counter <= 0 -> null
        counter > 100_000 -> counter / 1000   // µAh
        else -> counter                        // zaten mAh
    }
    val est = if (chargeMah != null && level > 0) chargeMah * 100 / level else null

    return Live(
        level, status, st == 2, plug, health, tech, temp, mv,
        ua(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
        ua(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE),
        chargeMah, est, if (cyc >= 0) cyc else null
    )
}

// ---------- Pil: root ile okunan sabit değerler ----------

data class RootBat(val cycle: Int?, val designMah: Int?, val fullMah: Int?, val ok: Boolean)

private fun toMah(v: Long): Int = if (v > 100_000) (v / 1000).toInt() else v.toInt()

fun readRootBat(): RootBat {
    val cmd = "for d in battery bms; do for f in cycle_count charge_full_design charge_full; do " +
        "v=\$(cat /sys/class/power_supply/\$d/\$f 2>/dev/null); " +
        "[ -n \"\$v\" ] && echo \"\$d/\$f=\$v\"; done; done"
    val out = RootShell.exec(cmd) ?: return RootBat(null, null, null, false)
    val m = out.lineSequence().mapNotNull { l ->
        val p = l.split("=")
        if (p.size == 2) p[0].trim() to p[1].trim() else null
    }.toMap()

    fun get(f: String): Long? =
        listOf("battery/$f", "bms/$f").mapNotNull { m[it]?.toLongOrNull() }.firstOrNull { it > 0 }

    val cycle = listOf("battery/cycle_count", "bms/cycle_count")
        .mapNotNull { m[it]?.toIntOrNull() }.firstOrNull { it >= 0 }
    return RootBat(cycle, get("charge_full_design")?.let(::toMah), get("charge_full")?.let(::toMah), true)
}
