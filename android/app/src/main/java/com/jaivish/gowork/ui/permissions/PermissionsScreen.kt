package com.jaivish.gowork.ui.permissions

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jaivish.gowork.system.PermissionState
import com.jaivish.gowork.system.Permissions
import com.jaivish.gowork.ui.components.GoCard
import com.jaivish.gowork.ui.components.PrimaryButton
import com.jaivish.gowork.ui.components.ScreenHeader
import com.jaivish.gowork.ui.components.rememberPermissionState
import com.jaivish.gowork.ui.theme.GoColors

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val state = rememberPermissionState()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GoColors.Ink)
            .systemBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("Setup", onBack) }
        item { PermissionList(state) }
    }
}

/** The four switches GoWork needs, each with a button straight to the right settings page. */
@Composable
fun PermissionList(state: PermissionState) {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Permissions.openNotificationSettings(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        PermissionCard(
            title = "1. Accessibility (required)",
            body = "Lets GoWork see when Instagram or YouTube is on screen and draw the lock over it. " +
                "Settings → Accessibility → GoWork Focus Guard → turn it on.",
            granted = state.accessibility,
            action = "Turn on",
            onAction = { Permissions.openAccessibilitySettings(context) },
        ) {
            if (!state.accessibility) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Switch greyed out or \"Restricted setting\"? Android blocks it for apps installed from an APK. " +
                        "Open App info → ⋮ (top right) → \"Allow restricted settings\", then come back and turn it on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GoColors.Volt,
                )
                TextButton(onClick = { Permissions.openAppDetails(context) }) {
                    Text("Open App info", color = GoColors.Volt)
                }
            }
        }
        PermissionCard(
            title = "2. Usage access",
            body = "Reads your real screen time so limits count every minute, even before GoWork was installed.",
            granted = state.usageAccess,
            action = "Allow",
            onAction = { Permissions.openUsageAccessSettings(context) },
        )
        PermissionCard(
            title = "3. Notifications",
            body = "Shows the \"GoWork is watching\" status with today's time.",
            granted = state.notifications,
            action = "Allow",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    Permissions.openNotificationSettings(context)
                }
            },
        )
        PermissionCard(
            title = "4. Run in background",
            body = "Stops battery saver from killing the guard. On Xiaomi / Oppo / Vivo also enable \"Autostart\" for GoWork.",
            granted = state.batteryUnrestricted,
            action = "Allow",
            onAction = { Permissions.requestIgnoreBatteryOptimizations(context) },
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    granted: Boolean,
    action: String,
    onAction: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    GoCard(borderColor = if (granted) GoColors.Mint.copy(alpha = 0.5f) else GoColors.Stroke) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (granted) GoColors.Mint else GoColors.Volt,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = GoColors.Muted)
        if (!granted) {
            Spacer(Modifier.height(12.dp))
            PrimaryButton(action, onClick = onAction)
        } else {
            Spacer(Modifier.height(4.dp))
            Text("Done ✓", style = MaterialTheme.typography.labelLarge, color = GoColors.Mint)
        }
        extra()
    }
}
