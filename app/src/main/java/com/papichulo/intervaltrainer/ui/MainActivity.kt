// Copyright (C) 2026 famphuelva
// SPDX-License-Identifier: GPL-3.0-or-later

package com.papichulo.intervaltrainer.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.papichulo.intervaltrainer.service.TimerForegroundService
import com.papichulo.intervaltrainer.ui.theme.IntervalTrainerTheme
import com.papichulo.intervaltrainer.ui.theme.TrainerTheme

class MainActivity : ComponentActivity() {

    private var boundService by mutableStateOf<TimerForegroundService?>(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            boundService = (binder as TimerForegroundService.LocalBinder).service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
        }
    }

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        requestPermissions.launch(wanted.toTypedArray())

        bindService(
            Intent(this, TimerForegroundService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )

        setContent {
            IntervalTrainerTheme {
                val background = TrainerTheme.colors.background
                Box(modifier = Modifier.fillMaxSize().background(background)) {
                    val service = boundService
                    if (service != null) {
                        val state by service.engine.state.collectAsState()
                        val speed by service.speedTracker.stats.collectAsState()
                        TimerScreen(
                            state = state,
                            speed = speed,
                            onPlayPause = { if (state.running) service.pause() else service.play() },
                            onReset = service::reset,
                            onSkip = service::skip,
                            onUpdateConfig = service::updateConfig
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        unbindService(connection)
        super.onDestroy()
    }
}
