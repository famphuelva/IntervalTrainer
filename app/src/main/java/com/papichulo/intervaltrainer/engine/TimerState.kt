// Copyright (C) 2026 famphuelva
// SPDX-License-Identifier: GPL-3.0-or-later

package com.papichulo.intervaltrainer.engine

import com.papichulo.intervaltrainer.model.Phase
import com.papichulo.intervaltrainer.model.TimerConfig

data class TimerState(
    val config: TimerConfig,
    val segmentIndex: Int = 0,
    val remainingMs: Long = (config.phases.firstOrNull()?.seconds ?: 0) * 1000L,
    val running: Boolean = false,
    val finished: Boolean = false
) {
    val totalSegments: Int get() = config.phases.size * config.rounds

    val currentPhase: Phase get() = config.phases[segmentIndex % config.phases.size]

    val currentRound: Int get() = (segmentIndex / config.phases.size) + 1

    val nextPhase: Phase? get() {
        val next = segmentIndex + 1
        return if (next < totalSegments) config.phases[next % config.phases.size] else null
    }

    val totalWorkoutSeconds: Int get() =
        config.phases.sumOf { it.seconds } * config.rounds
}
