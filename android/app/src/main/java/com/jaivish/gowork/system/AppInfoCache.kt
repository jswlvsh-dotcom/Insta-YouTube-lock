package com.jaivish.gowork.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.jaivish.gowork.core.KnownApps
import java.util.concurrent.ConcurrentHashMap

/** Labels and icons of installed apps, cached. */
class AppInfoCache(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val labels = ConcurrentHashMap<String, String>()
    private val icons = ConcurrentHashMap<String, ImageBitmap>()

    fun label(pkg: String): String = labels.getOrPut(pkg) {
        try {
            @Suppress("DEPRECATION")
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            KnownApps.fallbackLabel(pkg)
        }
    }

    /** Loads (and caches) an app icon. Call off the main thread the first time. */
    fun icon(pkg: String): ImageBitmap? {
        icons[pkg]?.let { return it }
        return try {
            val bitmap = pm.getApplicationIcon(pkg).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
            icons[pkg] = bitmap
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    fun cachedIcon(pkg: String): ImageBitmap? = icons[pkg]

    fun isInstalled(pkg: String): Boolean = try {
        @Suppress("DEPRECATION")
        pm.getApplicationInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    /** Every app with a launcher icon, except GoWork itself. */
    fun launchablePackages(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = pm.queryIntentActivities(intent, 0)
        return activities.map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .distinct()
    }

    /** Home-screen apps; their time is not "screen time" in the top-apps list. */
    fun launcherPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
    }

    private companion object {
        const val ICON_PX = 96
    }
}
