package com.afn478.geominder.ui.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min

/**
 * OpenFreeMap vector styles: free, keyless OpenStreetMap tiles. MapLibre shows the required
 * OpenStreetMap/OpenMapTiles attribution itself.
 */
private const val LIGHT_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val DARK_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"

/**
 * A MapLibre map bound to the composition's lifecycle. [interactive] maps pan and zoom;
 * previews are static and leave touches to whatever is drawn above them. Both draw into a
 * TextureView so they fade, scroll and clip like ordinary views.
 */
@Composable
internal fun MapLibreView(
    interactive: Boolean,
    onMapReady: (MapLibreMap) -> Unit,
    modifier: Modifier = Modifier,
    onLoadFailed: () -> Unit = {},
) {
    val context = LocalContext.current
    val darkMap = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val styleUrl = if (darkMap) DARK_STYLE_URL else LIGHT_STYLE_URL
    val currentOnMapReady by rememberUpdatedState(onMapReady)
    val currentOnLoadFailed by rememberUpdatedState(onLoadFailed)
    val density = LocalDensity.current
    val mapView = remember {
        MapLibre.getInstance(context)
        // MapLibre leaves room for its logo before the attribution button; without the logo,
        // that gap would strand the button near the middle of small maps.
        val attributionInset = with(density) { 12.dp.roundToPx() }
        val options = MapLibreMapOptions.createFromAttributes(context)
            // A SurfaceView is a separate window layer that ignores Compose's alpha, so navigation
            // fades would cut the map out instantly while the controls drawn over it fade away.
            .textureMode(true)
            .compassEnabled(false)
            .logoEnabled(false)
            .attributionMargins(intArrayOf(attributionInset, 0, 0, attributionInset))
            .rotateGesturesEnabled(false)
            .tiltGesturesEnabled(false)
            .apply {
                if (!interactive) {
                    scrollGesturesEnabled(false)
                    zoomGesturesEnabled(false)
                    doubleTapGesturesEnabled(false)
                    quickZoomGesturesEnabled(false)
                }
            }
        MapView(context, options).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> if (!resumed) { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> if (resumed) { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> if (started) { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        val failure = MapView.OnDidFailLoadingMapListener { currentOnLoadFailed() }
        mapView.addOnDidFailLoadingMapListener(failure)
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.removeOnDidFailLoadingMapListener(failure)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
    DisposableEffect(mapView) {
        mapView.getMapAsync { ready ->
            map = ready
            currentOnMapReady(ready)
        }
        onDispose { map = null }
    }
    LaunchedEffect(map, styleUrl) {
        map?.setStyle(styleUrl)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/**
 * The zoom at which a circle of [radiusMeters] fills [fraction] of the shorter side of a map
 * [widthDp] × [heightDp] in size. MapLibre measures in 512-pixel tiles of density-independent
 * pixels.
 */
internal fun zoomForRadius(
    radiusMeters: Double,
    latitude: Double,
    widthDp: Float,
    heightDp: Float,
    fraction: Double = 0.6,
): Double {
    val radiusDp = fraction * min(widthDp, heightDp) / 2.0
    if (radiusDp <= 0.0 || radiusMeters <= 0.0) return DEFAULT_ZOOM
    val metersPerDp = radiusMeters / radiusDp
    val zoom = ln(EARTH_CIRCUMFERENCE_METERS * cos(Math.toRadians(latitude)) / (512.0 * metersPerDp)) / ln(2.0)
    return zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
}

internal const val WORLD_ZOOM = 1.0
private const val DEFAULT_ZOOM = 15.0
private const val MIN_ZOOM = 3.0
private const val MAX_ZOOM = 18.5
private const val EARTH_CIRCUMFERENCE_METERS = 40_075_016.686
