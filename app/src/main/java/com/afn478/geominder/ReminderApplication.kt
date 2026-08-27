package com.afn478.geominder

import android.app.Application
import com.afn478.geominder.alarm.AlarmEventDispatcher
import com.afn478.geominder.alarm.AlarmPermissionController
import com.afn478.geominder.alarm.AndroidExactAlarmScheduler
import com.afn478.geominder.alarm.ExactAlarmScheduler
import com.afn478.geominder.alert.AlertActionDispatcher
import com.afn478.geominder.alert.AlertDeliveryCoordinator
import com.afn478.geominder.alert.AndroidAlertDelivery
import com.afn478.geominder.backup.ReminderBackupManager
import com.afn478.geominder.backup.SafReminderBackup
import com.afn478.geominder.boot.BootFeatureRuntime
import com.afn478.geominder.boot.DefaultBootFeatureEntryPoint
import com.afn478.geominder.data.local.ReminderDatabase
import com.afn478.geominder.data.repository.RoomReminderRepository
import com.afn478.geominder.domain.repository.ReminderRepository
import com.afn478.geominder.geofence.AndroidGeofenceRegistrar
import com.afn478.geominder.geofence.AndroidReverseGeocoder
import com.afn478.geominder.geofence.CurrentLocationProvider
import com.afn478.geominder.geofence.ExactAlarmGeoVerificationScheduler
import com.afn478.geominder.geofence.FusedCurrentLocationProvider
import com.afn478.geominder.geofence.GeoEnterCoordinator
import com.afn478.geominder.geofence.GeoFeatureRuntime
import com.afn478.geominder.geofence.GeoLabelResolver
import com.afn478.geominder.geofence.AndroidNetworkAvailabilityMonitor
import com.afn478.geominder.geofence.GeoLabelRefreshCoordinator
import com.afn478.geominder.geofence.GeofenceRegistrar
import com.afn478.geominder.integration.ApplicationFeatureRuntime
import com.afn478.geominder.integration.ReminderSchedulingCoordinator
import com.afn478.geominder.localization.AndroidSystemLanguageProvider
import com.afn478.geominder.localization.AppLanguagePreferences
import com.afn478.geominder.localization.SystemLanguageProvider
import com.afn478.geominder.parser.ReminderTextParserFactory
import com.afn478.geominder.settings.AndroidSettingsPermissionStatusProvider
import com.afn478.geominder.settings.ParserKeywordTimeDefaultsProvider
import com.afn478.geominder.settings.SettingsPermissionIntentProvider
import com.afn478.geominder.settings.SettingsPermissionStatusProvider
import com.afn478.geominder.settings.SettingsRepository
import com.afn478.geominder.settings.SharedPreferencesSettingsRepository
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    val languageProvider: SystemLanguageProvider
    val parserFactory: ReminderTextParserFactory
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
    override val languageProvider: SystemLanguageProvider =
        AndroidSystemLanguageProvider(applicationContext)
    override val parserFactory: ReminderTextParserFactory =
        ReminderTextParserFactory(languageProvider)
    override val settingsRepository: SettingsRepository =
        SharedPreferencesSettingsRepository(
            context = applicationContext,
            keywordTimeDefaultsProvider = ParserKeywordTimeDefaultsProvider(parserFactory.create()),
        )
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
    override val geoLabelResolver: GeoLabelResolver = AndroidReverseGeocoder(
        context = applicationContext,
        localeProvider = { AppLanguagePreferences.locale(applicationContext) },
    )
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
