@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.afn478.geominder.alert

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.afn478.geominder.R
import com.afn478.geominder.ui.theme.ReminderTheme
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

class FullScreenAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        configureScrim()
        enableEdgeToEdge()

        val reminderId = intent.getStringExtra(AlertContract.EXTRA_REMINDER_ID).orEmpty()
        if (reminderId.isBlank()) {
            finish()
            return
        }
        val title = intent.getStringExtra(AlertContract.EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(AlertContract.EXTRA_TEXT).orEmpty()

        setContent {
            ReminderTheme(darkTheme = true) {
                FullScreenAlert(
                    title = title,
                    text = text,
                    onSnooze = { finishWithAction(reminderId, AlertAction.SNOOZE) },
                    onDismiss = { finishWithAction(reminderId, AlertAction.DISMISS) },
                    onDone = { finishWithAction(reminderId, AlertAction.DONE) },
                )
            }
        }
    }

    @Suppress("DEPRECATION") // Required only for the API 29-30 solid system-bar fallback.
    private fun configureScrim() {
        window.setBackgroundDrawableResource(android.R.color.transparent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = 72
                dimAmount = 0.45f
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        } else {
            window.statusBarColor = AndroidColor.BLACK
            window.navigationBarColor = AndroidColor.BLACK
        }
    }

    private fun finishWithAction(reminderId: String, action: AlertAction) {
        sendBroadcast(AlertIntentFactory.actionIntent(this, reminderId, action))
        finishAndRemoveTask()
    }
}

@Composable
private fun FullScreenAlert(
    title: String,
    text: String,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    val isBlurredScrim = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scrim = if (isBlurredScrim) Color.Black.copy(alpha = 0.54f) else Color(0xFF101014)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrim)
            .padding(horizontal = 24.dp, vertical = 40.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PulsingAlertRing()
                Spacer(Modifier.height(36.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                if (text.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            AlertActions(onSnooze = onSnooze, onDismiss = onDismiss, onDone = onDone)
        }
    }
}

@Composable
private fun PulsingAlertRing() {
    val pulse = remember { Animatable(0f) }
    val effectsSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    LaunchedEffect(effectsSpec) {
        while (currentCoroutineContext().isActive) {
            pulse.animateTo(1f, effectsSpec)
            pulse.animateTo(0f, effectsSpec)
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(184.dp)
            .background(primary.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 18.dp.toPx() * pulse.value
            val alpha = 0.92f - (pulse.value * 0.48f)
            drawCircle(
                color = primary.copy(alpha = alpha),
                radius = (size.minDimension / 2f) - inset,
                center = Offset(size.width / 2f, size.height / 2f),
                style = Stroke(width = 6.dp.toPx() - pulse.value * 2.dp.toPx()),
            )
            drawCircle(
                color = primary,
                radius = 42.dp.toPx(),
                center = center,
            )
        }
    }
}

@Composable
private fun AlertActions(
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(
            onClick = onSnooze,
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Text(stringResource(R.string.alert_snooze), style = MaterialTheme.typography.titleLarge)
        }
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Text(stringResource(R.string.alert_dismiss), style = MaterialTheme.typography.titleLarge)
        }
        Button(
            onClick = onDone,
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Text(stringResource(R.string.alert_done), style = MaterialTheme.typography.titleLarge)
        }
    }
}
