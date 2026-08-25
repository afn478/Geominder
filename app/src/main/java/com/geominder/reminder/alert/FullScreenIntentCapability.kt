package com.geominder.reminder.alert

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

class FullScreenIntentCapability(context: Context) {
    private val applicationContext = context.applicationContext

    fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            NotificationManagerCompat.from(applicationContext).canUseFullScreenIntent()

    /** Returns null below API 34 because there is no per-app full-screen-intent settings page. */
    fun settingsIntent(): Intent? = settingsIntent(applicationContext.packageName)

    companion object {
        fun settingsIntent(packageName: String): Intent? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
            return Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:$packageName"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
