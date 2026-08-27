package com.afn478.geominder.localization

import androidx.annotation.StringRes

/** A presentation string that can stay resource-backed until it reaches Compose. */
sealed interface UiText {
    data class Resource(
        @StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    data class Plain(val value: String) : UiText

    companion object
}

fun UiText.Companion.resource(@StringRes id: Int, vararg args: Any): UiText =
    UiText.Resource(id, args.toList())
