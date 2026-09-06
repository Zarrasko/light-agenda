package com.thelightphone.agenda

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

internal object AgendaPreferences {
    val CALENDARS_JSON = stringPreferencesKey("calendars_json")
    val CACHED_EVENTS_JSON = stringPreferencesKey("cached_events_json")
    val CACHED_EVENTS_SYNCED_AT = stringPreferencesKey("cached_events_synced_at")

    // Events already notified about, as "uid|startEpochMillis" keys - a recurring event's uid
    // repeats across occurrences, so the key has to include the occurrence's own start time.
    val NOTIFIED_EVENT_KEYS_JSON = stringPreferencesKey("notified_event_keys_json")

    // Shown once, right after the first calendar is added - never nag again after that,
    // whichever way the user answered it.
    val HAS_PROMPTED_REMINDERS = booleanPreferencesKey("has_prompted_reminders")
}
