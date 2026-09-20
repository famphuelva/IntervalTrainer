// Copyright (C) 2026 famphuelva
// SPDX-License-Identifier: GPL-3.0-or-later

package com.papichulo.intervaltrainer.engine

import com.papichulo.intervaltrainer.model.Phase
import com.papichulo.intervaltrainer.model.TimerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class TickEvent {
    data class PhaseChanged(val phase: Phase) : TickEvent()
    data class CountdownBeep(val secondsLeft: Int) : TickEvent()
    object Finished : TickEvent()
}

/**
 * Pure Kotlin countdown engine, no Android dependencies so it can be unit tested on the JVM.
 * The caller is responsible for supplying real elapsed-time deltas (e.g. from
 * SystemClock.elapsedRealtime()) so the countdown stays accurate across Doze/background throttling.
 */
class TimerEngine(initialConfig: TimerConfig) {

    private val _state = MutableStateFlow(TimerState(config = initialConfig))
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private var lastAnnouncedSecond: Int? = null

    fun play() {
        val s = _state.value
        if (s.finished || s.running) return
        _state.value = s.copy(running = true)
    }

    fun pause() {
        val s = _state.value
        if (!s.running) return
        _state.value = s.copy(running = false)
    }

    fun reset() {
        val s = _state.value
        lastAnnouncedSecond = null
        _state.value = TimerState(config = s.config)
    }

    fun skip(direction: Int) {
        val s = _state.value
        val target = (s.segmentIndex + direction).coerceIn(0, s.totalSegments - 1)
        lastAnnouncedSecond = null
        _state.value = s.copy(
            segmentIndex = target,
            remainingMs = s.config.phases[target % s.config.phases.size].seconds * 1000L,
            finished = false
        )
    }

    /** Replaces phases/rounds/sound and restarts the run from the first segment. */
    fun updateConfig(config: TimerConfig) {
        lastAnnouncedSecond = null
        _state.value = TimerState(config = config)
    }

    fun setSoundOn(soundOn: Boolean) {
        _state.value = _state.value.copy(config = _state.value.config.copy(soundOn = soundOn))
    }

    /**
     * Advances the countdown by [deltaMs] of real elapsed time and returns the events that
     * occurred during this step (in order), so the caller can trigger sound/vibration/notifications.
     */
    fun tick(deltaMs: Long): List<TickEvent> {
        val s = _state.value
        if (!s.running || s.finished) return emptyList()

        val events = mutableListOf<TickEvent>()
        var remaining = s.remainingMs - deltaMs
        var segmentIndex = s.segmentIndex

        while (remaining <= 0) {
            val nextIndex = segmentIndex + 1
            if (nextIndex >= s.totalSegments) {
                _state.value = s.copy(
                    segmentIndex = segmentIndex,
                    remainingMs = 0,
                    running = false,
                    finished = true
                )
                events += TickEvent.Finished
                lastAnnouncedSecond = null
                return events
            }
            val carry = -remaining
            segmentIndex = nextIndex
            val phase = s.config.phases[segmentIndex % s.config.phases.size]
            remaining = phase.seconds * 1000L - carry
            events += TickEvent.PhaseChanged(phase)
            lastAnnouncedSecond = null
        }

        val secLeft = Math.ceil(remaining / 1000.0 - 0.0001).toInt().coerceAtLeast(0)
        if (secLeft != lastAnnouncedSecond) {
            lastAnnouncedSecond = secLeft
            if (secLeft in 1..3) events += TickEvent.CountdownBeep(secLeft)
        }

        _state.value = s.copy(segmentIndex = segmentIndex, remainingMs = remaining)
        return events
    }
}
