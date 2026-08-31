package com.thelightphone.pulse

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

internal object PulsePreferences {
    // Base64 of an AES/GCM-encrypted API key - see PulseCredentialCipher.
    val ENCRYPTED_API_KEY = stringPreferencesKey("encrypted_api_key")
    val CACHED_ACTIVITIES_JSON = stringPreferencesKey("cached_activities_json")
    val CACHED_ACTIVITIES_SYNCED_AT = stringPreferencesKey("cached_activities_synced_at")

    // Which glanceable fields show on the home screen - all default to shown (absent = true).
    val SHOW_RESTING_HR = booleanPreferencesKey("show_resting_hr")
    val SHOW_SLEEP = booleanPreferencesKey("show_sleep")
    val SHOW_HRV = booleanPreferencesKey("show_hrv")
    val SHOW_STEPS = booleanPreferencesKey("show_steps")
    val SHOW_FITNESS_TREND = booleanPreferencesKey("show_fitness_trend")
    val SHOW_GEAR = booleanPreferencesKey("show_gear")
    val SHOW_UPCOMING = booleanPreferencesKey("show_upcoming")
}
