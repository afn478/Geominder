package com.afn478.geominder.ui.appbar

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReachableAppBarPolicyTest {
    @Test
    fun `reachable app bar is limited to tall portrait windows`() {
        assertTrue(shouldUseReachableAppBar(windowHeight = 800.dp, windowWidth = 400.dp))
        assertFalse(shouldUseReachableAppBar(windowHeight = 579.dp, windowWidth = 400.dp))
        assertFalse(shouldUseReachableAppBar(windowHeight = 800.dp, windowWidth = 1_200.dp))
    }

    @Test
    fun `expanded height uses the reachability proportion and a sensible cap`() {
        assertEquals(232.dp, reachableAppBarExpandedHeight(580.dp))
        assertEquals(320.dp, reachableAppBarExpandedHeight(800.dp))
        assertEquals(360.dp, reachableAppBarExpandedHeight(1_000.dp))
    }
}
