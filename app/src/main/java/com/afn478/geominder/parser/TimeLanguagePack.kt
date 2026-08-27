package com.afn478.geominder.parser

import com.afn478.geominder.localization.SupportedLanguage
import java.time.LocalTime
import java.util.Locale

enum class HourModifier {
    TWENTY_FOUR_HOUR,
    AM,
    PM,
    NIGHT,
}

enum class ModifierPosition {
    BEFORE_HOUR,
    AFTER_HOUR,
    NONE,
}

data class ClockPattern(
    // The regex carries the source language's word order; this metadata keeps that choice
    // explicit for future packs instead of assuming the modifier follows the hour.
    val regex: Regex,
    val priority: Int,
    val modifierPosition: ModifierPosition = ModifierPosition.NONE,
    val modifierAliases: Map<String, HourModifier> = emptyMap(),
    val hourGroup: String = "hour",
    val minuteGroup: String = "minute",
    val modifierGroup: String = "period",
    val halfGroup: String? = null,
    val defaultModifier: HourModifier = HourModifier.TWENTY_FOUR_HOUR,
)

data class RelativeDateRule(
    val regex: Regex,
    val daysFromToday: Long,
    val priority: Int = 90,
)

data class RelativeDayRule(
    val regex: Regex,
    val amountGroup: String = "amount",
    val unitGroup: String = "unit",
    val unitMultipliers: Map<String, Long>,
)

enum class DurationUnit {
    HOURS,
    MINUTES,
}

data class RelativeDurationRule(
    val regex: Regex,
    val amountGroup: String = "amount",
    val unitGroup: String = "unit",
    val unitTypes: Map<String, DurationUnit>,
)

/** All lexical rules that are safe to activate for one source language. */
data class TimeLanguagePack(
    val language: SupportedLanguage,
    val locale: Locale,
    val keywordTimes: Map<String, LocalTime>,
    val clockPatterns: List<ClockPattern>,
    val relativeDateRules: List<RelativeDateRule>,
    val relativeDayRules: List<RelativeDayRule>,
    val relativeDurationRules: List<RelativeDurationRule>,
    val nextWords: Set<String>,
    val fromPrefixes: List<Regex>,
    val fromSuffixes: List<Regex>,
)
