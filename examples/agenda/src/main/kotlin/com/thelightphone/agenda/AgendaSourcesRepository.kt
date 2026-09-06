package com.thelightphone.agenda

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

// Configured calendar feeds - stored as plain JSON, same as Pulse caches its activities list.
// An ICS URL is a bearer capability (whoever has it can read the calendar), same as any
// subscribe link a stock calendar app stores, but it's not a login credential worth the
// extra complexity of Pulse's AES-encrypted API key storage.
internal class AgendaSourcesRepository(private val dataStore: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun load(): List<CalendarSource> {
        val raw = dataStore.data.first()[AgendaPreferences.CALENDARS_JSON] ?: return emptyList()
        return runCatching { json.decodeFromString<List<CalendarSource>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun add(label: String, icsUrl: String) {
        val updated = load() + CalendarSource(id = UUID.randomUUID().toString(), label = label, icsUrl = icsUrl)
        save(updated)
    }

    suspend fun remove(id: String) {
        save(load().filterNot { it.id == id })
    }

    private suspend fun save(sources: List<CalendarSource>) {
        dataStore.edit { prefs -> prefs[AgendaPreferences.CALENDARS_JSON] = json.encodeToString(sources) }
    }
}
