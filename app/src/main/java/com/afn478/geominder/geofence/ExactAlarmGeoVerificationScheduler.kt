package com.afn478.geominder.geofence

import com.afn478.geominder.alarm.AlarmScheduleResult
import com.afn478.geominder.alarm.ExactAlarmScheduler
import com.afn478.geominder.domain.model.ReminderId
import com.afn478.geominder.domain.model.TriggerId

/** Keeps active-from checks on the same stable alarm contract as ordinary reminder alarms. */
class ExactAlarmGeoVerificationScheduler(
    private val exactAlarmScheduler: ExactAlarmScheduler,
) : GeoVerificationScheduler {
    @Suppress("DEPRECATION") // Retained until the scheduler removes its compatibility result.
    override fun schedule(request: GeoVerificationRequest): GeoVerificationScheduleResult =
        runCatching {
            exactAlarmScheduler.scheduleGatedCheck(
                reminderId = request.reminderId,
                triggerId = request.triggerId,
                checkAt = request.verifyAt,
            )
        }.fold(
            onSuccess = { result ->
                when (result) {
                    is AlarmScheduleResult.Scheduled -> GeoVerificationScheduleResult.Scheduled
                    is AlarmScheduleResult.ExactAlarmAccessDenied ->
                        GeoVerificationScheduleResult.ExactAlarmPermissionRequired
                    is AlarmScheduleResult.NotApplicable -> GeoVerificationScheduleResult.Failed(
                        IllegalStateException("Gated verification was not scheduled: ${result.reason}"),
                    )
                }
            },
            onFailure = GeoVerificationScheduleResult::Failed,
        )

    override fun cancel(reminderId: ReminderId, triggerId: TriggerId) {
        exactAlarmScheduler.cancelGatedCheck(reminderId, triggerId)
    }
}
