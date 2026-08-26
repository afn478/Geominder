@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.afn478.geominder.alert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afn478.geominder.ReminderApplication
import com.afn478.geominder.R
import com.afn478.geominder.ui.theme.ReminderTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class FullScreenAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        hideSystemBars()

        val reminderId = intent.getStringExtra(AlertContract.EXTRA_REMINDER_ID).orEmpty()
        if (reminderId.isBlank()) {
            finish()
            return
        }
        val title = intent.getStringExtra(AlertContract.EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(AlertContract.EXTRA_TEXT).orEmpty()
        val timeText = intent.getStringExtra(AlertContract.EXTRA_TIME_TEXT)
            ?.takeIf(String::isNotBlank)
        val locationText = intent.getStringExtra(AlertContract.EXTRA_LOCATION_TEXT)
            ?.takeIf(String::isNotBlank)
        val isDebugAlert = intent.getBooleanExtra(AlertContract.EXTRA_DEBUG_ALERT, false)
        val settingsRepository = (application as ReminderApplication).appContainer.settingsRepository

        setContent {
            val settings = settingsRepository.settings.collectAsStateWithLifecycle().value
            ReminderTheme(
                themeMode = settings.themeMode,
                accentTheme = settings.accentTheme,
            ) {
                FullScreenAlert(
                    title = title,
                    text = text,
                    timeText = timeText,
                    locationText = locationText,
                    onSnooze = { finishAlert(reminderId, AlertAction.SNOOZE, isDebugAlert) },
                    onDismiss = { finishAlert(reminderId, AlertAction.DISMISS, isDebugAlert) },
                    onDone = { finishAlert(reminderId, AlertAction.DONE, isDebugAlert) },
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun finishAlert(reminderId: String, action: AlertAction, isDebugAlert: Boolean) {
        if (isDebugAlert) {
            finishAndRemoveTask()
            return
        }
        sendBroadcast(AlertIntentFactory.actionIntent(this, reminderId, action))
        finishAndRemoveTask()
    }
}

@Composable
private fun FullScreenAlert(
    title: String,
    text: String,
    timeText: String?,
    locationText: String?,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val backgroundTransition = rememberInfiniteTransition(label = "alert background")
    val pulsePhase = backgroundTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = ALERT_GRADIENT_PULSE_DURATION_MILLIS,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "alert background pulse",
    ).value
    val rotationDegrees = backgroundTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = ALERT_GRADIENT_ROTATION_DURATION_MILLIS,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "alert background rotation",
    ).value
    val intensity = ((sin(pulsePhase.toDouble()) + 1.0) / 2.0).toFloat()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val background = alertBackground(
                    colors = colors,
                    width = size.width,
                    height = size.height,
                    intensity = intensity,
                    rotationDegrees = rotationDegrees,
                )
                onDrawBehind { drawRect(brush = background) }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 184.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AlertIcon()
            Spacer(Modifier.height(24.dp))
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                ),
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (text.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = text,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (timeText != null || locationText != null) {
                Spacer(Modifier.height(24.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    timeText?.let {
                        AlertDetailChip(
                            icon = Icons.Default.AccessTime,
                            text = it,
                        )
                    }
                    locationText?.let {
                        AlertDetailChip(
                            icon = Icons.Default.LocationOn,
                            text = it,
                        )
                    }
                }
            }
        }

        AlertActions(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 120.dp),
            onSnooze = onSnooze,
            onDismiss = onDismiss,
            onDone = onDone,
        )
    }
}

private fun alertBackground(
    colors: ColorScheme,
    width: Float,
    height: Float,
    intensity: Float,
    rotationDegrees: Float,
): Brush {
    val screenCenter = Offset(width / 2f, height / 2f)
    val orbitRadius = max(width, height) * 0.50f
    val angleRadians = Math.toRadians(rotationDegrees.toDouble())
    val glowCenter = Offset(
        x = screenCenter.x + (sin(angleRadians).toFloat() * orbitRadius),
        y = screenCenter.y - (cos(angleRadians).toFloat() * orbitRadius),
    )
    val edgeAlpha = 0.10f + (0.05f * intensity)
    val centerAlpha = 0.36f + (0.08f * intensity)
    val outerAlpha = 0.02f + (0.02f * intensity)
    return Brush.radialGradient(
        colorStops = arrayOf(
            0.00f to colors.primary.copy(alpha = centerAlpha).compositeOver(colors.surface),
            0.32f to colors.primary.copy(alpha = edgeAlpha).compositeOver(colors.surface),
            0.72f to colors.primary.copy(alpha = outerAlpha).compositeOver(colors.surface),
            1.00f to colors.surface,
        ),
        center = glowCenter,
        radius = max(width, height) * 0.90f,
    )
}

private const val ALERT_GRADIENT_PULSE_DURATION_MILLIS = 24_000
private const val ALERT_GRADIENT_ROTATION_DURATION_MILLIS = 90_000

@Composable
private fun AlertIcon() {
    Surface(
        modifier = Modifier.size(80.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Icon(
            imageVector = Icons.Default.Alarm,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Composable
private fun AlertDetailChip(
    icon: ImageVector,
    text: String,
) {
    Surface(
        modifier = Modifier.widthIn(max = 380.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AlertActions(
    modifier: Modifier,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AlertButton(
            labelResource = R.string.alert_done,
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AlertButton(
                labelResource = R.string.alert_snooze,
                onClick = onSnooze,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = secondaryButtonColors(),
            )
            AlertButton(
                labelResource = R.string.alert_dismiss,
                onClick = onDismiss,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = secondaryButtonColors(),
            )
        }
    }
}

@Composable
private fun AlertButton(
    labelResource: Int,
    onClick: () -> Unit,
    modifier: Modifier,
    colors: ButtonColors,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        colors = colors,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(labelResource).uppercase(),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun secondaryButtonColors(): ButtonColors =
    ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
