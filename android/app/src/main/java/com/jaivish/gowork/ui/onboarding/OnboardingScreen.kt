package com.jaivish.gowork.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaivish.gowork.core.KnownApps
import com.jaivish.gowork.core.Settings
import com.jaivish.gowork.data.Graph
import com.jaivish.gowork.ui.components.GoCard
import com.jaivish.gowork.ui.components.PrimaryButton
import com.jaivish.gowork.ui.components.SettingSlider
import com.jaivish.gowork.ui.components.SettingSwitch
import com.jaivish.gowork.ui.components.rememberPermissionState
import com.jaivish.gowork.ui.permissions.PermissionList
import com.jaivish.gowork.ui.theme.GoColors

private const val STEPS = 4

@Composable
fun OnboardingScreen(settings: Settings, onFinish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step -= 1 }

    Column(
        Modifier
            .fillMaxSize()
            .background(GoColors.Ink)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        StepDots(step)
        Spacer(Modifier.height(20.dp))
        Box(Modifier.weight(1f)) {
            Crossfade(targetState = step, label = "onboarding") { current ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (current) {
                        0 -> WelcomeStep()
                        1 -> AboutYouStep(settings)
                        2 -> RulesStep(settings)
                        else -> PermissionsStep()
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        when (step) {
            0 -> PrimaryButton("Let's set it up →", onClick = { step = 1 })
            STEPS - 1 -> FinishButtons(onFinish)
            else -> PrimaryButton("Next →", onClick = { step += 1 })
        }
    }
}

@Composable
private fun StepDots(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(STEPS) { index ->
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (index == step) 28.dp else 10.dp)
                    .clip(CircleShape)
                    .background(if (index <= step) GoColors.Volt else GoColors.PanelHigh),
            )
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("⚡", fontSize = 56.sp)
        Text(
            buildAnnotatedString {
                append("GO DO YOUR ")
                withStyle(SpanStyle(color = GoColors.Volt)) { append("WORK.") }
            },
            fontSize = 52.sp,
            lineHeight = 54.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
        )
        Text(
            "Instagram and YouTube are built to eat your time. GoWork watches them in the background and pulls you out.",
            style = MaterialTheme.typography.titleMedium,
            color = GoColors.Muted,
        )
        GoCard {
            Feature("⏱", "Every 2 minutes", "A full-screen \"Go do your work\" lock. Continue only after a countdown.")
            Feature("🔒", "Daily limits", "Hit your limit and the app stays locked until midnight.")
            Feature("🎬", "Reels & Shorts blocker", "Block just the doom-scroll part, keep DMs and uploads.")
            Feature("📊", "Real screen time", "See exactly where your day went, app by app.")
            Feature("🎯", "Focus hours & sprints", "Hard lock while you edit, write or study.")
        }
    }
}

@Composable
private fun Feature(icon: String, title: String, body: String) {
    Row(Modifier.padding(vertical = 8.dp)) {
        Text(icon, fontSize = 22.sp, modifier = Modifier.width(36.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(body, style = MaterialTheme.typography.bodySmall, color = GoColors.Muted)
        }
    }
}

@Composable
private fun AboutYouStep(settings: Settings) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Who are we keeping on track?", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(
            "The lock speaks to you by name and reminds you what you're working on.",
            style = MaterialTheme.typography.bodyLarge,
            color = GoColors.Muted,
        )
        OnboardingField(
            value = settings.userName,
            label = "Your name",
            placeholder = "Jaivish",
            onChange = { v -> Graph.settings.update { it.copy(userName = v) } },
            capitalization = KeyboardCapitalization.Words,
        )
        OnboardingField(
            value = settings.goal,
            label = "What should you be doing instead?",
            placeholder = "Finish editing the client video",
            onChange = { v -> Graph.settings.update { it.copy(goal = v) } },
            capitalization = KeyboardCapitalization.Sentences,
        )
    }
}

@Composable
private fun RulesStep(settings: Settings) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Your rules", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text("You can change all of this later in Settings.", style = MaterialTheme.typography.bodyLarge, color = GoColors.Muted)
        GoCard {
            SettingSlider(
                title = "\"Go do your work\" every",
                valueLabel = "${settings.nudgeIntervalMinutes} min",
                value = settings.nudgeIntervalMinutes,
                range = 1..30,
                onChange = { v -> Graph.settings.update { it.copy(nudgeIntervalMinutes = v) } },
            )
            listOf(KnownApps.INSTAGRAM, KnownApps.YOUTUBE).forEach { pkg ->
                val rule = settings.ruleFor(pkg)
                if (rule != null) {
                    SettingSlider(
                        title = "${KnownApps.fallbackLabel(pkg)} daily limit",
                        valueLabel = if (rule.dailyLimitMinutes == 0) "Off" else "${rule.dailyLimitMinutes} min",
                        value = rule.dailyLimitMinutes,
                        range = 0..240,
                        step = 5,
                        onChange = { v ->
                            Graph.settings.update { s ->
                                s.copy(rules = s.rules.map { if (it.packageName == pkg) it.copy(dailyLimitMinutes = v) else it })
                            }
                        },
                    )
                }
            }
            SettingSwitch(
                title = "Mindful pause on open",
                subtitle = "5 seconds of \"why are you here?\" before the app opens.",
                checked = settings.mindfulOpen,
                onCheckedChange = { on -> Graph.settings.update { it.copy(mindfulOpen = on) } },
            )
            SettingSwitch(
                title = "Say it out loud",
                subtitle = "A voice says \"go do your work\" with every reminder.",
                checked = settings.voiceEnabled,
                onCheckedChange = { on -> Graph.settings.update { it.copy(voiceEnabled = on) } },
            )
        }
    }
}

@Composable
private fun PermissionsStep() {
    val state = rememberPermissionState()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Give GoWork its powers", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text(
            "Android needs your OK before any app can watch other apps. Tap each one; GoWork checks automatically.",
            style = MaterialTheme.typography.bodyLarge,
            color = GoColors.Muted,
        )
        PermissionList(state)
    }
}

@Composable
private fun FinishButtons(onFinish: () -> Unit) {
    val state = rememberPermissionState()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PrimaryButton(
            if (state.coreReady) "Start protecting me" else "Turn on Accessibility first",
            onClick = onFinish,
            enabled = state.coreReady,
            color = GoColors.Mint,
        )
        if (!state.coreReady) {
            TextButton(onClick = onFinish) { Text("Skip for now", color = GoColors.Muted) }
        }
    }
}

@Composable
private fun OnboardingField(
    value: String,
    label: String,
    placeholder: String,
    onChange: (String) -> Unit,
    capitalization: KeyboardCapitalization,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = GoColors.Faint) },
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
