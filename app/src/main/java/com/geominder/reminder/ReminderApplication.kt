package com.geominder.reminder

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.geominder.reminder.alarm.AlarmEventDispatcher
import com.geominder.reminder.alarm.AlarmPermissionController
import com.geominder.reminder.alarm.AndroidExactAlarmScheduler
import com.geominder.reminder.alarm.ExactAlarmScheduler
import com.geominder.reminder.alert.AlertActionDispatcher
import com.geominder.reminder.alert.AlertDeliveryCoordinator
import com.geominder.reminder.alert.AndroidAlertDelivery
import com.geominder.reminder.backup.ReminderBackupManager
import com.geominder.reminder.backup.SafReminderBackup
import com.geominder.reminder.boot.BootFeatureRuntime
import com.geominder.reminder.boot.DefaultBootFeatureEntryPoint
import com.geominder.reminder.data.local.ReminderDatabase
import com.geominder.reminder.data.repository.RoomReminderRepository
import com.geominder.reminder.domain.repository.ReminderRepository
import com.geominder.reminder.geofence.AndroidGeofenceRegistrar
import com.geominder.reminder.geofence.AndroidReverseGeocoder
import com.geominder.reminder.geofence.CurrentLocationProvider
import com.geominder.reminder.geofence.ExactAlarmGeoVerificationScheduler
import com.geominder.reminder.geofence.FusedCurrentLocationProvider
import com.geominder.reminder.geofence.GeoEnterCoordinator
import com.geominder.reminder.geofence.GeoFeatureRuntime
import com.geominder.reminder.geofence.GeoLabelResolver
import com.geominder.reminder.geofence.AndroidNetworkAvailabilityMonitor
import com.geominder.reminder.geofence.GeoLabelRefreshCoordinator
import com.geominder.reminder.geofence.GeofenceRegistrar
import com.geominder.reminder.integration.ApplicationFeatureRuntime
import com.geominder.reminder.integration.ReminderSchedulingCoordinator
import com.geominder.reminder.settings.AndroidSettingsPermissionStatusProvider
import com.geominder.reminder.settings.SettingsPermissionIntentProvider
import com.geominder.reminder.settings.SettingsPermissionStatusProvider
import com.geominder.reminder.settings.SettingsRepository
import com.geominder.reminder.settings.SharedPreferencesSettingsRepository
import com.google.android.gms.location.LocationServices

class ReminderApplication : Application() {
    val appContainer: AppContainer by lazy(LazyThreadSafetyMode.NONE) {
        DefaultAppContainer(this)
    }

    override fun onCreate() {
        super.onCreate()
        val container = appContainer
        AlarmEventDispatcher.install(container.featureRuntime)
        GeoFeatureRuntime.entryPoint = container.featureRuntime
        AlertActionDispatcher.install(container.featureRuntime)
        BootFeatureRuntime.entryPoint = DefaultBootFeatureEntryPoint(
            repository = container.reminderRepository,
            alarmScheduler = container.exactAlarmScheduler,
            geofenceRegistrar = container.geofenceRegistrar,
        )
        GeoLabelRefreshCoordinator(
            repository = container.reminderRepository,
            resolver = container.geoLabelResolver,
            networkMonitor = AndroidNetworkAvailabilityMonitor(this),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ).start()
    }
}

interface AppContainer {
    val reminderRepository: ReminderRepository
    val settingsRepository: SettingsRepository
    val alarmPermissionController: AlarmPermissionController
    val exactAlarmScheduler: ExactAlarmScheduler
    val geofenceRegistrar: GeofenceRegistrar
    val currentLocationProvider: CurrentLocationProvider
    val geoLabelResolver: GeoLabelResolver
    val permissionStatusProvider: SettingsPermissionStatusProvider
    val permissionIntentProvider: SettingsPermissionIntentProvider
    val schedulingCoordinator: ReminderSchedulingCoordinator
    val featureRuntime: ApplicationFeatureRuntime
    val safReminderBackup: SafReminderBackup
}

private class DefaultAppContainer(application: Application) : AppContainer {
    private val applicationContext = application.applicationContext
    private val database = ReminderDatabase.getInstance(applicationContext)

    override val reminderRepository: ReminderRepository =
        RoomReminderRepository(database.reminderDao())
    override val settingsRepository: SettingsRepository =
        SharedPreferencesSettingsRepository(applicationContext)
    override val alarmPermissionController = AlarmPermissionController(applicationContext)
    override val exactAlarmScheduler: ExactAlarmScheduler =
        AndroidExactAlarmScheduler(applicationContext, permissionController = alarmPermissionController)

    private val geofencingClient = LocationServices.getGeofencingClient(applicationContext)
    override val geofenceRegistrar: GeofenceRegistrar = AndroidGeofenceRegistrar(
        context = applicationContext,
        geofencingClient = geofencingClient,
    )
    override val currentLocationProvider: CurrentLocationProvider =
        FusedCurrentLocationProvider(applicationContext)
    override val geoLabelResolver: GeoLabelResolver = AndroidReverseGeocoder(applicationContext)
    override val permissionStatusProvider: SettingsPermissionStatusProvider =
        AndroidSettingsPermissionStatusProvider(
            context = applicationContext,
            alarmPermissionController = alarmPermissionController,
        )
    override val permissionIntentProvider = SettingsPermissionIntentProvider(
        context = applicationContext,
        alarmPermissionController = alarmPermissionController,
    )
    override val schedulingCoordinator = ReminderSchedulingCoordinator(
        exactAlarmScheduler = exactAlarmScheduler,
        geofenceRegistrar = geofenceRegistrar,
    )

    private val alertDeliveryCoordinator = AlertDeliveryCoordinator(
        repository = reminderRepository,
        alertDelivery = AndroidAlertDelivery(applicationContext),
    )
    private val geoVerificationScheduler = ExactAlarmGeoVerificationScheduler(exactAlarmScheduler)
    override val featureRuntime = ApplicationFeatureRuntime(
        repository = reminderRepository,
        exactAlarmScheduler = exactAlarmScheduler,
        geofenceRegistrar = geofenceRegistrar,
        alarmPermissionController = alarmPermissionController,
        alertCoordinator = alertDeliveryCoordinator,
        geoEnterCoordinatorFactory = { emitter ->
            GeoEnterCoordinator(
                locationProvider = currentLocationProvider,
                verificationScheduler = geoVerificationScheduler,
                triggerEmitter = emitter,
            )
        },
    )

    private val backupManager = ReminderBackupManager(
        repository = reminderRepository,
        postImportScheduler = schedulingCoordinator,
    )
    override val safReminderBackup = SafReminderBackup(
        contentResolver = applicationContext.contentResolver,
        manager = backupManager,
    )
}
