package com.jaivish.gowork.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.jaivish.gowork.core.KnownApps
import com.jaivish.gowork.data.Graph
import com.jaivish.gowork.system.PermissionState
import com.jaivish.gowork.system.Permissions
import com.jaivish.gowork.ui.theme.GoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun GoCard(
    modifier: Modifier = Modifier,
    borderColor: Color = GoColors.Stroke,
    background: Color = GoColors.Panel,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .padding(20.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = GoColors.Muted) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
    )
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = GoColors.Volt,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = GoColors.Ink,
            disabledContainerColor = GoColors.PanelHigh,
            disabledContentColor = GoColors.Faint,
        ),
    ) {
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (enabled) GoColors.Stroke else GoColors.PanelHigh),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White, disabledContentColor = GoColors.Faint),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SettingSwitch(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) Color.White else GoColors.Faint)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = GoColors.Muted)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GoColors.Ink,
                checkedTrackColor = GoColors.Volt,
                uncheckedThumbColor = GoColors.Muted,
                uncheckedTrackColor = GoColors.PanelHigh,
                uncheckedBorderColor = GoColors.Stroke,
            ),
        )
    }
}

@Composable
fun SettingSlider(
    title: String,
    valueLabel: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    onChange: (Int) -> Unit,
    enabled: Boolean = true,
    subtitle: String? = null,
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) Color.White else GoColors.Faint,
                modifier = Modifier.weight(1f),
            )
            Text(valueLabel, style = MaterialTheme.typography.titleMedium, color = GoColors.Volt)
        }
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = GoColors.Muted)
        val stops = ((range.last - range.first) / step).coerceAtLeast(1)
        Slider(
            value = value.coerceIn(range).toFloat(),
            onValueChange = { raw ->
                val snapped = range.first + (((raw - range.first) / step).let { kotlin.math.round(it) }.toInt() * step)
                if (snapped != value) onChange(snapped.coerceIn(range))
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (stops - 1).coerceAtLeast(0),
            enabled = enabled,
        )
    }
}

/** Circular progress ring with content in the middle. */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    stroke: Dp = 14.dp,
    trackColor: Color = GoColors.PanelHigh,
    content: @Composable () -> Unit = {},
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            val sweep = 360f * progress.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

@Composable
fun AppIcon(pkg: String, size: Dp = 40.dp) {
    val bitmap by produceState<ImageBitmap?>(initialValue = Graph.apps.cachedIcon(pkg), pkg) {
        if (value == null) value = withContext(Dispatchers.IO) { Graph.apps.icon(pkg) }
    }
    val image = bitmap
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(size / 4)))
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4))
                .background(appColor(pkg)),
        ) {
            Text(Graph.apps.label(pkg).take(1), color = Color.White, fontWeight = FontWeight.Black)
        }
    }
}

fun appColor(pkg: String): Color = when (pkg) {
    KnownApps.INSTAGRAM -> GoColors.Insta
    KnownApps.YOUTUBE -> GoColors.Youtube
    else -> GoColors.Sky
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun StatBlock(value: String, label: String, modifier: Modifier = Modifier, color: Color = Color.White) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = GoColors.Muted, maxLines = 1)
    }
}

/** Permission status, refreshed every second while the screen is visible. */
@Composable
fun rememberPermissionState(): PermissionState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var state by remember { mutableStateOf(Permissions.read(context)) }
    androidx.compose.runtime.LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                state = Permissions.read(context)
                delay(1_000)
            }
        }
    }
    return state
}

/** Current wall-clock time, ticking every [periodMs]. */
@Composable
fun rememberNow(periodMs: Long = 1_000L): State<Long> = produceState(System.currentTimeMillis(), periodMs) {
    while (true) {
        value = System.currentTimeMillis()
        delay(periodMs)
    }
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))
