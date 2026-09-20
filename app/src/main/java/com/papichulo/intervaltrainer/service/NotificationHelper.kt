// Copyright (C) 2026 famphuelva
// SPDX-License-Identifier: GPL-3.0-or-later

package com.papichulo.intervaltrainer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.papichulo.intervaltrainer.R
import com.papichulo.intervaltrainer.engine.TimerState
import com.papichulo.intervaltrainer.ui.MainActivity

object NotificationHelper {
    const val CHANNEL_ID = "interval_trainer_running"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    fun build(context: Context, state: TimerState): android.app.Notification {
        val contentPendingIntent = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(Intent(context, MainActivity::class.java))
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val title = if (state.finished) {
            "Entrenamiento completado"
        } else {
            "${state.currentPhase.name} · Ronda ${state.currentRound} de ${state.config.rounds}"
        }
        val secondsLeft = ((state.remainingMs + 999) / 1000).toInt().coerceAtLeast(0)
        val text = if (state.finished) {
            "Toca para abrir la app"
        } else {
            "%d:%02d restantes".format(secondsLeft / 60, secondsLeft % 60)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(state.running)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (!state.finished) {
            val toggleAction = if (state.running) {
                NotificationCompat.Action(
                    0, context.getString(R.string.action_pause),
                    servicePendingIntent(context, TimerForegroundService.ACTION_PAUSE)
                )
            } else {
                NotificationCompat.Action(
                    0, context.getString(R.string.action_resume),
                    servicePendingIntent(context, TimerForegroundService.ACTION_PLAY)
                )
            }
            builder.addAction(toggleAction)
            builder.addAction(
                NotificationCompat.Action(
                    0, context.getString(R.string.action_skip),
                    servicePendingIntent(context, TimerForegroundService.ACTION_SKIP)
                )
            )
        }

        return builder.build()
    }

    private fun servicePendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, TimerForegroundService::class.java).setAction(action)
        return PendingIntent.getService(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
