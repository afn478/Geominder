package com.afn478.geominder.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.afn478.geominder.R
import com.afn478.geominder.localization.UiText

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Resource -> stringResource(id, *args.toTypedArray())
    is UiText.Plain -> value
}

@Composable
fun String.resolveNearbyLabel(): String = stringResource(
    R.string.near_location,
    removeLegacyNearbyPrefix(),
)

private fun String.removeLegacyNearbyPrefix(): String = trim().replace(
    LEGACY_NEAR_PREFIX,
    "",
)

private val LEGACY_NEAR_PREFIX = Regex("(?i)^near\\s+")
