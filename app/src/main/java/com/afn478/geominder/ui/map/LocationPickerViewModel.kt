package com.afn478.geominder.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.afn478.geominder.R
import com.afn478.geominder.geofence.CancellationHandle
import com.afn478.geominder.geofence.CurrentLocationProvider
import com.afn478.geominder.geofence.GeoLabelResolver
import com.afn478.geominder.geofence.HaversineDistance
import com.afn478.geominder.geofence.LocationFailure
import com.afn478.geominder.geofence.LocationResult
import com.afn478.geominder.geofence.PlaceResult
import com.afn478.geominder.geofence.PlaceSearch
import com.afn478.geominder.localization.UiText
import com.afn478.geominder.localization.resource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Where the map should move next; [id] makes repeated moves to one place distinct. */
data class CameraRequest(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    /** Frame the trigger radius; otherwise keep the current zoom. */
    val fitRadius: Boolean,
    val animate: Boolean,
)

data class LocationPickerUiState(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val radiusMeters: Double,
    val label: String? = null,
    val isResolvingLabel: Boolean = false,
    val isMoving: Boolean = false,
    val query: String = "",
    val results: List<PlaceResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchMessage: UiText? = null,
    val isLocating: Boolean = false,
    val locationError: UiText? = null,
    val mapLoadFailed: Boolean = false,
    val cameraRequest: CameraRequest? = null,
) {
    val radiusSteps: List<Double>
        get() = (RADIUS_STEPS + radiusMeters).distinct().sorted()

    val canConfirm: Boolean
        get() = latitude != null && longitude != null && !isMoving

    companion object {
        val RADIUS_STEPS = listOf(
            50.0, 75.0, 100.0, 150.0, 200.0, 300.0, 400.0, 500.0, 750.0,
            1_000.0, 1_500.0, 2_000.0, 3_000.0, 5_000.0,
        )
    }
}

/**
 * State for the full-screen location picker: the map centre is the chosen place, search moves
 * the map, and the label follows the centre once the map stops moving.
 */
class LocationPickerViewModel(
    initialLatitude: Double?,
    initialLongitude: Double?,
    initialRadiusMeters: Double,
    private val placeSearch: PlaceSearch,
    private val labelResolver: GeoLabelResolver,
    private val locationProvider: CurrentLocationProvider,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val injectedScope: CoroutineScope? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LocationPickerUiState(radiusMeters = initialRadiusMeters))
    val uiState: StateFlow<LocationPickerUiState> = _uiState.asStateFlow()

    private var nextCameraRequestId = 0L
    private var labelVersion = 0L
    private var searchJob: Job? = null
    private var locationRequest: CancellationHandle? = null
    /** A search hit the map is moving to; its name beats a reverse-geocoded address. */
    private var selectedPlace: PlaceResult? = null

    init {
        if (initialLatitude != null && initialLongitude != null) {
            requestCamera(initialLatitude, initialLongitude, fitRadius = true, animate = false)
        } else {
            locateMe()
        }
    }

    fun onCameraMoveStarted() {
        _uiState.update { it.copy(isMoving = true) }
    }

    fun onCameraIdle(latitude: Double, longitude: Double) {
        val current = _uiState.value
        val sameSpot = selectedPlace == null && current.latitude != null && current.longitude != null &&
            HaversineDistance.meters(current.latitude, current.longitude, latitude, longitude) < SAME_SPOT_METERS
        if (sameSpot) {
            // Camera padding and layout changes report a "move" without moving the centre. Looking
            // the address up again would change the panel, which moves the camera again: a loop.
            _uiState.update { it.copy(isMoving = false) }
            return
        }
        val version = ++labelVersion
        val place = selectedPlace?.takeIf {
            HaversineDistance.meters(it.latitude, it.longitude, latitude, longitude) < PLACE_MATCH_METERS
        }
        selectedPlace = null
        _uiState.update {
            it.copy(
                latitude = latitude,
                longitude = longitude,
                isMoving = false,
                label = place?.let(::placeLabel),
                isResolvingLabel = place == null,
            )
        }
        if (place != null) return
        runCatching {
            labelResolver.resolve(latitude, longitude) { label ->
                if (version != labelVersion) return@resolve
                _uiState.update { it.copy(label = label.takeIf(String::isNotBlank), isResolvingLabel = false) }
            }
        }.onFailure {
            _uiState.update { it.copy(isResolvingLabel = false) }
        }
    }

    fun onRadiusChange(radiusMeters: Double) {
        if (radiusMeters.isFinite() && radiusMeters > 0.0) {
            _uiState.update { it.copy(radiusMeters = radiusMeters) }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, searchMessage = null) }
        searchJob?.cancel()
        if (query.trim().length < MIN_QUERY_LENGTH) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
            return
        }
        // Search as you type, but only once typing pauses: the open geocoder is a shared service.
        searchJob = workScope().launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            search(query)
        }
    }

    fun onSearch() {
        val query = _uiState.value.query
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = workScope().launch { search(query) }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(query = "", results = emptyList(), isSearching = false, searchMessage = null) }
    }

    fun onResultSelected(place: PlaceResult) {
        searchJob?.cancel()
        selectedPlace = place
        _uiState.update { it.copy(results = emptyList(), isSearching = false, searchMessage = null) }
        requestCamera(place.latitude, place.longitude, fitRadius = true, animate = true)
    }

    fun locateMe() {
        locationRequest?.cancel()
        _uiState.update { it.copy(isLocating = true, locationError = null) }
        locationRequest = runCatching {
            locationProvider.locate { result ->
                locationRequest = null
                when (result) {
                    is LocationResult.Available -> {
                        _uiState.update { it.copy(isLocating = false) }
                        requestCamera(
                            result.fix.latitude,
                            result.fix.longitude,
                            fitRadius = true,
                            animate = _uiState.value.latitude != null,
                        )
                    }
                    is LocationResult.Unavailable -> _uiState.update {
                        it.copy(isLocating = false, locationError = result.reason.userMessage())
                    }
                }
            }
        }.getOrElse {
            _uiState.update {
                it.copy(isLocating = false, locationError = LocationFailure.REQUEST_FAILED.userMessage())
            }
            null
        }
    }

    fun onMapLoadFailed() {
        _uiState.update { it.copy(mapLoadFailed = true) }
    }

    fun onCameraRequestHandled(id: Long) {
        _uiState.update { state ->
            if (state.cameraRequest?.id == id) state.copy(cameraRequest = null) else state
        }
    }

    fun result(): PickedLocation? {
        val state = _uiState.value
        val latitude = state.latitude ?: return null
        val longitude = state.longitude ?: return null
        return PickedLocation(latitude, longitude, state.radiusMeters, state.label)
    }

    override fun onCleared() {
        locationRequest?.cancel()
        super.onCleared()
    }

    private suspend fun search(query: String) {
        _uiState.update { it.copy(isSearching = true, searchMessage = null) }
        val state = _uiState.value
        val outcome = runCatching {
            withContext(ioDispatcher) { placeSearch.search(query, state.latitude, state.longitude) }
        }
        if (_uiState.value.query != query) return
        _uiState.update {
            val results = outcome.getOrNull().orEmpty()
            it.copy(
                isSearching = false,
                results = results,
                searchMessage = when {
                    outcome.exceptionOrNull() is IOException -> UiText.resource(R.string.place_search_unavailable)
                    outcome.isFailure -> UiText.resource(R.string.place_search_unavailable)
                    results.isEmpty() -> UiText.resource(R.string.no_places_found)
                    else -> null
                },
            )
        }
    }

    private fun requestCamera(latitude: Double, longitude: Double, fitRadius: Boolean, animate: Boolean) {
        _uiState.update {
            it.copy(
                cameraRequest = CameraRequest(++nextCameraRequestId, latitude, longitude, fitRadius, animate),
            )
        }
    }

    private fun placeLabel(place: PlaceResult): String =
        listOfNotNull(place.name, place.detail).distinct().joinToString(", ")

    private fun workScope(): CoroutineScope = injectedScope ?: viewModelScope

    private fun LocationFailure.userMessage(): UiText = UiText.resource(
        when (this) {
            LocationFailure.PERMISSION_REQUIRED -> R.string.location_permission_required
            LocationFailure.BACKGROUND_PERMISSION_REQUIRED -> R.string.background_location_permission_required
            LocationFailure.LOCATION_DISABLED -> R.string.location_services_disabled
            LocationFailure.NO_LOCATION -> R.string.no_current_location
            LocationFailure.REQUEST_FAILED -> R.string.location_request_failed
        },
    )

    private companion object {
        const val MIN_QUERY_LENGTH = 3
        const val SEARCH_DEBOUNCE_MILLIS = 450L
        const val PLACE_MATCH_METERS = 25.0
        const val SAME_SPOT_METERS = 0.5
    }
}

class LocationPickerViewModelFactory(
    private val initialLatitude: Double?,
    private val initialLongitude: Double?,
    private val initialRadiusMeters: Double,
    private val placeSearch: PlaceSearch,
    private val labelResolver: GeoLabelResolver,
    private val locationProvider: CurrentLocationProvider,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(LocationPickerViewModel::class.java)) {
            "Unsupported ViewModel class: ${modelClass.name}"
        }
        return LocationPickerViewModel(
            initialLatitude = initialLatitude,
            initialLongitude = initialLongitude,
            initialRadiusMeters = initialRadiusMeters,
            placeSearch = placeSearch,
            labelResolver = labelResolver,
            locationProvider = locationProvider,
        ) as T
    }
}
