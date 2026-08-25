package com.afn478.geominder.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restores AlarmManager and Play Services registrations without starting a service. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return

        val pendingResult = goAsync()
        BootResyncLauncher.launch(BootFeatureRuntime.entryPoint) {
            pendingResult.finish()
        }
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
