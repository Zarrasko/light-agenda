package com.thelightphone.pulse

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import java.util.Base64

// Reads/writes the one encrypted API key in DataStore. Unlike an OAuth token, this never
// rotates, so there's nothing more to track than the key itself.
internal class PulseCredentialsRepository(
    private val dataStore: DataStore<Preferences>,
    private val cipher: PulseCredentialCipher = PulseCredentialCipher(),
) {
    suspend fun load(): String? {
        val encoded = dataStore.data.first()[PulsePreferences.ENCRYPTED_API_KEY] ?: return null
        return runCatching {
            cipher.decrypt(Base64.getDecoder().decode(encoded))
        }.getOrNull()
    }

    suspend fun save(apiKey: String) {
        val encoded = Base64.getEncoder().encodeToString(cipher.encrypt(apiKey))
        dataStore.edit { prefs -> prefs[PulsePreferences.ENCRYPTED_API_KEY] = encoded }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(PulsePreferences.ENCRYPTED_API_KEY)
            prefs.remove(PulsePreferences.CACHED_ACTIVITIES_JSON)
            prefs.remove(PulsePreferences.CACHED_ACTIVITIES_SYNCED_AT)
        }
    }
}
