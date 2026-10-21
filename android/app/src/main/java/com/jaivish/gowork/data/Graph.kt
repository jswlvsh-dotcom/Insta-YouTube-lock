package com.jaivish.gowork.data

import android.content.Context
import com.jaivish.gowork.system.AppInfoCache
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Tiny service locator; everything lives in one process. */
object Graph {
    private lateinit var app: Context

    lateinit var settings: SettingsRepository
        private set
    lateinit var stats: PrefsStatsRepository
        private set
    lateinit var apps: AppInfoCache
        private set

    fun init(context: Context) {
        app = context.applicationContext
        settings = SettingsRepository(app)
        stats = PrefsStatsRepository(app)
        apps = AppInfoCache(app)
    }

    /** Day GoWork was first installed; streaks never count days before it. */
    fun installDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        val firstInstall = try {
            app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
        return Instant.ofEpochMilli(firstInstall).atZone(zone).toLocalDate()
    }
}
