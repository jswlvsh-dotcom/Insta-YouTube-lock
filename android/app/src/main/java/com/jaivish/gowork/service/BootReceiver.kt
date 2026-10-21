package com.jaivish.gowork.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jaivish.gowork.data.Graph

/** Brings the status notification back after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val settings = Graph.settings.current
                if (settings.onboarded && settings.protectionEnabled) GuardService.start(context)
            }
        }
    }
}
