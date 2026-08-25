package com.afn478.geominder.domain.model

import java.time.Instant

/** A one-shot wall-clock event that must be scheduled as an exact alarm. */
data class TimeTrigger(
    val id: TriggerId = TriggerId.create(),
    val exactAt: Instant,
)
