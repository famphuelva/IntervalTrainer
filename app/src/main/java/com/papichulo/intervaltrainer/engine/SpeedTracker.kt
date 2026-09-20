// Copyright (C) 2026 famphuelva
// SPDX-License-Identifier: GPL-3.0-or-later

package com.papichulo.intervaltrainer.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class SpeedStats(
    val currentKmh: Double? = null,
    val roundAvgKmh: Double? = null,
    val prevRoundAvgKmh: Double? = null,
    val totalAvgKmh: Double? = null,
    val distanceM: Double = 0.0
)

/**
 * Pure Kotlin speed/distance tracker fed with GPS fixes. Averages are distance divided by
 * *workout* time (only time reported through [addTime], i.e. while the timer runs), so pauses
 * don't drag the average down. Not tied to Android so it can be unit tested on the JVM.
 */
class SpeedTracker {

    private val _stats = MutableStateFlow(SpeedStats())
    val stats: StateFlow<SpeedStats> = _stats.asStateFlow()

    private var lastLat = 0.0
    private var lastLon = 0.0
    private var hasLast = false

    private var totalDist = 0.0
    private var totalMs = 0L
    private var roundDist = 0.0
    private var roundMs = 0L
    private var prevRoundAvg: Double? = null
    private var round = 1

    private val window = ArrayDeque<Pair<Long, Double>>() // (fix time ms, cumulative distance m)
    private var lastFixMs = 0L
    private var nowMs = 0L
    private var current: Double? = null

    @Synchronized
    fun addTime(deltaMs: Long) {
        totalMs += deltaMs
        roundMs += deltaMs
        nowMs += deltaMs
        if (current != null && nowMs - lastFixMs > STALE_MS) current = null
        publish()
    }

    @Synchronized
    fun setRound(newRound: Int) {
        if (newRound == round) return
        prevRoundAvg = kmh(roundDist, roundMs)
        round = newRound
        roundDist = 0.0
        roundMs = 0L
        publish()
    }

    /** Call when tracking pauses so the gap between two fixes isn't counted as distance. */
    @Synchronized
    fun breakSegment() {
        hasLast = false
        window.clear()
        current = null
        publish()
    }

    @Synchronized
    fun reset() {
        hasLast = false
        window.clear()
        totalDist = 0.0; totalMs = 0L; roundDist = 0.0; roundMs = 0L
        prevRoundAvg = null; round = 1; current = null
        publish()
    }

    /** [timeMs] must be monotonic (e.g. Location.elapsedRealtimeNanos / 1e6). */
    @Synchronized
    fun onLocation(lat: Double, lon: Double, accuracyM: Float, timeMs: Long) {
        if (accuracyM > MAX_ACCURACY_M) return
        nowMs = timeMs
        lastFixMs = timeMs
        if (!hasLast) {
            lastLat = lat; lastLon = lon; hasLast = true
            window.addLast(timeMs to totalDist)
            return
        }
        val step = haversineM(lastLat, lastLon, lat, lon)
        if (step < MIN_STEP_M) return // GPS jitter while standing still; keep old anchor
        lastLat = lat; lastLon = lon
        totalDist += step
        roundDist += step

        window.addLast(timeMs to totalDist)
        while (window.size > 1 && timeMs - window.first().first > WINDOW_MS) window.removeFirst()
        val span = timeMs - window.first().first
        current = if (span >= MIN_SPAN_MS) kmh(totalDist - window.first().second, span) else current
        publish()
    }

    private fun kmh(distM: Double, ms: Long): Double? =
        if (ms < 1000L) null else distM / (ms / 1000.0) * 3.6

    private fun publish() {
        _stats.value = SpeedStats(
            currentKmh = current,
            roundAvgKmh = kmh(roundDist, roundMs),
            prevRoundAvgKmh = prevRoundAvg,
            totalAvgKmh = kmh(totalDist, totalMs),
            distanceM = totalDist
        )
    }

    companion object {
        private const val MAX_ACCURACY_M = 30f
        private const val MIN_STEP_M = 3.0
        private const val WINDOW_MS = 10_000L
        private const val MIN_SPAN_MS = 3_000L
        private const val STALE_MS = 8_000L

        fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * r * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
