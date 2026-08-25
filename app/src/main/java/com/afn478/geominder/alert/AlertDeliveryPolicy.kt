package com.afn478.geominder.alert

enum class AlertPresentation {
    FULL_SCREEN,
    NORMAL_NOTIFICATION,
}

data class AlertCapabilities(
    val canUseFullScreenIntent: Boolean,
    val hasExactAlarmAccess: Boolean = true,
)

/** Pure policy kept separate from Android services so the safety fallback is directly testable. */
object AlertDeliveryPolicy {
    fun choosePresentation(
        isLockedAndNonInteractive: Boolean,
        capabilities: AlertCapabilities,
    ): AlertPresentation {
        return if (
            isLockedAndNonInteractive &&
            capabilities.canUseFullScreenIntent &&
            capabilities.hasExactAlarmAccess
        ) {
            AlertPresentation.FULL_SCREEN
        } else {
            AlertPresentation.NORMAL_NOTIFICATION
        }
    }
}
