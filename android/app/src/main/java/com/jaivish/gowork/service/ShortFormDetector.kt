package com.jaivish.gowork.service

import android.view.accessibility.AccessibilityNodeInfo
import com.jaivish.gowork.core.KnownApps

/**
 * Best-effort detection of the Reels (Instagram) and Shorts (YouTube) players.
 *
 * Two signals, either one is enough:
 *  1. A view id that only exists in the full-screen short-video player.
 *  2. The "Reels" / "Shorts" bottom tab is the selected one.
 *
 * Instagram and YouTube rename their views from time to time; when that happens add the
 * new id here (use the "Layout Inspector" or any accessibility viewer to find it).
 */
object ShortFormDetector {

    private class Rule(val viewIds: List<String>, val tabLabels: List<String>)

    private val rules = mapOf(
        KnownApps.INSTAGRAM to Rule(
            viewIds = listOf(
                "com.instagram.android:id/clips_viewer_view_pager",
                "com.instagram.android:id/clips_viewer_pager",
            ),
            tabLabels = listOf("Reels"),
        ),
        KnownApps.YOUTUBE to Rule(
            viewIds = listOf(
                "com.google.android.youtube:id/reel_recycler",
                "com.google.android.youtube:id/reel_player_page_container",
                "com.google.android.youtube:id/reel_watch_player",
            ),
            tabLabels = listOf("Shorts"),
        ),
    )

    fun supports(pkg: String): Boolean = pkg in rules

    fun isShortForm(pkg: String, root: AccessibilityNodeInfo): Boolean {
        val rule = rules[pkg] ?: return false
        for (id in rule.viewIds) {
            val nodes = safe { root.findAccessibilityNodeInfosByViewId(id) }
            if (nodes.any { it.isVisibleToUser }) return true
        }
        for (label in rule.tabLabels) {
            val nodes = safe { root.findAccessibilityNodeInfosByText(label) }
            val selectedTab = nodes.any { node ->
                node.hasExactLabel(label) && (node.isSelected || node.parent?.isSelected == true)
            }
            if (selectedTab) return true
        }
        return false
    }

    private fun AccessibilityNodeInfo.hasExactLabel(label: String): Boolean =
        text?.toString().equals(label, ignoreCase = true) ||
            contentDescription?.toString().equals(label, ignoreCase = true)

    private inline fun safe(block: () -> List<AccessibilityNodeInfo>?): List<AccessibilityNodeInfo> =
        try {
            block().orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
}
