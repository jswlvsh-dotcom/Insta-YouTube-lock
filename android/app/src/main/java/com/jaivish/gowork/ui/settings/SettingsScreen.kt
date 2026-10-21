package com.jaivish.gowork.ui.settings

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.jaivish.gowork.BuildConfigInfo
import com.jaivish.gowork.core.AppRule
import com.jaivish.gowork.core.KnownApps
import com.jaivish.gowork.core.Settings
import com.jaivish.gowork.core.formatClockDuration
import com.jaivish.gowork.core.formatMinuteOfDay
import com.jaivish.gowork.data.Graph
import com.jaivish.gowork.engine.LockKind
import com.jaivish.gowork.service.ShortFormDetector
import com.jaivish.gowork.service.Voice
import com.jaivish.gowork.engine.Messages
import com.jaivish.gowork.ui.components.AppIcon
import com.jaivish.gowork.ui.components.GoCard
import com.jaivish.gowork.ui.components.PrimaryButton
import com.jaivish.gowork.ui.components.ScreenHeader
import com.jaivish.gowork.ui.components.SecondaryButton
import com.jaivish.gowork.ui.components.SectionLabel
import com.jaivish.gowork.ui.components.SettingSlider
import com.jaivish.gowork.ui.components.SettingSwitch
import com.jaivish.gowork.ui.components.rememberNow
import com.jaivish.gowork.ui.theme.GoColors
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: Settings,
    onBack: () -> Unit,
    onAddApps: () -> Unit,
    onPreview: (LockKind) -> Unit,
) {
    val now by rememberNow()
    var unlockCountdownEnds by remember { mutableLongStateOf(0L) }
    val editable = StrictGate.canEdit(settings, now)

    LaunchedEffect(unlockCountdownEnds, now) {
        if (unlockCountdownEnds > 0 && now >= unlockCountdownEnds) {
            unlockCountdownEnds = 0L
            StrictGate.unlockedUntil = System.currentTimeMillis() + STRICT_UNLOCK_WINDOW_MS
        }
    }

    fun update(transform: (Settings) -> Settings) {
        if (editable) Graph.settings.update(transform)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GoColors.Ink)
            .systemBarsPadding()
            .imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ScreenHeader("Settings", onBack) }

        if (settings.strictMode) {
            item {
                StrictGateCard(
                    editable = editable,
                    unlockedFor = (StrictGate.unlockedUntil - now).coerceAtLeast(0L),
                    countdown = if (unlockCountdownEnds > 0) (unlockCountdownEnds - now).coerceAtLeast(0L) else null,
                    onStart = { unlockCountdownEnds = System.currentTimeMillis() + STRICT_COUNTDOWN_MS },
                )
            }
        }

        item {
            GoCard {
                SectionLabel("Protection")
                SettingSwitch(
                    title = "Protection on",
                    subtitle = "Reminders, limits and locks for your watched apps.",
                    checked = settings.protectionEnabled,
                    onCheckedChange = { on -> update { it.copy(protectionEnabled = on) } },
                    enabled = editable,
                )
            }
        }

        item {
            GoCard {
                SectionLabel("About you")
                Spacer(Modifier.height(10.dp))
                GoTextField(
                    value = settings.userName,
                    label = "Your name (the lock calls you by it)",
                    enabled = editable,
                    onChange = { v -> update { it.copy(userName = v) } },
                )
                Spacer(Modifier.height(10.dp))
                GoTextField(
                    value = settings.goal,
                    label = "What are you working on?",
                    enabled = editable,
                    onChange = { v -> update { it.copy(goal = v) } },
                    capitalization = KeyboardCapitalization.Sentences,
                )
            }
        }

        item {
            GoCard {
                SectionLabel("Go do your work - reminders")
                SettingSlider(
                    title = "Remind me every",
                    valueLabel = "${settings.nudgeIntervalMinutes} min",
                    value = settings.nudgeIntervalMinutes,
                    range = 1..30,
                    onChange = { v -> update { it.copy(nudgeIntervalMinutes = v) } },
                    enabled = editable,
                    subtitle = "Of use on your watched apps, all added together.",
                )
                SettingSlider(
                    title = "Wait before \"Continue\"",
                    valueLabel = "${settings.nudgeWaitSeconds} s",
                    value = settings.nudgeWaitSeconds,
                    range = 0..60,
                    step = 5,
                    onChange = { v -> update { it.copy(nudgeWaitSeconds = v) } },
                    enabled = editable,
                )
                SettingSwitch(
                    title = "Each reminder waits longer",
                    subtitle = "+5 s every time you continue in the same session.",
                    checked = settings.escalateWait,
                    onCheckedChange = { on -> update { it.copy(escalateWait = on) } },
                    enabled = editable,
                )
                HorizontalDivider(color = GoColors.Stroke, modifier = Modifier.padding(vertical = 8.dp))
                SettingSwitch(
                    title = "Mindful pause on open",
                    subtitle = "\"Why are you here?\" when you open a watched app after a break.",
                    checked = settings.mindfulOpen,
                    onCheckedChange = { on -> update { it.copy(mindfulOpen = on) } },
                    enabled = editable,
                )
                if (settings.mindfulOpen) {
                    SettingSlider(
                        title = "Pause length",
                        valueLabel = "${settings.mindfulOpenSeconds} s",
                        value = settings.mindfulOpenSeconds,
                        range = 3..15,
                        onChange = { v -> update { it.copy(mindfulOpenSeconds = v) } },
                        enabled = editable,
                    )
                }
                HorizontalDivider(color = GoColors.Stroke, modifier = Modifier.padding(vertical = 8.dp))
                SettingSwitch(
                    title = "Say it out loud",
                    subtitle = "A voice says \"${Messages.spoken(LockKind.NUDGE, settings.userName)}\"",
                    checked = settings.voiceEnabled,
                    onCheckedChange = { on -> update { it.copy(voiceEnabled = on) } },
                    enabled = editable,
                )
                if (settings.voiceEnabled) VoiceTestButton(settings.userName)
                SettingSwitch(
                    title = "Vibrate",
                    checked = settings.vibrationEnabled,
                    onCheckedChange = { on -> update { it.copy(vibrationEnabled = on) } },
                    enabled = editable,
                )
            }
        }

        item {
            GoCard {
                SectionLabel("Watched apps & daily limits")
                settings.rules.forEach { rule ->
                    AppRuleEditor(
                        rule = rule,
                        enabled = editable,
                        onChange = { changed ->
                            update { s -> s.copy(rules = s.rules.map { if (it.packageName == changed.packageName) changed else it }) }
                        },
                        onRemove = {
                            update { s -> s.copy(rules = s.rules.filterNot { it.packageName == rule.packageName }) }
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                SecondaryButton("+ Add more apps", onClick = onAddApps, enabled = editable)
            }
        }

        item { FocusHoursCard(settings, editable, ::update) }

        item {
            GoCard {
                SectionLabel("Strict mode")
                SettingSwitch(
                    title = "Strict mode",
                    subtitle = "Changing any setting needs a 60-second wait. Sprints can't be stopped early.",
                    checked = settings.strictMode,
                    onCheckedChange = { on -> update { it.copy(strictMode = on) } },
                    enabled = editable,
                )
            }
        }

        item {
            GoCard {
                SectionLabel("See the locks")
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        LockKind.NUDGE to "Reminder",
                        LockKind.OPEN_PAUSE to "Mindful pause",
                        LockKind.LIMIT to "Daily limit",
                        LockKind.FOCUS to "Focus hours",
                        LockKind.SHORT_FORM to "Reels block",
                    ).forEach { (kind, label) ->
                        FilterChip(
                            selected = false,
                            onClick = { onPreview(kind) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(containerColor = GoColors.PanelHigh, labelColor = Color.White),
                        )
                    }
                }
            }
        }

        item {
            Text(
                "GoWork ${BuildConfigInfo.versionName(LocalContext.current)} · Made to get you back to work.",
                style = MaterialTheme.typography.bodySmall,
                color = GoColors.Faint,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun StrictGateCard(editable: Boolean, unlockedFor: Long, countdown: Long?, onStart: () -> Unit) {
    GoCard(borderColor = if (editable) GoColors.Mint else GoColors.Volt) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = if (editable) GoColors.Mint else GoColors.Volt)
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    editable -> "Unlocked for ${formatClockDuration(unlockedFor)}"
                    countdown != null -> "Unlocking in ${formatClockDuration(countdown)}…"
                    else -> "Settings are locked (strict mode)"
                },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
        if (!editable) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (countdown != null) "Still sure? Use this minute to think about why you want to change it."
                else "Wait 60 seconds to edit for 3 minutes.",
                style = MaterialTheme.typography.bodySmall,
                color = GoColors.Muted,
            )
            if (countdown == null) {
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Start 60 s unlock", onClick = onStart)
            }
        }
    }
}

@Composable
private fun AppRuleEditor(
    rule: AppRule,
    enabled: Boolean,
    onChange: (AppRule) -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(rule.packageName, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(Graph.apps.label(rule.packageName), style = MaterialTheme.typography.titleMedium, color = Color.White)
                if (!Graph.apps.isInstalled(rule.packageName)) {
                    Text("Not installed", style = MaterialTheme.typography.bodySmall, color = GoColors.Faint)
                }
            }
            androidx.compose.material3.Switch(
                checked = rule.enabled,
                onCheckedChange = { onChange(rule.copy(enabled = it)) },
                enabled = enabled,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = GoColors.Ink,
                    checkedTrackColor = GoColors.Volt,
                    uncheckedTrackColor = GoColors.PanelHigh,
                ),
            )
            if (rule.packageName != KnownApps.INSTAGRAM && rule.packageName != KnownApps.YOUTUBE) {
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove", tint = GoColors.Muted)
                }
            }
        }
        if (rule.enabled) {
            SettingSlider(
                title = "Daily limit",
                valueLabel = if (rule.dailyLimitMinutes == 0) "Off" else "${rule.dailyLimitMinutes} min",
                value = rule.dailyLimitMinutes,
                range = 0..240,
                step = 5,
                onChange = { onChange(rule.copy(dailyLimitMinutes = it)) },
                enabled = enabled,
            )
            if (ShortFormDetector.supports(rule.packageName)) {
                SettingSwitch(
                    title = if (rule.packageName == KnownApps.INSTAGRAM) "Block Reels" else "Block Shorts",
                    subtitle = "Locks the short-video player instantly; the rest of the app still works. (Beta)",
                    checked = rule.blockShortForm,
                    onCheckedChange = { onChange(rule.copy(blockShortForm = it)) },
                    enabled = enabled,
                )
            }
        }
        HorizontalDivider(color = GoColors.Stroke, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun FocusHoursCard(settings: Settings, editable: Boolean, update: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            initial / 60,
            initial % 60,
            DateFormat.is24HourFormat(context),
        ).show()
    }
    GoCard {
        SectionLabel("Focus hours")
        SettingSwitch(
            title = "Lock watched apps during work hours",
            subtitle = "Hard lock with up to 3 emergency unlocks a day.",
            checked = settings.focusEnabled,
            onCheckedChange = { on -> update { it.copy(focusEnabled = on) } },
            enabled = editable,
        )
        if (settings.focusEnabled) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    "From ${formatMinuteOfDay(settings.focusStartMinute)}",
                    onClick = { pickTime(settings.focusStartMinute) { m -> update { it.copy(focusStartMinute = m) } } },
                    enabled = editable,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    "To ${formatMinuteOfDay(settings.focusEndMinute)}",
                    onClick = { pickTime(settings.focusEndMinute) { m -> update { it.copy(focusEndMinute = m) } } },
                    enabled = editable,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.values().forEach { day ->
                    val selected = day in settings.focusDays
                    FilterChip(
                        selected = selected,
                        enabled = editable,
                        onClick = {
                            update { s -> s.copy(focusDays = if (selected) s.focusDays - day else s.focusDays + day) }
                        },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = GoColors.PanelHigh,
                            labelColor = Color.White,
                            selectedContainerColor = GoColors.Volt,
                            selectedLabelColor = GoColors.Ink,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceTestButton(name: String) {
    val context = LocalContext.current
    val voice = remember { Voice(context) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { voice.shutdown() } }
    SecondaryButton("▶  Test the voice", onClick = { voice.speak(Messages.spoken(LockKind.NUDGE, name)) })
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun GoTextField(
    value: String,
    label: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = capitalization),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GoColors.Volt,
            unfocusedBorderColor = GoColors.Stroke,
            focusedLabelColor = GoColors.Volt,
            cursorColor = GoColors.Volt,
        ),
    )
}

/** Strict mode: settings unlock for a few minutes after a 60 s wait. Shared by all settings screens. */
object StrictGate {
    var unlockedUntil by mutableLongStateOf(0L)

    fun canEdit(settings: Settings, now: Long = System.currentTimeMillis()): Boolean =
        !settings.strictMode || now < unlockedUntil
}

private const val STRICT_COUNTDOWN_MS = 60_000L
private const val STRICT_UNLOCK_WINDOW_MS = 3 * 60_000L
