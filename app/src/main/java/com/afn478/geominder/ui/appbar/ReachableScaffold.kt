package com.afn478.geominder.ui.appbar

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * A Material scaffold with a Samsung-style reachable app bar on tall portrait windows.
 *
 * The compact layout remains Material's native [TopAppBar]. On eligible windows, the same
 * title is rendered once and transformed between the native compact position and a centered,
 * larger position. Navigation and action controls use their own measured motion so they can
 * settle below the expanded title without changing the title's compact geometry.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReachableScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    compactTitleStartPadding: Dp = COMPACT_TITLE_START_PADDING,
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    /** The app bar re-expands when this changes; pass a stable key when only the title text varies. */
    scrollResetKey: Any? = title,
    content: @Composable (PaddingValues) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val appBarState = rememberTopAppBarState()
        // Size the header from the window, not from these constraints: the keyboard must never
        // flip the bar between its reachable and compact forms or resize it mid-animation.
        val windowSize = with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.let { DpSize(it.width.toDp(), it.height.toDp()) }
        }
        val windowHeight = windowSize.height.takeIf { it > 0.dp } ?: maxHeight
        val windowWidth = windowSize.width.takeIf { it > 0.dp } ?: maxWidth
        val useReachableAppBar = shouldUseReachableAppBar(
            windowHeight = windowHeight,
            windowWidth = windowWidth,
        )
        // While typing, the big pulled-down title would leave almost no room between itself and
        // the keyboard, so the header stays collapsed and ignores drags until the keyboard closes.
        val imeVisible = WindowInsets.isImeVisible
        val currentImeVisible by rememberUpdatedState(imeVisible)
        val scrollBehavior = if (useReachableAppBar) {
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
                state = appBarState,
                canScroll = { !currentImeVisible },
            )
        } else {
            null
        }

        // Only a real key change re-expands the bar. Coming back to this screen (say, from the map
        // picker) restores the saved header and content scroll together, so they stay in step.
        var appliedResetKey by rememberSaveable { mutableStateOf<Int?>(null) }
        LaunchedEffect(scrollResetKey) {
            val key = scrollResetKey.hashCode()
            if (appliedResetKey != null && appliedResetKey != key) {
                appBarState.heightOffset = 0f
                appBarState.contentOffset = 0f
            }
            appliedResetKey = key
        }
        LaunchedEffect(imeVisible, useReachableAppBar) {
            val collapsedOffset = appBarState.heightOffsetLimit
            if (imeVisible && useReachableAppBar && appBarState.heightOffset > collapsedOffset) {
                animate(
                    initialValue = appBarState.heightOffset,
                    targetValue = collapsedOffset,
                    animationSpec = tween(durationMillis = IME_COLLAPSE_MILLIS),
                ) { value, _ -> appBarState.heightOffset = value }
            }
        }

        val scaffoldModifier = Modifier
            .fillMaxSize()
            .then(
                scrollBehavior?.let { Modifier.nestedScroll(it.nestedScrollConnection) }
                    ?: Modifier,
            )

        Scaffold(
            modifier = scaffoldModifier,
            topBar = {
                ReachableTopAppBar(
                    title = title,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    compactTitleStartPadding = compactTitleStartPadding,
                    expandedHeight = reachableAppBarExpandedHeight(windowHeight),
                    appBarState = appBarState,
                    scrollBehavior = scrollBehavior,
                )
            },
            bottomBar = bottomBar,
            snackbarHost = snackbarHost,
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReachableTopAppBar(
    title: String,
    navigationIcon: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    compactTitleStartPadding: Dp,
    expandedHeight: Dp,
    appBarState: TopAppBarState,
    scrollBehavior: TopAppBarScrollBehavior?,
) {
    if (scrollBehavior == null) {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = navigationIcon ?: {},
            colors = reachableAppBarColors(),
            actions = actions,
        )
        return
    }

    Box {
        LargeTopAppBar(
            title = { Spacer(Modifier) },
            navigationIcon = {},
            colors = reachableAppBarColors(),
            actions = {},
            expandedHeight = expandedHeight,
            scrollBehavior = scrollBehavior,
        )
        ReachableTopAppBarOverlay(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            collapsedFraction = appBarState.collapsedFraction,
            compactTitleStartPadding = compactTitleStartPadding,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun ReachableTopAppBarOverlay(
    title: String,
    navigationIcon: (@Composable () -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    collapsedFraction: Float,
    compactTitleStartPadding: Dp,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        var titleSize by remember(title) { mutableStateOf(IntSize.Zero) }
        var navigationIconHeightPx by remember { mutableIntStateOf(0) }
        var actionHeightPx by remember { mutableIntStateOf(0) }
        val expansionFraction = (1f - collapsedFraction).coerceIn(0f, 1f)
        // Keep the title and controls independent: the title morphs through its own transform,
        // while navigation and actions stay anchored to the moving app-bar/content boundary.
        val titleProgress = expansionFraction
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val compactTitleStartPx = with(density) { compactTitleStartPadding.toPx() }
        val compactTitleOffsetYPx = TopAppBarDefaults.windowInsets.getTop(density) / 2f
        val expandedTitleOffsetYPx = with(density) {
            EXPANDED_TITLE_VERTICAL_OFFSET.toPx()
        }
        val expandedTitleHorizontalPaddingPx = with(density) {
            EXPANDED_TITLE_HORIZONTAL_PADDING.toPx()
        }
        val maxExpandedTitleWidthPx = (
            containerWidthPx - expandedTitleHorizontalPaddingPx * 2f
            ).coerceAtLeast(0f)
        val expandedTitleScale = if (titleSize.width > 0) {
            (maxExpandedTitleWidthPx / titleSize.width).coerceIn(
                minimumValue = 1f,
                maximumValue = EXPANDED_TITLE_SCALE,
            )
        } else {
            EXPANDED_TITLE_SCALE
        }
        val expandedTitleStartPx = if (titleSize.width > 0) {
            (containerWidthPx - titleSize.width) / 2f
        } else {
            compactTitleStartPx
        }
        val titleTranslationX = compactTitleStartPx +
            (expandedTitleStartPx - compactTitleStartPx) * titleProgress
        val titleTranslationY = compactTitleOffsetYPx +
            (expandedTitleOffsetYPx - compactTitleOffsetYPx) * titleProgress
        val titleScale = 1f +
            (expandedTitleScale - 1f) * titleProgress

        val actionHeight = actionHeightPx.toFloat()
        val actionBottomPaddingPx = with(density) { ACTION_BOTTOM_PADDING.toPx() }
        fun bottomAlignedTranslationY(itemHeight: Float): Float = (
            containerHeightPx - itemHeight / 2f - actionBottomPaddingPx
            ) - containerHeightPx / 2f
        val navigationIconTranslationY = bottomAlignedTranslationY(
            navigationIconHeightPx.toFloat(),
        )
        val actionTranslationY = bottomAlignedTranslationY(actionHeight)

        navigationIcon?.let { icon ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = TOP_APP_BAR_HORIZONTAL_PADDING)
                    .onSizeChanged { navigationIconHeightPx = it.height }
                    .graphicsLayer { translationY = navigationIconTranslationY },
            ) {
                icon()
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .widthIn(
                    max = (
                        maxWidth - EXPANDED_TITLE_HORIZONTAL_PADDING * 2f
                        ).coerceAtLeast(0.dp),
                )
                .onSizeChanged { titleSize = it }
                .graphicsLayer {
                    translationX = titleTranslationX
                    translationY = titleTranslationY
                    scaleX = titleScale
                    scaleY = titleScale
                    transformOrigin = TransformOrigin.Center
                },
        )

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = TOP_APP_BAR_HORIZONTAL_PADDING)
                .onSizeChanged { actionHeightPx = it.height }
                .graphicsLayer { translationY = actionTranslationY },
            content = actions,
        )
    }
}

@Composable
private fun reachableAppBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.background,
    scrolledContainerColor = MaterialTheme.colorScheme.background,
)

internal fun shouldUseReachableAppBar(
    windowHeight: Dp,
    windowWidth: Dp,
): Boolean =
    windowHeight >= REACHABLE_APP_BAR_MIN_WINDOW_HEIGHT && windowHeight > windowWidth

@OptIn(ExperimentalMaterial3Api::class)
internal fun reachableAppBarExpandedHeight(windowHeight: Dp): Dp =
    (windowHeight * REACHABLE_APP_BAR_HEIGHT_FRACTION).coerceIn(
        minimumValue = TopAppBarDefaults.LargeAppBarExpandedHeight,
        maximumValue = REACHABLE_APP_BAR_MAX_EXPANDED_HEIGHT,
    )

private const val REACHABLE_APP_BAR_HEIGHT_FRACTION = 0.40f
private const val IME_COLLAPSE_MILLIS = 200
private const val EXPANDED_TITLE_SCALE = 1.55f
private val COMPACT_TITLE_START_PADDING = 24.dp
private val EXPANDED_TITLE_VERTICAL_OFFSET = 20.dp
private val EXPANDED_TITLE_HORIZONTAL_PADDING = 16.dp
private val ACTION_BOTTOM_PADDING = 8.dp
private val TOP_APP_BAR_HORIZONTAL_PADDING = 4.dp
private val REACHABLE_APP_BAR_MIN_WINDOW_HEIGHT = 580.dp
private val REACHABLE_APP_BAR_MAX_EXPANDED_HEIGHT = 360.dp
