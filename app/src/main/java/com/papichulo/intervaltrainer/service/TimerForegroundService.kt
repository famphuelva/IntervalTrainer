// Copyright (C) 2026 famphuelva
// SPDX-License-Identifier: GPL-3.0-or-later

package com.papichulo.intervaltrainer.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.app.ServiceCompat
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.papichulo.intervaltrainer.data.SettingsRepository
import com.papichulo.intervaltrainer.engine.SpeedTracker
import com.papichulo.intervaltrainer.engine.TickEvent
import com.papichulo.intervaltrainer.engine.TimerEngine
import com.papichulo.intervaltrainer.engine.TimerState
import com.papichulo.intervaltrainer.model.PhaseKind
import com.papichulo.intervaltrainer.model.TimerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the countdown ticking (and the notification/audio/vibration cues firing) while the
 * screen is off or the app is backgrounded — a plain Handler in the Activity would freeze
 * under Doze, which defeats the whole point of a workout timer worn on an armband.
 */
class TimerForegroundService : Service() {

    inner class LocalBinder : android.os.Binder() {
        val service: TimerForegroundService get() = this@TimerForegroundService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickJob: Job? = null
    private var lastNotifiedSecond: Int? = null

    private lateinit var settingsRepository: SettingsRepository
    lateinit var engine: TimerEngine
        private set

    val speedTracker = SpeedTracker()
    private var locationListener: LocationListener? = null

    private lateinit var toneGenerator: ToneGenerator
    private lateinit var vibrator: Vibrator

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
        engine = TimerEngine(TimerConfig.default())
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        NotificationHelper.ensureChannel(applicationContext)

        scope.launch {
            engine.updateConfig(settingsRepository.load())
        }
        startTickLoop()
    }

    private fun startTickLoop() {
        tickJob = scope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(200)
                val now = SystemClock.elapsedRealtime()
                val delta = now - last
                last = now
                val wasRunning = engine.state.value.running
                val events = engine.tick(delta)
                events.forEach(::handleEvent)
                val after = engine.state.value
                speedTracker.setRound(after.currentRound)
                if (wasRunning) speedTracker.addTime(delta)
                syncForegroundState()
            }
        }
    }

    private fun handleEvent(event: TickEvent) {
        val soundOn = engine.state.value.config.soundOn
        when (event) {
            is TickEvent.PhaseChanged -> {
                if (soundOn) toneGenerator.startTone(toneForKind(event.phase.kind), 180)
                vibrate(if (event.phase.kind == PhaseKind.RUN) longArrayOf(0, 70, 50, 70) else longArrayOf(0, 130))
            }
            is TickEvent.CountdownBeep -> {
                if (soundOn) toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
            }
            TickEvent.Finished -> {
                stopLocationUpdates()
                if (soundOn) toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                vibrate(longArrayOf(0, 120, 80, 120, 80, 220))
            }
        }
    }

    private fun toneForKind(kind: PhaseKind) = when (kind) {
        PhaseKind.WALK -> ToneGenerator.TONE_PROP_ACK
        PhaseKind.RUN -> ToneGenerator.TONE_PROP_BEEP2
        PhaseKind.REST -> ToneGenerator.TONE_PROP_PROMPT
    }

    private fun vibrate(pattern: LongArray) {
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    // ---- commands exposed to the bound Activity / notification actions ----

    fun play() {
        ContextCompat.startForegroundService(applicationContext, Intent(applicationContext, TimerForegroundService::class.java))
        engine.play()
        startLocationUpdates()
        syncForegroundState(force = true)
    }

    fun pause() {
        engine.pause()
        stopLocationUpdates()
        syncForegroundState(force = true)
    }

    fun skip(direction: Int) {
        engine.skip(direction)
        syncForegroundState(force = true)
    }

    fun reset() {
        engine.reset()
        stopLocationUpdates()
        speedTracker.reset()
        syncForegroundState(force = true)
    }

    fun updateConfig(config: TimerConfig) {
        engine.updateConfig(config)
        stopLocationUpdates()
        speedTracker.reset()
        scope.launch { settingsRepository.save(config) }
        syncForegroundState(force = true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_SKIP -> skip(1)
        }
        syncForegroundState(force = true)
        return START_STICKY
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (locationListener != null || !engine.state.value.config.speedOn || !hasLocationPermission()) return
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val listener = LocationListener { loc: Location ->
            speedTracker.onLocation(loc.latitude, loc.longitude, loc.accuracy, loc.elapsedRealtimeNanos / 1_000_000L)
        }
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            locationListener = listener
        } catch (_: Exception) {
            // GPS provider unavailable: the timer keeps working, speeds just stay empty.
        }
    }

    private fun stopLocationUpdates() {
        val listener = locationListener ?: return
        (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(listener)
        locationListener = null
        speedTracker.breakSegment()
    }

    private fun startForegroundSafely(notification: android.app.Notification) {
        val base = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        val withLocation = base or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        try {
            ServiceCompat.startForeground(
                this, NotificationHelper.NOTIFICATION_ID, notification,
                if (hasLocationPermission()) withLocation else base
            )
        } catch (_: Exception) {
            // Location-type FGS can be refused when started from the background; fall back.
            ServiceCompat.startForeground(this, NotificationHelper.NOTIFICATION_ID, notification, base)
        }
    }

    private fun isIdle(s: TimerState) =
        !s.running && !s.finished && s.segmentIndex == 0 &&
            s.remainingMs == s.currentPhase.seconds * 1000L

    private fun syncForegroundState(force: Boolean = false) {
        val s = engine.state.value
        val secLeft = ((s.remainingMs + 999) / 1000).toInt()
        if (!force && secLeft == lastNotifiedSecond) return
        lastNotifiedSecond = secLeft

        when {
            isIdle(s) -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            s.finished -> {
                stopForeground(STOP_FOREGROUND_DETACH)
                NotificationManagerCompat.from(applicationContext)
                    .notify(NotificationHelper.NOTIFICATION_ID, NotificationHelper.build(applicationContext, s))
            }
            else -> {
                startForegroundSafely(NotificationHelper.build(applicationContext, s))
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        stopLocationUpdates()
        tickJob?.cancel()
        scope.cancel()
        toneGenerator.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "com.papichulo.intervaltrainer.action.PLAY"
        const val ACTION_PAUSE = "com.papichulo.intervaltrainer.action.PAUSE"
        const val ACTION_SKIP = "com.papichulo.intervaltrainer.action.SKIP"
    }
}
