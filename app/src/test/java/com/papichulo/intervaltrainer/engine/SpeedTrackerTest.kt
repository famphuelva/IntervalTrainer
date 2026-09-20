// Copyright (C) 2026 famphuelva
// SPDX-License-Identifier: GPL-3.0-or-later

package com.papichulo.intervaltrainer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedTrackerTest {

    // ~1.11 m per 0.00001 deg latitude; 0.00009 deg ≈ 10 m
    private fun feed(t: SpeedTracker, seconds: Int, metersPerSec: Double, startSec: Int = 0, startMeters: Double = 0.0) {
        val degPerM = 0.00001 / 1.1132
        for (i in 0..seconds) {
            val sec = startSec + i
            t.onLocation(40.0 + (startMeters + i * metersPerSec) * degPerM, -3.0, 5f, sec * 1000L)
            if (i > 0) t.addTime(1000)
        }
    }

    @Test
    fun steadyPaceGivesExpectedSpeed() {
        val t = SpeedTracker()
        feed(t, 20, 2.5) // 9 km/h
        val s = t.stats.value
        assertEquals(9.0, s.currentKmh!!, 0.3)
        assertEquals(9.0, s.totalAvgKmh!!, 0.3)
        assertEquals(50.0, s.distanceM, 2.0)
    }

    @Test
    fun poorAccuracyFixesAreIgnored() {
        val t = SpeedTracker()
        t.onLocation(40.0, -3.0, 80f, 0)
        t.onLocation(40.01, -3.0, 80f, 1000)
        assertEquals(0.0, t.stats.value.distanceM, 0.0)
    }

    @Test
    fun jitterWhileStandingStillAddsNoDistance() {
        val t = SpeedTracker()
        t.onLocation(40.0, -3.0, 5f, 0)
        for (i in 1..10) t.onLocation(40.0 + (i % 2) * 0.000005, -3.0, 5f, i * 1000L)
        assertEquals(0.0, t.stats.value.distanceM, 0.0)
    }

    @Test
    fun roundChangeKeepsPreviousRoundAverage() {
        val t = SpeedTracker()
        feed(t, 10, 2.0)              // round 1 at 7.2 km/h
        t.setRound(2)
        assertNull(t.stats.value.roundAvgKmh)
        assertEquals(7.2, t.stats.value.prevRoundAvgKmh!!, 0.4)
        feed(t, 10, 3.0, startSec = 10, startMeters = 20.0) // round 2 faster
        val s = t.stats.value
        assertEquals(10.8, s.roundAvgKmh!!, 0.8)
        assertNotNull(s.totalAvgKmh)
    }

    @Test
    fun pauseGapIsNotCountedAsDistance() {
        val t = SpeedTracker()
        feed(t, 5, 2.0)
        val before = t.stats.value.distanceM
        t.breakSegment()
        t.onLocation(41.0, -3.0, 5f, 600_000L) // far away after a pause
        assertEquals(before, t.stats.value.distanceM, 0.0)
    }
}
