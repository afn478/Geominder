package com.afn478.geominder.ui.text

import androidx.annotation.StringRes
import com.afn478.geominder.R
import com.afn478.geominder.integration.ReminderSchedulingException
import com.afn478.geominder.localization.UiText
import com.afn478.geominder.localization.resource

/**
 * Maps a failure to a localized, user-facing message. Exception text (SQLite errors, Play
 * Services status dumps) is for logs only and never reaches the screen.
 */
fun Throwable.toUserMessage(@StringRes fallback: Int): UiText =
    if (causes().any { it is ReminderSchedulingException }) {
        UiText.resource(R.string.trigger_update_failed)
    } else {
        UiText.resource(fallback)
    }

private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }
