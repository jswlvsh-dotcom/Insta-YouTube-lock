package com.jaivish.gowork.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.jaivish.gowork.core.DayCounters
import com.jaivish.gowork.core.Settings
import com.jaivish.gowork.core.formatClockDuration
import com.jaivish.gowork.core.formatDuration
import com.jaivish.gowork.core.formatTimeOfDay
import com.jaivish.gowork.data.Graph
import com.jaivish.gowork.engine.LiveState
import com.jaivish.gowork.engine.LiveStatus
import com.jaivish.gowork.system.PermissionState
import com.jaivish.gowork.system.ScreenTimeReader
import com.jaivish.gowork.system.ScreenTimeSnapshot
import com.jaivish.gowork.ui.components.AppIcon
import com.jaivish.gowork.ui.components.Dot
import com.jaivish.gowork.ui.components.GoCard
import com.jaivish.gowork.ui.components.PrimaryButton
import com.jaivish.gowork.ui.components.ProgressRing
import com.jaivish.gowork.ui.components.SectionLabel
import com.jaivish.gowork.ui.components.StatBlock
import com.jaivish.gowork.ui.components.appColor
import com.jaivish.gowork.ui.components.rememberNow
import com.jaivish.gowork.ui.components.rememberPermissionState
import com.jaivish.gowork.ui.theme.GoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

data class AppToday(
    val pkg: String,
    val label: String,
    val usageMs: Long,
    val limitMinutes: Int,
    val counters: DayCounters,
)

@Composable
fun DashboardScreen(
    settings: Settings,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val permissions = rememberPermissionState()
    val live by LiveState.status.collectAsStateWithLifecycle()
    val statsVersion by Graph.stats.changes.collectAsStateWithLifecycle()
    val snapshot by rememberScreenTime(settings.trackedPackages)
    val now by rememberNow()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    val apps = remember(statsVersion, snapshot, settings.rules, today) {
        settings.rules.filter { it.enabled }.map { rule ->
            val counters = Graph.stats.get(today.toString(), rule.packageName)
            val system = snapshot?.trackedToday?.get(rule.packageName) ?: 0L
            AppToday(
                pkg = rule.packageName,
                label = Graph.apps.label(rule.packageName),
                usageMs = maxOf(counters.usageMs, system),
                limitMinutes = rule.dailyLimitMinutes,
                counters = counters,
            )
        }
    }
    val week = remember(statsVersion, snapshot, settings.rules, today) { weekTotals(settings, snapshot, today) }
    val streak = remember(statsVersion, settings.rules, today) { limitStreak(settings, today) }

    Box(
        Modifier
            .fillMaxSize()
            .background(GoColors.Ink)
            .systemBarsPadding(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Header(settings, onOpenSettings) }
            if (!permissions.allReady) item { SetupCard(permissions, onOpenPermissions) }
            item { TodayCard(settings, apps, live, permissions) }
            items(apps, key = { it.pkg }) { AppCard(it, live) }
            item { SprintCard(settings, now) }
            item { WeekCard(week) }
            item { WinsCard(apps, streak) }
            item { PhoneTodayCard(snapshot) }
            item {
                Text(
                    "Everything stays on this phone. GoWork never reads your messages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GoColors.Faint,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Header(settings: Settings, onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            val name = settings.userName.ifBlank { "there" }
            Text("Hey $name 👋", style = MaterialTheme.typography.titleMedium, color = GoColors.Muted)
            Text(
                "Go do your work.",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
        }
    }
}

@Composable
private fun SetupCard(permissions: PermissionState, onOpenPermissions: () -> Unit) {
    val critical = !permissions.coreReady
    GoCard(borderColor = if (critical) GoColors.Flame else GoColors.Stroke) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = if (critical) GoColors.Flame else GoColors.Volt)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (critical) "GoWork can't lock anything yet" else "Finish setup for the best protection",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Text(
                    if (critical) "Turn on GoWork in Accessibility so it can see Instagram & YouTube."
                    else "A few permissions are still missing (screen time, notifications or battery).",
                    style = MaterialTheme.typography.bodySmall,
                    color = GoColors.Muted,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Fix it now", onClick = onOpenPermissions, color = if (critical) GoColors.Flame else GoColors.Volt)
    }
}

@Composable
private fun TodayCard(settings: Settings, apps: List<AppToday>, live: LiveStatus, permissions: PermissionState) {
    val total = apps.sumOf { it.usageMs }
    val limitMs = apps.filter { it.limitMinutes > 0 }.sumOf { it.limitMinutes * 60_000L }
    val progress = if (limitMs > 0) total.toFloat() / limitMs else 0f
    val ringColor = when {
        progress >= 1f -> GoColors.Flame
        progress >= 0.75f -> GoColors.Volt
        else -> GoColors.Mint
    }
    val protecting = settings.protectionEnabled && permissions.coreReady && live.connected
    GoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(if (protecting) GoColors.Mint else GoColors.Flame)
            Spacer(Modifier.width(8.dp))
            SectionLabel(
                when {
                    !settings.protectionEnabled -> "Protection paused"
                    !permissions.coreReady -> "Protection off - needs Accessibility"
                    !live.connected -> "Starting the guard…"
                    else -> "Watching ${apps.joinToString(" & ") { it.label }.ifEmpty { "nothing yet" }}"
                },
                color = if (protecting) GoColors.Mint else GoColors.Flame,
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress = progress, color = ringColor, size = 132.dp, stroke = 12.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatDuration(total), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text(
                        if (limitMs > 0) "of ${formatDuration(limitMs)}" else "today",
                        style = MaterialTheme.typography.bodySmall,
                        color = GoColors.Muted,
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBlock("Every ${settings.nudgeIntervalMinutes} min", "\"Go do your work\" reminder", color = GoColors.Volt)
                StatBlock("${apps.sumOf { it.counters.opens }}×", "opened today")
                if (settings.goal.isNotBlank()) {
                    Text(
                        "🎯 ${settings.goal}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppCard(app: AppToday, live: LiveStatus) {
    val limitMs = app.limitMinutes * 60_000L
    val progress = if (limitMs > 0) (app.usageMs.toFloat() / limitMs).coerceIn(0f, 1f) else 0f
    val over = limitMs > 0 && app.usageMs >= limitMs
    GoCard(borderColor = if (over) GoColors.Flame.copy(alpha = 0.7f) else GoColors.Stroke) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.pkg, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    when {
                        over -> "Locked until midnight"
                        live.trackingPackage == app.pkg && live.msToNextNudge != null ->
                            "On it now · reminder in ${formatClockDuration(live.msToNextNudge)}"
                        limitMs > 0 -> "${formatDuration((limitMs - app.usageMs).coerceAtLeast(0))} left today"
                        else -> "No daily limit · reminders only"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) GoColors.Flame else GoColors.Muted,
                )
            }
            Text(formatDuration(app.usageMs), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        if (limitMs > 0) {
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = if (over) GoColors.Flame else appColor(app.pkg),
                trackColor = GoColors.PanelHigh,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatBlock("${app.counters.opens}", "opens", Modifier.weight(1f))
            StatBlock("${app.counters.nudges}", "reminders", Modifier.weight(1f))
            StatBlock("${app.counters.listened}", "times you left", Modifier.weight(1f), color = GoColors.Mint)
            if (app.counters.shortFormMs > 0) {
                StatBlock(formatDuration(app.counters.shortFormMs), "reels/shorts", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SprintCard(settings: Settings, now: Long) {
    var confirmEnd by remember { mutableStateOf(false) }
    val running = settings.sprintEndsAt > now
    GoCard(borderColor = if (running) GoColors.Sky else GoColors.Stroke) {
        SectionLabel("Focus sprint", color = if (running) GoColors.Sky else GoColors.Muted)
        Spacer(Modifier.height(8.dp))
        if (running) {
            Text(
                formatClockDuration(settings.sprintEndsAt - now),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
            )
            Text(
                "Instagram & co. are locked until ${formatTimeOfDay(settings.sprintEndsAt, ZoneId.systemDefault())}.",
                style = MaterialTheme.typography.bodyMedium,
                color = GoColors.Muted,
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { confirmEnd = true }, enabled = !settings.strictMode) {
                Text(if (settings.strictMode) "Strict mode: sprint can't be stopped" else "End sprint early", color = GoColors.Muted)
            }
        } else {
            Text("Lock every watched app for a while.", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text("Pick a sprint and do one task until it ends.", style = MaterialTheme.typography.bodySmall, color = GoColors.Muted)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 25, 45, 60).forEach { minutes ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            Graph.settings.update { it.copy(sprintEndsAt = System.currentTimeMillis() + minutes * 60_000L) }
                        },
                        label = { Text("$minutes min", fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = GoColors.PanelHigh,
                            labelColor = Color.White,
                        ),
                    )
                }
            }
        }
    }
    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End the sprint?") },
            text = { Text("Future you will thank you for finishing it.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEnd = false
                    Graph.settings.update { it.copy(sprintEndsAt = 0L) }
                }) { Text("End it", color = GoColors.Flame) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }) { Text("Keep going", color = GoColors.Volt) }
            },
            containerColor = GoColors.Panel,
        )
    }
}

@Composable
private fun WeekCard(week: List<Pair<LocalDate, Long>>) {
    GoCard {
        SectionLabel("Last 7 days · watched apps")
        Spacer(Modifier.height(14.dp))
        WeekChart(week)
        val average = if (week.isNotEmpty()) week.sumOf { it.second } / week.size else 0L
        Spacer(Modifier.height(10.dp))
        Text("Daily average ${formatDuration(average)}", style = MaterialTheme.typography.bodySmall, color = GoColors.Muted)
    }
}

@Composable
private fun WinsCard(apps: List<AppToday>, streak: Int) {
    val listened = apps.sumOf { it.counters.listened }
    val nudges = apps.sumOf { it.counters.nudges }
    GoCard {
        SectionLabel("Your wins")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatBlock("🔥 $streak", if (streak == 1) "day under limits" else "days under limits", Modifier.weight(1f))
            StatBlock("$listened", "times you went to work", Modifier.weight(1f), color = GoColors.Mint)
            StatBlock("$nudges", "reminders today", Modifier.weight(1f))
        }
    }
}

@Composable
private fun PhoneTodayCard(snapshot: ScreenTimeSnapshot?) {
    GoCard {
        SectionLabel("Whole phone today")
        Spacer(Modifier.height(10.dp))
        when {
            snapshot == null -> Text("Reading screen time…", color = GoColors.Muted)
            !snapshot.hasAccess -> Text(
                "Give GoWork \"Usage access\" to see your real screen time here.",
                style = MaterialTheme.typography.bodyMedium,
                color = GoColors.Muted,
            )
            else -> {
                Text(formatDuration(snapshot.totalTodayMs), style = MaterialTheme.typography.displaySmall, color = Color.White)
                Text("screen time so far", style = MaterialTheme.typography.bodySmall, color = GoColors.Muted)
                Spacer(Modifier.height(12.dp))
                val max = snapshot.topApps.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
                snapshot.topApps.forEach { (pkg, ms) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp),
                    ) {
                        AppIcon(pkg, size = 28.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Graph.apps.label(pkg), style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth(ms.toFloat() / max)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(appColor(pkg)),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(formatDuration(ms), style = MaterialTheme.typography.bodyMedium, color = GoColors.Muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** Phone screen time, refreshed every minute while the dashboard is visible. */
@Composable
private fun rememberScreenTime(tracked: List<String>): State<ScreenTimeSnapshot?> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    return produceState<ScreenTimeSnapshot?>(initialValue = null, tracked, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = withContext(Dispatchers.IO) {
                    runCatching { ScreenTimeReader(context).snapshot(tracked) }.getOrNull()
                } ?: ScreenTimeSnapshot(hasAccess = false)
                delay(60_000)
            }
        }
    }
}

/** Watched-app total per day: the higher of Android's screen time and GoWork's own count. */
private fun weekTotals(settings: Settings, snapshot: ScreenTimeSnapshot?, today: LocalDate): List<Pair<LocalDate, Long>> {
    val tracked = settings.trackedPackages
    val system = snapshot?.week?.associateBy { it.date }.orEmpty()
    return (6 downTo 0).map { back ->
        val date = today.minusDays(back.toLong())
        val ours = Graph.stats.day(date.toString())
        val total = tracked.sumOf { pkg ->
            maxOf(ours[pkg]?.usageMs ?: 0L, system[date]?.perApp?.get(pkg) ?: 0L)
        }
        date to total
    }
}

/** Days in a row (ending today) where no watched app hit its limit, since GoWork was installed. */
private fun limitStreak(settings: Settings, today: LocalDate): Int {
    val installed = Graph.installDate()
    var streak = 0
    var date = today
    while (!date.isBefore(installed) && streak < 365) {
        val day = Graph.stats.day(date.toString())
        val hit = settings.trackedPackages.any { day[it]?.limitHit == true }
        if (hit) break
        streak++
        date = date.minusDays(1)
    }
    return streak
}
