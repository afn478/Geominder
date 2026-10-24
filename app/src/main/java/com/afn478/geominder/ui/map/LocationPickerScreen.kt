package com.afn478.geominder.ui.map

import android.graphics.PointF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afn478.geominder.R
import com.afn478.geominder.geofence.GeofenceSpec
import com.afn478.geominder.geofence.PlaceResult
import com.afn478.geominder.ui.add.SupportingError
import com.afn478.geominder.ui.text.resolve
import java.text.NumberFormat
import java.util.Locale
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun LocationPickerRoute(
    viewModel: LocationPickerViewModel,
    onBack: () -> Unit,
    onConfirm: (PickedLocation) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LocationPickerScreen(
        state = state,
        onBack = onBack,
        onConfirm = { viewModel.result()?.let(onConfirm) },
        onCameraMoveStarted = viewModel::onCameraMoveStarted,
        onCameraIdle = viewModel::onCameraIdle,
        onCameraRequestHandled = viewModel::onCameraRequestHandled,
        onRadiusChange = viewModel::onRadiusChange,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::onSearch,
        onClearSearch = viewModel::clearSearch,
        onResultSelected = viewModel::onResultSelected,
        onLocateMe = viewModel::locateMe,
        onMapLoadFailed = viewModel::onMapLoadFailed,
    )
}

/**
 * Full-screen place picker: drag the map under the fixed centre pin, or search for a place, then
 * set the trigger radius and confirm.
 */
@Composable
fun LocationPickerScreen(
    state: LocationPickerUiState,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onCameraMoveStarted: () -> Unit,
    onCameraIdle: (Double, Double) -> Unit,
    onCameraRequestHandled: (Long) -> Unit,
    onRadiusChange: (Double) -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onResultSelected: (PlaceResult) -> Unit,
    onLocateMe: () -> Unit,
    onMapLoadFailed: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var metersPerDp by remember { mutableStateOf<Double?>(null) }
    var panelHeightPx by remember { mutableIntStateOf(0) }
    val currentOnMoveStarted by rememberUpdatedState(onCameraMoveStarted)
    val currentOnIdle by rememberUpdatedState(onCameraIdle)

    // The map runs underneath the panel so its rounded corners show map, not a backdrop. Camera
    // padding keeps the map's centre (the chosen place) in the middle of the part left visible.
    LaunchedEffect(map, panelHeightPx) {
        val ready = map ?: return@LaunchedEffect
        ready.moveCamera(CameraUpdateFactory.paddingTo(0.0, 0.0, 0.0, panelHeightPx.toDouble()))
        // Lined up with the back arrow above it and the location button opposite.
        with(density) {
            ready.uiSettings.setAttributionMargins(
                ATTRIBUTION_START_MARGIN.roundToPx(),
                0,
                0,
                panelHeightPx + ATTRIBUTION_BOTTOM_MARGIN.roundToPx(),
            )
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize()) {
            MapLibreView(
                interactive = true,
                onMapReady = { ready ->
                    map = ready
                    if (state.cameraRequest == null && state.latitude == null) {
                        ready.moveCamera(CameraUpdateFactory.zoomTo(WORLD_ZOOM))
                    }
                    ready.addOnCameraMoveStartedListener {
                        currentOnMoveStarted()
                        focusManager.clearFocus()
                    }
                    ready.addOnCameraMoveListener {
                        val target = ready.cameraPosition.target ?: return@addOnCameraMoveListener
                        metersPerDp = ready.projection.getMetersPerPixelAtLatitude(target.latitude)
                    }
                    ready.addOnCameraIdleListener {
                        val target = ready.cameraPosition.target?.wrap() ?: return@addOnCameraIdleListener
                        metersPerDp = ready.projection.getMetersPerPixelAtLatitude(target.latitude)
                        currentOnIdle(target.latitude, target.longitude)
                    }
                },
                onLoadFailed = onMapLoadFailed,
                modifier = Modifier.fillMaxSize(),
            )

            // Everything drawn over the map lives in its visible part, above the panel.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = with(density) { panelHeightPx.toDp() }),
            ) {
                val widthDp = maxWidth.value
                val heightDp = maxHeight.value

                LaunchedEffect(map, state.cameraRequest) {
                    val ready = map ?: return@LaunchedEffect
                    val request = state.cameraRequest ?: return@LaunchedEffect
                    val target = LatLng(request.latitude, request.longitude)
                    val zoom = if (request.fitRadius) {
                        zoomForRadius(state.radiusMeters, request.latitude, widthDp, heightDp, fraction = 0.5)
                    } else {
                        ready.cameraPosition.zoom
                    }
                    val update = CameraUpdateFactory.newLatLngZoom(target, zoom)
                    if (request.animate) ready.animateCamera(update, CAMERA_ANIMATION_MILLIS) else ready.moveCamera(update)
                    onCameraRequestHandled(request.id)
                }

                if (state.latitude != null) {
                    val haptics = LocalHapticFeedback.current
                    val scope = rememberCoroutineScope()
                    var pinOffset by remember { mutableStateOf(Offset.Zero) }
                    var dragging by remember { mutableStateOf(false) }
                    var settle by remember { mutableStateOf<Job?>(null) }
                    // Dragging the pin moves only the pin; on release the map glides the chosen
                    // spot back under the centre, so it ends exactly as panning the map would.
                    val pinDrag = Modifier.pointerInput(map, widthDp, heightDp) {
                        val ready = map ?: return@pointerInput
                        val mapWidthPx = with(density) { widthDp.dp.toPx() }
                        val mapHeightPx = with(density) { heightDp.dp.toPx() }
                        fun recentre() {
                            dragging = false
                            val tip = PointF(mapWidthPx / 2f + pinOffset.x, mapHeightPx / 2f + pinOffset.y)
                            ready.animateCamera(
                                CameraUpdateFactory.newLatLng(ready.projection.fromScreenLocation(tip)),
                                PIN_SETTLE_MILLIS,
                            )
                            settle = scope.launch {
                                Animatable(pinOffset, Offset.VectorConverter).animateTo(
                                    Offset.Zero,
                                    tween(PIN_SETTLE_MILLIS, easing = FastOutSlowInEasing),
                                ) { pinOffset = value }
                            }
                        }
                        detectDragGestures(
                            onDragStart = {
                                settle?.cancel()
                                dragging = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                focusManager.clearFocus()
                                currentOnMoveStarted()
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                // Keep the pin on the map, clear of its edges.
                                val inset = with(density) { 24.dp.toPx() }
                                val halfWidth = mapWidthPx / 2f - inset
                                val halfHeight = mapHeightPx / 2f - inset
                                pinOffset = Offset(
                                    (pinOffset.x + amount.x).coerceIn(-halfWidth, halfWidth),
                                    (pinOffset.y + amount.y).coerceIn(-halfHeight, halfHeight),
                                )
                            },
                            onDragEnd = ::recentre,
                            onDragCancel = ::recentre,
                        )
                    }
                    LocationMarker(
                        radiusDp = metersPerDp?.let { (state.radiusMeters / it).toFloat().dp },
                        lifted = state.isMoving || dragging,
                        offset = { pinOffset },
                        pinModifier = pinDrag,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchField(
                        query = state.query,
                        isSearching = state.isSearching,
                        onBack = onBack,
                        onQueryChange = onQueryChange,
                        onSearch = {
                            focusManager.clearFocus()
                            onSearch()
                        },
                        onClear = onClearSearch,
                    )
                    AnimatedVisibility(visible = state.results.isNotEmpty() || state.searchMessage != null) {
                        SearchResults(
                            results = state.results,
                            message = state.searchMessage?.resolve(),
                            onResultSelected = { place ->
                                focusManager.clearFocus()
                                onResultSelected(place)
                            },
                        )
                    }
                    if (state.mapLoadFailed) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                        ) {
                            Text(
                                stringResource(R.string.map_load_failed),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
                }

                val locatingDescription = stringResource(R.string.locating_current_position)
                SmallFloatingActionButton(
                    onClick = onLocateMe,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        // Clear of MapLibre's attribution button in the bottom-left corner.
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    if (state.isLocating) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .semantics { contentDescription = locatingDescription },
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = stringResource(R.string.locate_current_position),
                        )
                    }
                }

                // Frame the circle again once the radius is chosen, if it no longer fits.
                LaunchedEffect(map, state.radiusMeters) {
                    val ready = map ?: return@LaunchedEffect
                    val target = ready.cameraPosition.target ?: return@LaunchedEffect
                    val perDp = metersPerDp ?: return@LaunchedEffect
                    val radiusDp = state.radiusMeters / perDp
                    val shortSide = minOf(widthDp, heightDp)
                    if (radiusDp > shortSide * 0.42 || radiusDp < shortSide * 0.06) {
                        val zoom = zoomForRadius(state.radiusMeters, target.latitude, widthDp, heightDp, fraction = 0.5)
                        ready.animateCamera(CameraUpdateFactory.zoomTo(zoom), CAMERA_ANIMATION_MILLIS)
                    }
                }
            }

            PickerPanel(
                state = state,
                onRadiusChange = onRadiusChange,
                onConfirm = onConfirm,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { panelHeightPx = it.height },
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    isSearching: Boolean,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            val placeholder = stringResource(R.string.search_places)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = placeholder },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            when {
                isSearching -> CircularProgressIndicator(
                    modifier = Modifier
                        .padding(14.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp,
                )
                query.isNotEmpty() -> IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    results: List<PlaceResult>,
    message: String?,
    onResultSelected: (PlaceResult) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (results.isEmpty()) {
            Text(
                message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
            return@Surface
        }
        LazyColumn(Modifier.heightIn(max = 320.dp)) {
            items(results) { place ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onResultSelected(place) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        Icons.Outlined.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            place.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        place.detail?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (place != results.last()) HorizontalDivider(Modifier.padding(start = 56.dp))
            }
        }
    }
}

@Composable
private fun PickerPanel(
    state: LocationPickerUiState,
    onRadiusChange: (Double) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val finding = state.isMoving || state.isResolvingLabel || state.latitude == null
            Text(
                text = if (finding) stringResource(R.string.finding_address) else state.label ?: coordinatesText(state),
                style = MaterialTheme.typography.titleMedium,
                color = if (finding) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                // A fixed height: the panel's size sets the map padding, so a label that changed
                // the line count would shift the map with every lookup.
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = coordinatesText(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.locationError?.let { SupportingError(it, Modifier.padding(top = 4.dp)) }

            val steps = state.radiusSteps
            val index = steps.indexOf(state.radiusMeters).coerceAtLeast(0)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.radius),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatDistance(state.radiusMeters),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = index.toFloat(),
                onValueChange = { value ->
                    steps.getOrNull(value.roundToInt())?.let(onRadiusChange)
                },
                valueRange = 0f..(steps.size - 1).toFloat(),
                steps = (steps.size - 2).coerceAtLeast(0),
            )
            smallRadiusHint(state.radiusMeters)?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onConfirm,
                enabled = state.canConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                Text(stringResource(R.string.use_this_location))
            }
        }
    }
}

@Composable
private fun coordinatesText(state: LocationPickerUiState): String {
    val latitude = state.latitude ?: return ""
    val longitude = state.longitude ?: return ""
    return String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
}

/** A caution for radiuses too small to be detected reliably, or null when the radius is fine. */
@Composable
internal fun smallRadiusHint(radiusMeters: Double?): String? =
    radiusMeters?.takeIf { it > 0.0 && it < GeofenceSpec.RELIABLE_RADIUS_METERS }?.let {
        stringResource(R.string.small_radius_hint, formatDistance(GeofenceSpec.RELIABLE_RADIUS_METERS))
    }

/** "150 m" or "1.5 km", in the user's number format. */
@Composable
internal fun formatDistance(meters: Double): String {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return if (abs(meters) < 1_000.0) {
        stringResource(R.string.distance_meters, NumberFormat.getIntegerInstance(locale).format(meters.roundToInt()))
    } else {
        val format = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
        stringResource(R.string.distance_kilometers, format.format(meters / 1_000.0))
    }
}

private const val CAMERA_ANIMATION_MILLIS = 700
private const val PIN_SETTLE_MILLIS = 350
private val ATTRIBUTION_START_MARGIN = 24.dp
private val ATTRIBUTION_BOTTOM_MARGIN = 24.dp
