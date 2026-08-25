package com.geominder.reminder.settings

/** User-facing appearance choices. Values are deliberately independent of storage strings. */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK, BLACK;

    companion object {
        /** Parses the persisted name, retaining SYSTEM as the safe default. */
        fun fromStorage(value: String?): ThemeMode =
            value?.let { runCatching { valueOf(it) }.getOrNull() } ?: SYSTEM
    }
}

enum class AccentTheme {
    DYNAMIC, SUNSET, OCEAN, FOREST, PLUM, CITRUS, ROSE, SKY, SLATE;

    companion object {
        /** Parses the persisted name, retaining DYNAMIC as the safe default. */
        fun fromStorage(value: String?): AccentTheme =
            value?.let { runCatching { valueOf(it) }.getOrNull() } ?: DYNAMIC
    }
}
