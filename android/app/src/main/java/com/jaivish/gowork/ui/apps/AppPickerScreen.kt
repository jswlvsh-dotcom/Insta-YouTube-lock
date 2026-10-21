package com.jaivish.gowork.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jaivish.gowork.core.AppRule
import com.jaivish.gowork.core.KnownApps
import com.jaivish.gowork.core.Settings
import com.jaivish.gowork.data.Graph
import com.jaivish.gowork.ui.components.AppIcon
import com.jaivish.gowork.ui.components.ScreenHeader
import com.jaivish.gowork.ui.components.SectionLabel
import com.jaivish.gowork.ui.components.rememberNow
import com.jaivish.gowork.ui.settings.StrictGate
import com.jaivish.gowork.ui.theme.GoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PickerApp(val pkg: String, val label: String, val suggested: Boolean)

@Composable
fun AppPickerScreen(settings: Settings, onBack: () -> Unit) {
    val apps by produceState<List<PickerApp>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val suggested = KnownApps.suggestions.toSet()
            Graph.apps.launchablePackages()
                .map { PickerApp(it, Graph.apps.label(it), it in suggested) }
                .sortedWith(compareByDescending<PickerApp> { it.suggested }.thenBy { it.label.lowercase() })
        }
    }
    var query by remember { mutableStateOf("") }
    val now by rememberNow()
    val editable = StrictGate.canEdit(settings, now)
    val watched = settings.rules.filter { it.enabled }.map { it.packageName }.toSet()

    Box(
        Modifier
            .fillMaxSize()
            .background(GoColors.Ink)
            .systemBarsPadding(),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ScreenHeader("Choose apps to watch", onBack) }
            if (!editable) {
                item {
                    Text(
                        "Strict mode is on. Unlock settings first to change your apps.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GoColors.Volt,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoColors.Volt,
                        unfocusedBorderColor = GoColors.Stroke,
                        focusedLabelColor = GoColors.Volt,
                        cursorColor = GoColors.Volt,
                    ),
                )
            }
            val list = apps
            if (list == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GoColors.Volt)
                    }
                }
            } else {
                val filtered = list.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                item { SectionLabel("Time-wasters first", modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
                items(filtered, key = { it.pkg }) { app ->
                    val checked = app.pkg in watched
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (checked) GoColors.PanelHigh else GoColors.Panel)
                            .clickable(enabled = editable) { toggle(app.pkg, !checked) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        AppIcon(app.pkg, size = 36.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            app.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { toggle(app.pkg, it) },
                            enabled = editable,
                            colors = CheckboxDefaults.colors(checkedColor = GoColors.Volt, checkmarkColor = GoColors.Ink),
                        )
                    }
                }
            }
        }
    }
}

private fun toggle(pkg: String, watch: Boolean) {
    Graph.settings.update { s ->
        val exists = s.rules.any { it.packageName == pkg }
        when {
            watch && !exists -> s.copy(rules = s.rules + AppRule(pkg, dailyLimitMinutes = 30))
            watch -> s.copy(rules = s.rules.map { if (it.packageName == pkg) it.copy(enabled = true) else it })
            pkg == KnownApps.INSTAGRAM || pkg == KnownApps.YOUTUBE ->
                s.copy(rules = s.rules.map { if (it.packageName == pkg) it.copy(enabled = false) else it })
            else -> s.copy(rules = s.rules.filterNot { it.packageName == pkg })
        }
    }
}
