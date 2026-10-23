package com.afn478.geominder.ui.map

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.afn478.geominder.R
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.roundToInt

/**
 * A small static map of a location trigger: the place, its trigger radius, and a tap target that
 * opens the full picker. Without coordinates it shows an invitation to pick a place instead.
 */
@Composable
fun LocationMapPreview(
    latitude: Double?,
    longitude: Double?,
    radiusMeters: Double?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    height: Dp = 168.dp,
) {
    val shape = MaterialTheme.shapes.large
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            onClickLabel = stringResource(R.string.pick_on_map),
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (latitude == null || longitude == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(clickModifier),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    stringResource(R.string.pick_on_map),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            return@Surface
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthDp = maxWidth.value
            val heightDp = maxHeight.value
            val target = LatLng(latitude, longitude)
            val radius = radiusMeters?.takeIf { it.isFinite() && it > 0.0 }
            val currentTarget by rememberUpdatedState(target)
            val currentRadius by rememberUpdatedState(radius)
            var map by remember { mutableStateOf<MapLibreMap?>(null) }
            var metersPerDp by remember { mutableStateOf<Double?>(null) }

            fun frame(map: MapLibreMap) {
                val zoom = zoomForRadius(currentRadius ?: 150.0, currentTarget.latitude, widthDp, heightDp)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentTarget, zoom))
                metersPerDp = map.projection.getMetersPerPixelAtLatitude(currentTarget.latitude)
            }

            MapLibreView(
                interactive = false,
                onMapReady = { ready ->
                    map = ready
                    frame(ready)
                },
                modifier = Modifier.fillMaxSize(),
            )
            // Re-frame when the trigger moves or its radius changes.
            LaunchedEffect(map, latitude, longitude, radius, widthDp, heightDp) {
                map?.let(::frame)
            }
            LocationMarker(
                radiusDp = radius?.let { r -> metersPerDp?.let { (r / it).toFloat().dp } },
                modifier = Modifier.fillMaxSize(),
            )
            // Above the map so taps and scrolls reach the card, not the map view.
            Box(
                Modifier
                    .fillMaxSize()
                    .then(clickModifier),
            )
        }
    }
}

/**
 * The trigger radius as a translucent circle with a pin whose tip marks the exact centre.
 * [offset] moves both away from the centre while the pin is dragged; [pinModifier] goes on the
 * pin's touch target.
 */
@Composable
internal fun LocationMarker(
    radiusDp: Dp?,
    modifier: Modifier = Modifier,
    lifted: Boolean = false,
    offset: () -> Offset = { Offset.Zero },
    pinModifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val lift by animateDpAsState(if (lifted) 10.dp else 0.dp, label = "pin lift")
    Box(modifier, contentAlignment = Alignment.Center) {
        if (radiusDp != null) {
            Canvas(Modifier.fillMaxSize()) {
                val radiusPx = with(density) { radiusDp.toPx() }
                val centre = center + offset()
                drawCircle(color = color.copy(alpha = 0.16f), radius = radiusPx, center = centre)
                drawCircle(
                    color = color.copy(alpha = 0.7f),
                    radius = radiusPx,
                    center = centre,
                    style = Stroke(width = 2.dp.toPx()),
                )
                drawCircle(color = color, radius = 3.dp.toPx(), center = centre)
            }
        }
        Box(
            modifier = Modifier
                // The Place glyph's tip sits at 22/24 of its height; lift it onto the centre.
                .offset {
                    val drag = offset()
                    IntOffset(
                        drag.x.roundToInt(),
                        drag.y.roundToInt() + (-(PIN_SIZE * 0.42f) - lift).roundToPx(),
                    )
                }
                .size(PIN_TOUCH_SIZE)
                .then(pinModifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Place,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(PIN_SIZE),
            )
        }
    }
}

private val PIN_SIZE = 44.dp
private val PIN_TOUCH_SIZE = 64.dp
