package com.jaivish.gowork.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaivish.gowork.core.KnownApps
import com.jaivish.gowork.core.Settings
import com.jaivish.gowork.core.formatMinuteOfDay
import com.jaivish.gowork.data.Graph
import com.jaivish.gowork.engine.LockActions
import com.jaivish.gowork.engine.LockKind
import com.jaivish.gowork.engine.LockRequest
import com.jaivish.gowork.engine.Messages
import com.jaivish.gowork.ui.apps.AppPickerScreen
import com.jaivish.gowork.ui.dashboard.DashboardScreen
import com.jaivish.gowork.ui.lock.LockScreen
import com.jaivish.gowork.ui.onboarding.OnboardingScreen
import com.jaivish.gowork.ui.permissions.PermissionsScreen
import com.jaivish.gowork.ui.settings.SettingsScreen

private enum class Screen { Home, Settings, Apps, Permissions }

@Composable
fun AppRoot() {
    val settings by Graph.settings.flow.collectAsStateWithLifecycle()
    var preview by remember { mutableStateOf<LockRequest?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (!settings.onboarded) {
            OnboardingScreen(settings = settings, onFinish = { Graph.settings.update { it.copy(onboarded = true) } })
        } else {
            MainNavigation(settings, onPreview = { kind -> preview = previewRequest(kind, settings) })
        }

        preview?.let { request ->
            BackHandler { preview = null }
            LockScreen(
                request = request,
                actions = object : LockActions {
                    override fun primary(request: LockRequest) {
                        preview = null
                    }

                    override fun secondary(request: LockRequest) {
                        preview = null
                    }

                    override fun back(request: LockRequest) {
                        preview = null
                    }
                },
            )
        }
    }
}

@Composable
private fun MainNavigation(settings: Settings, onPreview: (LockKind) -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
    BackHandler(enabled = screen != Screen.Home) {
        screen = if (screen == Screen.Apps) Screen.Settings else Screen.Home
    }
    Crossfade(targetState = screen, label = "screen") { target ->
        when (target) {
            Screen.Home -> DashboardScreen(
                settings = settings,
                onOpenSettings = { screen = Screen.Settings },
                onOpenPermissions = { screen = Screen.Permissions },
            )
            Screen.Settings -> SettingsScreen(
                settings = settings,
                onBack = { screen = Screen.Home },
                onAddApps = { screen = Screen.Apps },
                onPreview = onPreview,
            )
            Screen.Apps -> AppPickerScreen(settings = settings, onBack = { screen = Screen.Settings })
            Screen.Permissions -> PermissionsScreen(onBack = { screen = Screen.Home })
        }
    }
}

/** A realistic lock to show from Settings, so you know what is coming. */
private fun previewRequest(kind: LockKind, settings: Settings): LockRequest {
    val pkg = KnownApps.INSTAGRAM
    return LockRequest(
        id = -System.nanoTime(),
        kind = kind,
        packageName = pkg,
        appLabel = Graph.apps.label(pkg),
        waitSeconds = when (kind) {
            LockKind.NUDGE -> settings.nudgeWaitSeconds
            LockKind.OPEN_PAUSE -> settings.mindfulOpenSeconds
            LockKind.SHORT_FORM -> 0
            else -> 30
        },
        nudgeNumber = 3,
        intervalMinutes = settings.nudgeIntervalMinutes,
        todayUsageMs = 34 * 60_000L,
        todayOpens = 12,
        limitMinutes = settings.ruleFor(pkg)?.dailyLimitMinutes ?: 30,
        emergenciesLeft = 3,
        untilLabel = formatMinuteOfDay(settings.focusEndMinute),
        message = Messages.line(System.currentTimeMillis() / 60_000),
        userName = settings.userName.trim(),
        goal = settings.goal.trim(),
    )
}
