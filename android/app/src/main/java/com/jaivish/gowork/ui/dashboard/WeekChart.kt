package com.jaivish.gowork.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jaivish.gowork.core.formatDuration
import com.jaivish.gowork.ui.theme.GoColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Seven bars, today highlighted. */
@Composable
fun WeekChart(days: List<Pair<LocalDate, Long>>) {
    if (days.isEmpty()) return
    val max = days.maxOf { it.second }.coerceAtLeast(60_000L)
    val today = days.last().first
    Column {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            days.forEach { (date, ms) ->
                Text(
                    if (ms > 0) formatDuration(ms) else "–",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (date == today) GoColors.Volt else GoColors.Faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            val slot = size.width / days.size
            val barWidth = slot * 0.46f
            days.forEachIndexed { index, (date, ms) ->
                val fraction = (ms.toFloat() / max).coerceIn(0f, 1f)
                val barHeight = (size.height - 8.dp.toPx()) * fraction
                val left = slot * index + (slot - barWidth) / 2
                drawRoundRect(
                    color = GoColors.PanelHigh,
                    topLeft = Offset(left, 8.dp.toPx()),
                    size = Size(barWidth, size.height - 8.dp.toPx()),
                    cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                )
                if (barHeight > 0f) {
                    drawRoundRect(
                        color = if (date == today) GoColors.Volt else GoColors.Volt.copy(alpha = 0.45f),
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            days.forEach { (date, _) ->
                Text(
                    if (date == today) "Today" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (date == today) MaterialTheme.colorScheme.onSurface else GoColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
