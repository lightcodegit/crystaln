package com.crystaln.vpn.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Usage counters must survive process restarts and never go negative. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrafficStatsStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication() as Context
        TrafficStatsStore.clear(context, "p1")
        TrafficStatsStore.clear(context, "p2")
    }

    @Test
    fun accumulatesAcrossReloads() {
        TrafficStatsStore.add(context, "p1", rx = 100, tx = 50, seconds = 3)
        // Simulate a process restart: new object state reads from disk.
        TrafficStatsStore.flush(context)
        TrafficStatsStore.resetForTest()
        TrafficStatsStore.add(context, "p1", rx = 10, tx = 5, seconds = 1)

        val s = TrafficStatsStore.get(context, "p1")
        assertEquals(110L, s.rx)
        assertEquals(55L, s.tx)
        assertEquals(4L, s.seconds)
    }

    @Test
    fun nonPositiveDeltasAreIgnored() {
        TrafficStatsStore.add(context, "p2", rx = 0, tx = 0, seconds = 0)
        assertEquals(TrafficStats(), TrafficStatsStore.get(context, "p2"))
    }

    @Test
    fun clearRemovesProfileOnly() {
        TrafficStatsStore.add(context, "p1", rx = 1, tx = 1, seconds = 1)
        TrafficStatsStore.add(context, "p2", rx = 2, tx = 2, seconds = 2)
        TrafficStatsStore.clear(context, "p1")

        assertEquals(TrafficStats(), TrafficStatsStore.get(context, "p1"))
        assertEquals(2L, TrafficStatsStore.get(context, "p2").rx)
    }
}
