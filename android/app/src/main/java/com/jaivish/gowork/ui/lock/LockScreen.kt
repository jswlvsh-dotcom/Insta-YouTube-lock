package com.jaivish.gowork.ui.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaivish.gowork.core.formatDuration
import com.jaivish.gowork.engine.LockActions
import com.jaivish.gowork.engine.LockKind
import com.jaivish.gowork.engine.LockRequest
import com.jaivish.gowork.engine.Messages
import com.jaivish.gowork.ui.components.Dot
import com.jaivish.gowork.ui.components.PrimaryButton
import com.jaivish.gowork.ui.components.ProgressRing
import com.jaivish.gowork.ui.components.SecondaryButton
import com.jaivish.gowork.ui.theme.GoColors
import kotlinx.coroutines.delay

/** The full-screen "GO DO YOUR WORK." lock. Shown by the overlay and by the in-app preview. */
@Composable
fun LockScreen(request: LockRequest, actions: LockActions) {
    val accent = accentFor(request.kind)
    var remaining by remember(request.id) { mutableIntStateOf(request.waitSeconds) }
    LaunchedEffect(request.id) {
        while (remaining > 0) {
            delay(1_000)
            remaining -= 1
        }
    }
    val appear = remember(request.id) { Animatable(0f) }
    LaunchedEffect(request.id) { appear.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing)) }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GoColors.Ink)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.32f * glow), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.12f),
                        radius = size.width * 0.95f,
                    ),
                    radius = size.width * 0.95f,
                    center = Offset(size.width * 0.85f, size.height * 0.12f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.1f, size.height * 0.95f),
                        radius = size.width * 0.8f,
                    ),
                    radius = size.width * 0.8f,
                    center = Offset(size.width * 0.1f, size.height * 0.95f),
                )
            }
            // Swallow taps so nothing reaches the app underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 56.dp)
                .graphicsLayer {
                    alpha = appear.value
                    translationY = (1f - appear.value) * 60f
                },
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                KindChip(request, accent)
                Spacer(Modifier.height(28.dp))
                if (request.userName.isNotEmpty() && request.kind == LockKind.NUDGE) {
                    Text(
                        "${request.userName.uppercase()},",
                        style = MaterialTheme.typography.headlineMedium,
                        color = GoColors.Muted,
                    )
                }
                Headline(Messages.headline(request.kind), accent)
                Spacer(Modifier.height(16.dp))
                Text(subtitleFor(request), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.86f))
                Spacer(Modifier.height(10.dp))
                Text(request.message, style = MaterialTheme.typography.bodyLarge, color = GoColors.Muted)
                if (request.goal.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    GoalCard(request.goal, accent)
                }
                Spacer(Modifier.height(22.dp))
                StatsRow(request)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val secondary = secondaryLabel(request, remaining)
                if (remaining > 0 && secondary != null) {
                    ProgressRing(
                        progress = remaining / request.waitSeconds.coerceAtLeast(1).toFloat(),
                        color = accent,
                        size = 64.dp,
                        stroke = 6.dp,
                    ) {
                        Text("$remaining", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                PrimaryButton(text = primaryLabel(request), onClick = { actions.primary(request) }, color = accent)
                if (secondary != null) {
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(
                        text = secondary,
                        onClick = { actions.secondary(request) },
                        enabled = remaining <= 0,
                    )
                }
            }
        }
    }
}

@Composable
private fun Headline(text: String, accent: Color) {
    val words = text.split(" ")
    val styled = buildAnnotatedString {
        words.forEachIndexed { index, word ->
            if (index == words.lastIndex) {
                withStyle(SpanStyle(color = accent)) { append(word) }
            } else {
                append(word)
                append(" ")
            }
        }
    }
    Text(
        styled,
        color = Color.White,
        fontSize = 54.sp,
        lineHeight = 56.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.5).sp,
    )
}

@Composable
private fun KindChip(request: LockRequest, accent: Color) {
    val label = when (request.kind) {
        LockKind.OPEN_PAUSE -> "MINDFUL PAUSE"
        LockKind.NUDGE -> "REMINDER #${request.nudgeNumber}"
        LockKind.LIMIT -> "DAILY LIMIT"
        LockKind.FOCUS -> "FOCUS HOURS"
        LockKind.SPRINT -> "FOCUS SPRINT"
        LockKind.SHORT_FORM -> "SHORT VIDEOS BLOCKED"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(GoColors.Panel)
            .border(1.dp, GoColors.Stroke, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Dot(accent)
        Spacer(Modifier.width(8.dp))
        Text(
            "${request.appLabel.uppercase()}  ·  $label",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun GoalCard(goal: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GoColors.Panel)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🎯", fontSize = 26.sp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text("YOUR GOAL", style = MaterialTheme.typography.labelSmall, color = accent)
            Text(goal, style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

@Composable
private fun StatsRow(request: LockRequest) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile("Today", formatDuration(request.todayUsageMs), Modifier.weight(1f))
        StatTile("Opens", "${request.todayOpens}×", Modifier.weight(1f))
        StatTile(
            "Limit",
            if (request.limitMinutes > 0) "${request.limitMinutes}m" else "Off",
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GoColors.Panel)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = GoColors.Muted)
    }
}

private fun accentFor(kind: LockKind): Color = when (kind) {
    LockKind.OPEN_PAUSE, LockKind.NUDGE -> GoColors.Volt
    LockKind.LIMIT, LockKind.SHORT_FORM -> GoColors.Flame
    LockKind.FOCUS, LockKind.SPRINT -> GoColors.Sky
}

private fun subtitleFor(r: LockRequest): String = when (r.kind) {
    LockKind.OPEN_PAUSE ->
        "Take one breath. You've opened ${r.appLabel} ${r.todayOpens}× today (${formatDuration(r.todayUsageMs)}). Do you really need it right now?"
    LockKind.NUDGE ->
        "${r.intervalMinutes * r.nudgeNumber.coerceAtLeast(1)} minutes this session. Close ${r.appLabel} and get back to it."
    LockKind.LIMIT ->
        "${r.appLabel} is locked until midnight. You used ${formatDuration(r.todayUsageMs)} of your ${r.limitMinutes}m."
    LockKind.FOCUS ->
        "${r.appLabel} is locked during your focus hours, until ${r.untilLabel ?: "later"}."
    LockKind.SPRINT ->
        "Your sprint runs until ${r.untilLabel ?: "later"}. Nothing on ${r.appLabel} is worth breaking it."
    LockKind.SHORT_FORM ->
        "Reels and Shorts are blocked. The rest of ${r.appLabel} still works."
}

private fun primaryLabel(r: LockRequest): String = when (r.kind) {
    LockKind.OPEN_PAUSE -> "Never mind, back to work"
    LockKind.NUDGE -> "I'm going to work 💪"
    LockKind.LIMIT -> "Close ${r.appLabel}"
    LockKind.FOCUS, LockKind.SPRINT -> "Back to work"
    LockKind.SHORT_FORM -> "Get me out of here"
}

private fun secondaryLabel(r: LockRequest, remaining: Int): String? {
    val wait = if (remaining > 0) " (${remaining}s)" else ""
    return when (r.kind) {
        LockKind.OPEN_PAUSE -> "I need ${r.appLabel} for work$wait"
        LockKind.NUDGE -> "Give me ${r.intervalMinutes} more min$wait"
        LockKind.LIMIT, LockKind.FOCUS, LockKind.SPRINT ->
            if (r.emergenciesLeft > 0) "Emergency 5 min · ${r.emergenciesLeft} left$wait" else null
        LockKind.SHORT_FORM -> "Close ${r.appLabel}"
    }
}
