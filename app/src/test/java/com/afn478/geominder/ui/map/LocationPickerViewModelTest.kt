package com.afn478.geominder.ui.map

import com.afn478.geominder.R
import com.afn478.geominder.geofence.CancellationHandle
import com.afn478.geominder.geofence.CurrentLocationProvider
import com.afn478.geominder.geofence.GeoLabelResolver
import com.afn478.geominder.geofence.LocationFailure
import com.afn478.geominder.geofence.LocationFix
import com.afn478.geominder.geofence.LocationResult
import com.afn478.geominder.geofence.PlaceResult
import com.afn478.geominder.geofence.PlaceSearch
import com.afn478.geominder.localization.resourceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

class LocationPickerViewModelTest {
    @Test
    fun `an existing location opens framed without asking for the current position`() {
        var located = false
        val viewModel = viewModel(
            latitude = 40.7,
            longitude = -74.0,
            locationProvider = CurrentLocationProvider { located = true; CancellationHandle {} },
        )

        val request = viewModel.uiState.value.cameraRequest!!
        assertEquals(40.7, request.latitude, 0.0)
        assertTrue(request.fitRadius)
        assertFalse(request.animate)
        assertFalse(located)
    }

    @Test
    fun `without a location the picker starts at the current position`() {
        val viewModel = viewModel(
            locationProvider = CurrentLocationProvider { callback ->
                callback(LocationResult.Available(LocationFix(40.7, -74.0, 5f, Instant.EPOCH)))
                CancellationHandle {}
            },
        )

        assertEquals(40.7, viewModel.uiState.value.cameraRequest!!.latitude, 0.0)
        assertFalse(viewModel.uiState.value.isLocating)
    }

    @Test
    fun `a failed position request shows why`() {
        val viewModel = viewModel(
            locationProvider = CurrentLocationProvider { callback ->
                callback(LocationResult.Unavailable(LocationFailure.PERMISSION_REQUIRED))
                CancellationHandle {}
            },
        )

        assertEquals(R.string.location_permission_required, viewModel.uiState.value.locationError?.resourceId())
    }

    @Test
    fun `the map centre gets a resolved label once the map stops`() {
        val viewModel = viewModel(
            latitude = 40.7,
            longitude = -74.0,
            labelResolver = { lat, lon, callback -> callback("Somewhere at $lat, $lon") },
        )

        viewModel.onCameraMoveStarted()
        assertTrue(viewModel.uiState.value.isMoving)
        assertFalse(viewModel.uiState.value.canConfirm)
        viewModel.onCameraIdle(40.75, -73.99)

        val state = viewModel.uiState.value
        assertEquals("Somewhere at 40.75, -73.99", state.label)
        assertTrue(state.canConfirm)
        assertEquals(PickedLocation(40.75, -73.99, 150.0, "Somewhere at 40.75, -73.99"), viewModel.result())
    }

    @Test
    fun `a camera settle without moving the centre does not look the address up again`() {
        var lookups = 0
        val viewModel = viewModel(
            latitude = 55.75,
            longitude = 37.61,
            labelResolver = { _, _, callback -> lookups++; callback("A long address") },
        )
        viewModel.onCameraIdle(55.75, 37.61)

        // A padding change: the map reports a move and an idle, but the centre stays put.
        viewModel.onCameraMoveStarted()
        viewModel.onCameraIdle(55.750000001, 37.610000001)

        assertEquals(1, lookups)
        assertEquals("A long address", viewModel.uiState.value.label)
        assertFalse(viewModel.uiState.value.isMoving)
    }

    @Test
    fun `a chosen search result names the place instead of a looked-up address`() {
        val place = PlaceResult("Penn Station", "7th Avenue, New York", 40.7506, -73.9935)
        val viewModel = viewModel(
            latitude = 40.7,
            longitude = -74.0,
            placeSearch = { _, _, _ -> listOf(place) },
            labelResolver = { _, _, _ -> error("the search result already names the place") },
        )

        viewModel.onQueryChange("penn")
        viewModel.onSearch()
        assertEquals(listOf(place), viewModel.uiState.value.results)

        viewModel.onResultSelected(place)
        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertTrue(viewModel.uiState.value.cameraRequest!!.animate)
        viewModel.onCameraIdle(40.75061, -73.99351)

        assertEquals("Penn Station, 7th Avenue, New York", viewModel.uiState.value.label)
    }

    @Test
    fun `search reports empty results and an unreachable service differently`() {
        val empty = viewModel(latitude = 1.0, longitude = 1.0, placeSearch = { _, _, _ -> emptyList() })
        empty.onQueryChange("nowhere")
        empty.onSearch()
        assertEquals(R.string.no_places_found, empty.uiState.value.searchMessage?.resourceId())

        val offline = viewModel(latitude = 1.0, longitude = 1.0, placeSearch = { _, _, _ -> throw IOException() })
        offline.onQueryChange("anywhere")
        offline.onSearch()
        assertEquals(R.string.place_search_unavailable, offline.uiState.value.searchMessage?.resourceId())

        offline.clearSearch()
        assertNull(offline.uiState.value.searchMessage)
    }

    @Test
    fun `a custom radius stays selectable among the steps`() {
        val viewModel = viewModel(latitude = 1.0, longitude = 1.0, radius = 120.0)

        assertTrue(120.0 in viewModel.uiState.value.radiusSteps)
        viewModel.onRadiusChange(500.0)
        assertEquals(500.0, viewModel.uiState.value.radiusMeters, 0.0)
    }

    private fun viewModel(
        latitude: Double? = null,
        longitude: Double? = null,
        radius: Double = 150.0,
        placeSearch: PlaceSearch = PlaceSearch { _, _, _ -> emptyList() },
        labelResolver: GeoLabelResolver = GeoLabelResolver { _, _, callback -> callback("label") },
        locationProvider: CurrentLocationProvider = CurrentLocationProvider { CancellationHandle {} },
    ) = LocationPickerViewModel(
        initialLatitude = latitude,
        initialLongitude = longitude,
        initialRadiusMeters = radius,
        placeSearch = placeSearch,
        labelResolver = labelResolver,
        locationProvider = locationProvider,
        ioDispatcher = Dispatchers.Unconfined,
        injectedScope = CoroutineScope(Dispatchers.Unconfined),
    )
}
