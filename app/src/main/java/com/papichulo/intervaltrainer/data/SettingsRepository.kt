// Copyright (C) 2026 famphuelva
// SPDX-License-Identifier: GPL-3.0-or-later

package com.papichulo.intervaltrainer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.papichulo.intervaltrainer.model.TimerConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "interval_trainer_settings")

/** Persists the user's configured phases/rounds/sound so they survive app restarts. */
class SettingsRepository(private val context: Context) {

    private val configKey = stringPreferencesKey("timer_config_json")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): TimerConfig {
        val raw = context.dataStore.data.map { it[configKey] }.first()
        return raw?.let { runCatching { json.decodeFromString<TimerConfig>(it) }.getOrNull() }
            ?: TimerConfig.default()
    }

    suspend fun save(config: TimerConfig) {
        context.dataStore.edit { it[configKey] = json.encodeToString(config) }
    }
}
