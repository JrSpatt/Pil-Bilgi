package com.spatt.ekran.suresi

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Bg = Color(0xFF080D0A)
val Card = Color(0xFF111915)
val Track = Color(0xFF1B2620)
val Green = Color(0xFF3DDC84)
val Gold = Color(0xFFE8B84A)
val Muted = Color(0xFF86948B)
val Ink = Color(0xFFEAF5EE)
val Ruby = Color(0xFFE0115F)
val RubyLight = Color(0xFFFF5C8A)

@Composable
fun Header(title: String, subtitle: String, level: Int, accent: Color, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Gold, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
        BatteryIcon(level, accent)
        Spacer(Modifier.width(6.dp))
        Text("%$level", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Card).clickable { onRefresh() },
            contentAlignment = Alignment.Center
        ) { Text("↻", color = accent, fontSize = 20.sp) }
    }
}

@Composable
fun BatteryIcon(level: Int, color: Color, modifier: Modifier = Modifier.size(width = 34.dp, height = 16.dp)) {
    Canvas(modifier) {
        val nub = 3.dp.toPx()
        val bodyW = size.width - nub
        val stroke = 1.6.dp.toPx()
        drawRoundRect(
            color.copy(alpha = 0.85f), Offset(stroke / 2, stroke / 2),
            Size(bodyW - stroke, size.height - stroke), CornerRadius(5.dp.toPx()), style = Stroke(stroke)
        )
        val pad = 3.5.dp.toPx()
        drawRoundRect(
            color, Offset(pad, pad),
            Size(((bodyW - 2 * pad) * level / 100f).coerceAtLeast(0f), size.height - 2 * pad),
            CornerRadius(2.5.dp.toPx())
        )
        drawRoundRect(
            color.copy(alpha = 0.85f), Offset(bodyW + 0.5.dp.toPx(), size.height * 0.3f),
            Size(2.dp.toPx(), size.height * 0.4f), CornerRadius(1.dp.toPx())
        )
    }
}

@Composable
fun ScreenIcon(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val sw = 1.8.dp.toPx()
        drawRoundRect(
            color, Offset(6.dp.toPx(), 2.dp.toPx()), Size(12.dp.toPx(), 20.dp.toPx()),
            CornerRadius(3.5.dp.toPx()), style = Stroke(sw)
        )
        drawLine(
            color, Offset(10.dp.toPx(), 18.5.dp.toPx()), Offset(14.dp.toPx(), 18.5.dp.toPx()),
            strokeWidth = sw, cap = StrokeCap.Round
        )
    }
}

@Composable
fun Ring(fraction: Float, center: String, sub: String, start: Color, end: Color, centerColor: Color) {
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "ring")
    Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val sw = 20.dp.toPx()
            val tl = Offset(sw / 2, sw / 2)
            val sz = Size(size.width - sw, size.height - sw)
            drawArc(Track, 0f, 360f, false, tl, sz, style = Stroke(sw))
            rotate(-90f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to start, maxOf(anim, 0.02f) to end, 1f to end
                    ),
                    startAngle = 0f, sweepAngle = 360f * anim, useCenter = false,
                    topLeft = tl, size = sz, style = Stroke(sw, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(center, color = centerColor, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
fun StatCard(label: String, value: String, dot: Color, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(value, color = valueColor, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = Muted, fontSize = 12.sp, lineHeight = 15.sp)
    }
}

@Composable
fun InfoGrid(items: List<Pair<String, String>>, dot: Color, left: Color, right: Color) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(row[0].first, row[0].second, dot, left, Modifier.weight(1f).fillMaxHeight())
                if (row.size > 1) StatCard(row[1].first, row[1].second, dot, right, Modifier.weight(1f).fillMaxHeight())
                else Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, tag: String, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Gold, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(
            tag, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun BottomBar(tab: Int, onTab: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(30.dp)).background(Card).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TabItem(tab == 0, "Ekran", Green, Modifier.weight(1f), { onTab(0) }) { c -> ScreenIcon(c) }
        TabItem(tab == 1, "Pil", RubyLight, Modifier.weight(1f), { onTab(1) }) { c ->
            BatteryIcon(60, c, Modifier.size(width = 30.dp, height = 15.dp))
        }
    }
}

@Composable
fun TabItem(
    selected: Boolean, label: String, accent: Color, modifier: Modifier,
    onClick: () -> Unit, icon: @Composable (Color) -> Unit
) {
    val bgc by animateColorAsState(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent, tween(250), label = "bg")
    val fg by animateColorAsState(if (selected) accent else Muted, tween(250), label = "fg")
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(bgc)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) { icon(fg) }
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}
