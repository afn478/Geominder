package com.afn478.geominder.settings

enum class ReminderSortField {
    TITLE,
    CREATION_DATE,
    MODIFICATION_DATE,
    DUE_DATE,
    ;

    companion object {
        /** Parses the persisted name, retaining due date as the safe default. */
        fun fromStorage(value: String?): ReminderSortField =
            value?.let { runCatching { valueOf(it) }.getOrNull() } ?: DUE_DATE
    }
}

enum class ReminderSortDirection {
    ASCENDING,
    DESCENDING,
    ;

    companion object {
        /** Parses the persisted name, retaining ascending as the safe default. */
        fun fromStorage(value: String?): ReminderSortDirection =
            value?.let { runCatching { valueOf(it) }.getOrNull() } ?: ASCENDING
    }
}

data class ReminderSortOrder(
    val field: ReminderSortField = ReminderSortField.DUE_DATE,
    val direction: ReminderSortDirection = ReminderSortDirection.ASCENDING,
) {
    companion object {
        val DEFAULT = ReminderSortOrder()
    }
}
