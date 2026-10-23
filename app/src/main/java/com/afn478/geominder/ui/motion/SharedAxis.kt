package com.afn478.geominder.ui.motion

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * Material's shared-axis X pattern, the motion Android itself uses between a page and its
 * children: both pages travel a short way along the axis while the outgoing one fades out fast
 * and the incoming one fades in after it, so the two never sit on top of each other half-faded.
 */
object SharedAxis {
    /** A page opening deeper in the hierarchy. */
    fun forwardEnter(): EnterTransition = enter(fromRight = true)
    fun forwardExit(): ExitTransition = exit(toLeft = true)

    /** Going back to the page underneath. */
    fun backwardEnter(): EnterTransition = enter(fromRight = false)
    fun backwardExit(): ExitTransition = exit(toLeft = false)

    fun forward(): ContentTransform = forwardEnter() togetherWith forwardExit()
    fun backward(): ContentTransform = backwardEnter() togetherWith backwardExit()

    private fun enter(fromRight: Boolean): EnterTransition =
        slideInHorizontally(tween(DURATION_MILLIS, easing = FastOutSlowInEasing)) { width ->
            travel(width) * if (fromRight) 1 else -1
        } + fadeIn(tween(FADE_IN_MILLIS, delayMillis = FADE_OUT_MILLIS, easing = LinearOutSlowInEasing))

    private fun exit(toLeft: Boolean): ExitTransition =
        slideOutHorizontally(tween(DURATION_MILLIS, easing = FastOutSlowInEasing)) { width ->
            travel(width) * if (toLeft) -1 else 1
        } + fadeOut(tween(FADE_OUT_MILLIS, easing = FastOutLinearInEasing))

    // Roughly Material's 30 dp on a phone, without needing a density here.
    private fun travel(width: Int): Int = (width * TRAVEL_FRACTION).toInt()

    private const val DURATION_MILLIS = 300
    private const val FADE_OUT_MILLIS = 90
    private const val FADE_IN_MILLIS = DURATION_MILLIS - FADE_OUT_MILLIS
    private const val TRAVEL_FRACTION = 0.08f
}
