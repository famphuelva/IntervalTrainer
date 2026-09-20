// Copyright (C) 2026 famphuelva
// SPDX-License-Identifier: GPL-3.0-or-later

package com.papichulo.intervaltrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.papichulo.intervaltrainer.engine.SpeedStats
import com.papichulo.intervaltrainer.engine.TimerState
import com.papichulo.intervaltrainer.model.Phase
import com.papichulo.intervaltrainer.model.PhaseKind
import com.papichulo.intervaltrainer.model.TimerConfig
import com.papichulo.intervaltrainer.ui.theme.TrainerTheme
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

private fun formatSeconds(totalSeconds: Int): String {
    val s = max(0, totalSeconds)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private fun labelFor(kind: PhaseKind) = when (kind) {
    PhaseKind.WALK -> "Caminar"
    PhaseKind.RUN -> "Correr"
    PhaseKind.REST -> "Descanso"
}

private fun newPhaseId() = "p${System.currentTimeMillis()}${Random.nextInt(1000)}"

@Composable
fun TimerScreen(
    state: TimerState,
    speed: SpeedStats,
    onPlayPause: () -> Unit,
    onReset: () -> Unit,
    onSkip: (Int) -> Unit,
    onUpdateConfig: (TimerConfig) -> Unit
) {
    val colors = TrainerTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        TopBar(soundOn = state.config.soundOn) {
            onUpdateConfig(state.config.copy(soundOn = !state.config.soundOn))
        }

        TimerCard(state, speed, onPlayPause, onReset, onSkip)

        EditorCard(
            config = state.config,
            enabled = !state.running,
            onUpdateConfig = onUpdateConfig
        )

        Text(
            "Tu configuración se guarda en este dispositivo.",
            style = TextStyle(fontSize = 11.5.sp, color = colors.onSurfaceDim),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TopBar(soundOn: Boolean, onToggleSound: () -> Unit) {
    val colors = TrainerTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "ENTRENADOR DE INTERVALOS",
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
            color = colors.onSurfaceDim
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .clickable(onClick = onToggleSound),
            contentAlignment = Alignment.Center
        ) {
            Text(if (soundOn) "🔔" else "🔕", fontSize = 17.sp)
        }
    }
}

@Composable
private fun TimerCard(
    state: TimerState,
    speed: SpeedStats,
    onPlayPause: () -> Unit,
    onReset: () -> Unit,
    onSkip: (Int) -> Unit
) {
    val colors = TrainerTheme.colors
    val phase = state.currentPhase
    val accent = colors.accentFor(phase.kind)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val pillLabel = if (state.finished) "Completado" else phase.name.ifBlank { labelFor(phase.kind) }
        val pillColor = if (state.finished) colors.walk else accent
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(pillColor)
                .padding(horizontal = 18.dp, vertical = 7.dp)
        ) {
            Text(pillLabel, color = colors.onAccentInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Box(modifier = Modifier.widthIn(max = 248.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            val fraction = if (state.finished) 1f else {
                val total = (phase.seconds * 1000L).coerceAtLeast(1L)
                (1f - state.remainingMs.toFloat() / total).coerceIn(0f, 1f)
            }
            val ringColor = if (state.finished) colors.walk else accent
            val trackColor = colors.surfaceVariant
            Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke,
                    size = Size(size.width, size.height)
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    style = stroke,
                    size = Size(size.width, size.height)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val secLeft = ceil(state.remainingMs / 1000.0 - 0.0001).toInt().coerceAtLeast(0)
                Text(
                    if (state.finished) "🎉" else formatSeconds(secLeft),
                    style = TextStyle(
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = colors.onBackground
                )
                Text(
                    "Ronda ${state.currentRound} de ${state.config.rounds}",
                    fontSize = 13.sp,
                    color = colors.onSurfaceDim
                )
            }
        }

        val nextPhase = state.nextPhase
        Text(
            if (state.finished) "Entrenamiento terminado"
            else if (nextPhase != null) "Siguiente: ${nextPhase.name.ifBlank { labelFor(nextPhase.kind) }} · ${formatSeconds(nextPhase.seconds)}"
            else "Última fase del entrenamiento",
            fontSize = 13.sp,
            color = colors.onSurfaceDim
        )

        if (state.config.speedOn) SpeedPanel(speed)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CtrlButton(symbol = "⏮", size = 52.dp, bg = colors.surfaceVariant, fg = colors.onBackground, onClick = { onSkip(-1) })
            CtrlButton(
                symbol = if (state.running) "⏸" else "▶",
                size = 68.dp,
                bg = accent,
                fg = colors.onAccentInk,
                onClick = onPlayPause,
                enabled = !state.finished
            )
            CtrlButton(symbol = "⏭", size = 52.dp, bg = colors.surfaceVariant, fg = colors.onBackground, onClick = { onSkip(1) })
        }

        Text(
            "Reiniciar",
            color = colors.onSurfaceDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onReset).padding(4.dp)
        )

        Text(
            "Duración total ${formatSeconds(state.totalWorkoutSeconds)}",
            fontSize = 12.sp,
            color = colors.onSurfaceDim,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

private fun formatKmh(v: Double?) = if (v == null) "–" else "%.1f".format(v)

@Composable
private fun SpeedPanel(speed: SpeedStats) {
    val colors = TrainerTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceVariant)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SpeedCell("Ahora", speed.currentKmh, modifier = Modifier.weight(1f))
            SpeedCell("Actual", speed.roundAvgKmh, modifier = Modifier.weight(1f))
            SpeedCell("Anterior", speed.prevRoundAvgKmh, modifier = Modifier.weight(1f))
        }
        Text(
            "Media total ${formatKmh(speed.totalAvgKmh)} km/h · ${"%.2f".format(speed.distanceM / 1000.0)} km",
            fontSize = 12.5.sp,
            color = colors.onSurfaceDim,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}

@Composable
private fun SpeedCell(label: String, value: Double?, modifier: Modifier = Modifier) {
    val colors = TrainerTheme.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            formatKmh(value),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
        Text("km/h", fontSize = 10.5.sp, color = colors.onSurfaceDim)
        Text(label, fontSize = 11.5.sp, color = colors.onSurfaceDim, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CtrlButton(symbol: String, size: androidx.compose.ui.unit.Dp, bg: Color, fg: Color, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(if (size > 60.dp) 20.dp else 16.dp))
            .background(bg)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = if (size > 60.dp) 22.sp else 18.sp, color = fg)
    }
}

@Composable
private fun EditorCard(
    config: TimerConfig,
    enabled: Boolean,
    onUpdateConfig: (TimerConfig) -> Unit
) {
    val colors = TrainerTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surface)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Series", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Text(
            "Define cada fase y cuántas veces se repite la secuencia completa. Pausa el entrenamiento para poder editar.",
            fontSize = 12.5.sp,
            color = colors.onSurfaceDim
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            config.phases.forEachIndexed { index, phase ->
                PhaseRow(
                    phase = phase,
                    enabled = enabled,
                    canMoveUp = index > 0,
                    canMoveDown = index < config.phases.lastIndex,
                    canDelete = config.phases.size > 1,
                    onChange = { updated ->
                        onUpdateConfig(config.copy(phases = config.phases.toMutableList().also { it[index] = updated }))
                    },
                    onMoveUp = {
                        val mutable = config.phases.toMutableList()
                        val tmp = mutable[index - 1]; mutable[index - 1] = mutable[index]; mutable[index] = tmp
                        onUpdateConfig(config.copy(phases = mutable))
                    },
                    onMoveDown = {
                        val mutable = config.phases.toMutableList()
                        val tmp = mutable[index + 1]; mutable[index + 1] = mutable[index]; mutable[index] = tmp
                        onUpdateConfig(config.copy(phases = mutable))
                    },
                    onDelete = {
                        onUpdateConfig(config.copy(phases = config.phases.filterIndexed { i, _ -> i != index }))
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onUpdateConfig(config.copy(speedOn = !config.speedOn)) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = config.speedOn,
                onCheckedChange = null,
                enabled = enabled,
                colors = CheckboxDefaults.colors(checkedColor = colors.walk)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Medir velocidad (GPS)", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
                Text("Desactívalo para gimnasio o estudiar", fontSize = 11.5.sp, color = colors.onSurfaceDim)
            }
        }

        Text(
            "+ Añadir fase",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceDim,
            modifier = Modifier
                .clickable(enabled = enabled) {
                    onUpdateConfig(
                        config.copy(
                            phases = config.phases + Phase(newPhaseId(), "Descanso", 30, PhaseKind.REST)
                        )
                    )
                }
                .padding(vertical = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Rondas", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.onBackground)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepButton("–", enabled) { onUpdateConfig(config.copy(rounds = (config.rounds - 1).coerceAtLeast(1))) }
                Text(
                    "${config.rounds}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
                StepButton("+", enabled) { onUpdateConfig(config.copy(rounds = (config.rounds + 1).coerceAtMost(99))) }
            }
        }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = TrainerTheme.colors
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(colors.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = 15.sp, color = colors.onBackground)
    }
}

@Composable
private fun PhaseRow(
    phase: Phase,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    onChange: (Phase) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = TrainerTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = phase.name,
            onValueChange = { onChange(phase.copy(name = it.take(24))) },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            textStyle = TextStyle(fontSize = 14.sp),
            colors = fieldColors()
        )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(PhaseKind.WALK, PhaseKind.RUN, PhaseKind.REST).forEach { kind ->
                val dotColor = colors.accentFor(kind)
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = if (phase.kind == kind) 1f else 0.35f))
                        .clickable(enabled = enabled) { onChange(phase.copy(kind = kind)) }
                )
            }
        }

        val minutes = phase.seconds / 60
        val secs = phase.seconds % 60
        OutlinedTextField(
            value = minutes.toString(),
            onValueChange = { text ->
                val m = text.filter(Char::isDigit).take(2).toIntOrNull() ?: 0
                onChange(phase.copy(seconds = (m.coerceIn(0, 59) * 60 + secs).coerceAtLeast(1)))
            },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.width(48.dp).height(52.dp),
            textStyle = TextStyle(fontSize = 13.5.sp, textAlign = TextAlign.Center),
            colors = fieldColors()
        )
        Text(":", color = colors.onSurfaceDim, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = secs.toString().padStart(2, '0'),
            onValueChange = { text ->
                val s = text.filter(Char::isDigit).take(2).toIntOrNull() ?: 0
                onChange(phase.copy(seconds = (minutes * 60 + s.coerceIn(0, 59)).coerceAtLeast(1)))
            },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            modifier = Modifier.width(48.dp).height(52.dp),
            textStyle = TextStyle(fontSize = 13.5.sp, textAlign = TextAlign.Center),
            colors = fieldColors()
        )

        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            MiniButton("↑", enabled && canMoveUp, onMoveUp)
            MiniButton("↓", enabled && canMoveDown, onMoveDown)
            MiniButton("✕", enabled && canDelete, onDelete, danger = true)
        }
    }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TrainerTheme.colors.walk,
    unfocusedBorderColor = TrainerTheme.colors.border,
    focusedContainerColor = TrainerTheme.colors.surface,
    unfocusedContainerColor = TrainerTheme.colors.surface,
    focusedTextColor = TrainerTheme.colors.onBackground,
    unfocusedTextColor = TrainerTheme.colors.onBackground
)

@Composable
private fun MiniButton(symbol: String, enabled: Boolean, onClick: () -> Unit, danger: Boolean = false) {
    val colors = TrainerTheme.colors
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = 11.sp, color = if (danger) colors.danger else colors.onSurfaceDim)
    }
}
