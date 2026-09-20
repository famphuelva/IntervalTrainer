// Copyright (C) 2026 famphuelva
// SPDX-License-Identifier: GPL-3.0-or-later

package com.papichulo.intervaltrainer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.papichulo.intervaltrainer.model.PhaseKind

data class TrainerColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val onBackground: Color,
    val onSurfaceDim: Color,
    val walk: Color,
    val run: Color,
    val rest: Color,
    val danger: Color
) {
    fun accentFor(kind: PhaseKind): Color = when (kind) {
        PhaseKind.WALK -> walk
        PhaseKind.RUN -> run
        PhaseKind.REST -> rest
    }

    val onAccentInk: Color = Color(0xFF04150D)
}

private val LightTrainerColors = TrainerColors(
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
    border = LightBorder,
    onBackground = LightOnBackground,
    onSurfaceDim = LightOnSurfaceDim,
    walk = AccentWalk,
    run = AccentRun,
    rest = AccentRest,
    danger = Danger
)

private val DarkTrainerColors = TrainerColors(
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    border = DarkBorder,
    onBackground = DarkOnBackground,
    onSurfaceDim = DarkOnSurfaceDim,
    walk = AccentWalkDark,
    run = AccentRunDark,
    rest = AccentRestDark,
    danger = DangerDark
)

val LocalTrainerColors = staticCompositionLocalOf { LightTrainerColors }

object TrainerTheme {
    val colors: TrainerColors
        @Composable get() = LocalTrainerColors.current
}

@Composable
fun IntervalTrainerTheme(content: @Composable () -> Unit) {
    val trainerColors = if (isSystemInDarkTheme()) DarkTrainerColors else LightTrainerColors

    val typography = Typography(
        displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 52.sp),
        titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp),
        bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp),
        labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    )

    CompositionLocalProvider(LocalTrainerColors provides trainerColors) {
        MaterialTheme(typography = typography, content = content)
    }
}
