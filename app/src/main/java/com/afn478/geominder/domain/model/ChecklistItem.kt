package com.afn478.geominder.domain.model

import java.util.UUID

/** Stable identifier for a checklist item within its parent reminder. */
@JvmInline
value class ChecklistItemId(val value: String) {
    init {
        require(value.isNotBlank()) { "Checklist item ID must not be blank" }
    }

    companion object {
        fun create(): ChecklistItemId = ChecklistItemId(UUID.randomUUID().toString())
    }
}

/**
 * Plain sub-item of a reminder. Checklist text is stored verbatim: it never feeds the time or
 * location parser and never carries a trigger of its own.
 */
data class ChecklistItem(
    val id: ChecklistItemId = ChecklistItemId.create(),
    val text: String,
    val done: Boolean = false,
) {
    init {
        require(text.isNotBlank()) { "Checklist item text must not be blank" }
    }
}
