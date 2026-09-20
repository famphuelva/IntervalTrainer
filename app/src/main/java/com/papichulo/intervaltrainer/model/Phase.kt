// Copyright (C) 2026 famphuelva
// SPDX-License-Identifier: GPL-3.0-or-later

package com.papichulo.intervaltrainer.model

import kotlinx.serialization.Serializable

@Serializable
enum class PhaseKind { WALK, RUN, REST }

@Serializable
data class Phase(
    val id: String,
    val name: String,
    val seconds: Int,
    val kind: PhaseKind
)

@Serializable
data class TimerConfig(
    val phases: List<Phase>,
    val rounds: Int,
    val soundOn: Boolean = true,
    val speedOn: Boolean = true
) {
    companion object {
        fun default() = TimerConfig(
            phases = listOf(
                Phase(id = "p1", name = "Caminar", seconds = 60, kind = PhaseKind.WALK),
                Phase(id = "p2", name = "Correr", seconds = 30, kind = PhaseKind.RUN)
            ),
            rounds = 8
        )
    }
}
