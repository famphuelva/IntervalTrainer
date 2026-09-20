// Copyright (C) 2026 famphuelva
// SPDX-License-Identifier: GPL-3.0-or-later

package com.papichulo.intervaltrainer.engine

import com.papichulo.intervaltrainer.model.Phase
import com.papichulo.intervaltrainer.model.PhaseKind
import com.papichulo.intervaltrainer.model.TimerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {

    private fun twoPhaseConfig(rounds: Int = 2) = TimerConfig(
        phases = listOf(
            Phase("walk", "Caminar", seconds = 2, kind = PhaseKind.WALK),
            Phase("run", "Correr", seconds = 1, kind = PhaseKind.RUN)
        ),
        rounds = rounds
    )

    @Test
    fun `starts paused on the first phase with full duration`() {
        val engine = TimerEngine(twoPhaseConfig())
        val s = engine.state.value
        assertFalse(s.running)
        assertEquals(0, s.segmentIndex)
        assertEquals(2000L, s.remainingMs)
        assertEquals("walk", s.currentPhase.id)
    }

    @Test
    fun `tick does nothing while paused`() {
        val engine = TimerEngine(twoPhaseConfig())
        val events = engine.tick(500)
        assertTrue(events.isEmpty())
        assertEquals(2000L, engine.state.value.remainingMs)
    }

    @Test
    fun `counts down while running`() {
        val engine = TimerEngine(twoPhaseConfig())
        engine.play()
        engine.tick(500)
        assertEquals(1500L, engine.state.value.remainingMs)
    }

    @Test
    fun `advances to next phase and carries the overshoot`() {
        val engine = TimerEngine(twoPhaseConfig())
        engine.play()
        val events = engine.tick(2200) // 200ms past the 2s walk phase
        assertEquals(1, engine.state.value.segmentIndex)
        assertEquals("run", engine.state.value.currentPhase.id)
        assertEquals(800L, engine.state.value.remainingMs) // 1000 - 200 carry
        assertTrue(events.any { it is TickEvent.PhaseChanged })
    }

    @Test
    fun `finishes after the last segment of the last round`() {
        val engine = TimerEngine(twoPhaseConfig(rounds = 1))
        engine.play()
        engine.tick(2000) // finishes walk -> starts run
        val events = engine.tick(1000) // finishes run -> workout done
        assertTrue(engine.state.value.finished)
        assertFalse(engine.state.value.running)
        assertTrue(events.any { it is TickEvent.Finished })
    }

    @Test
    fun `skip moves to the requested segment with full duration and clamps at the edges`() {
        val engine = TimerEngine(twoPhaseConfig(rounds = 2))
        engine.skip(1)
        assertEquals(1, engine.state.value.segmentIndex)
        assertEquals(1000L, engine.state.value.remainingMs)

        engine.skip(-5)
        assertEquals(0, engine.state.value.segmentIndex)

        engine.skip(99)
        assertEquals(engine.state.value.totalSegments - 1, engine.state.value.segmentIndex)
    }

    @Test
    fun `reset returns to the first segment and clears finished state`() {
        val engine = TimerEngine(twoPhaseConfig(rounds = 1))
        engine.play()
        engine.tick(5000)
        assertTrue(engine.state.value.finished)

        engine.reset()
        assertFalse(engine.state.value.finished)
        assertFalse(engine.state.value.running)
        assertEquals(0, engine.state.value.segmentIndex)
        assertEquals(2000L, engine.state.value.remainingMs)
    }

    @Test
    fun `emits a countdown beep event only once per second for the last three seconds`() {
        val engine = TimerEngine(
            TimerConfig(
                phases = listOf(Phase("p", "Fase", seconds = 5, kind = PhaseKind.REST)),
                rounds = 1
            )
        )
        engine.play()
        engine.tick(2100) // remaining 2.9s -> secLeft 3
        val firstBeeps = engine.tick(50).count { it is TickEvent.CountdownBeep } // still secLeft 3
        assertEquals(0, firstBeeps)
        val beepAt2 = engine.tick(900).count { it is TickEvent.CountdownBeep } // crosses into secLeft 2
        assertEquals(1, beepAt2)
    }
}
