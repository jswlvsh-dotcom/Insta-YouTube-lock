package com.jaivish.gowork

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.jaivish.gowork.data.Graph

class GoWorkApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_GUARD, getString(R.string.channel_guard), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.channel_guard_desc)
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val CHANNEL_GUARD = "guard"
    }
}
