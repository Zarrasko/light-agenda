package com.thelightphone.agenda

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.temporal.ChronoUnit

sealed class AgendaScreenMode {
    data class Loading(val message: String) : AgendaScreenMode()

    // No calendars configured yet - distinct from Agenda(emptyList()) so the empty state can
    // point straight at "add a calendar" instead of implying a synced-but-quiet agenda.
    object Empty : AgendaScreenMode()

    data class Agenda(
        val dayGroups: List<DayGroup>,
        val lastSyncedLabel: String?,
        val hadSourceErrors: Boolean,
    ) : AgendaScreenMode()
}

data class DayGroup(val label: String, val events: List<AgendaEvent>)

data class AgendaUiState(
    val mode: AgendaScreenMode = AgendaScreenMode.Loading("checking calendars..."),
    val errorModal: String? = null,
)

private const val NETWORK_ERROR_MESSAGE =
    "Agenda requires a network connection. Please insert a data sim or connect to wi-fi and try again."

class AgendaViewModel(
    private val dataStore: DataStore<Preferences>,
) : LightViewModel<Unit>() {
    private val api = AgendaApi()
    private val sourcesRepository = AgendaSourcesRepository(dataStore)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val _uiState = MutableStateFlow(AgendaUiState())
    val uiState: StateFlow<AgendaUiState> = _uiState.asStateFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, _ ->
        viewModelScope.launch(Dispatchers.Main) {
            _uiState.update { it.copy(errorModal = NETWORK_ERROR_MESSAGE) }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            loadCachedEventsIfAny()
            refresh()
        }
    }

    private suspend fun loadCachedEventsIfAny() {
        val prefs = dataStore.data.first()
        val cachedJson = prefs[AgendaPreferences.CACHED_EVENTS_JSON] ?: return
        val syncedAt = prefs[AgendaPreferences.CACHED_EVENTS_SYNCED_AT]
        val events = runCatching { json.decodeFromString<List<AgendaEvent>>(cachedJson) }.getOrNull()
        if (!events.isNullOrEmpty()) {
            _uiState.update { it.copy(mode = buildAgendaMode(events, syncedAt, hadSourceErrors = false)) }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            val sources = sourcesRepository.load()
            if (sources.isEmpty()) {
                _uiState.update { it.copy(mode = AgendaScreenMode.Empty, errorModal = null) }
                return@launch
            }

            val hadCached = _uiState.value.mode is AgendaScreenMode.Agenda
            if (!hadCached) {
                _uiState.update { it.copy(mode = AgendaScreenMode.Loading("loading agenda..."), errorModal = null) }
            }

            val windowStart = Instant.now().truncatedTo(ChronoUnit.DAYS)
            val windowEnd = windowStart.plus(AGENDA_WINDOW_DAYS, ChronoUnit.DAYS)

            val (events, hadSourceErrors) = fetchAllEvents(api, sources, windowStart, windowEnd)

            if (events.isEmpty() && hadSourceErrors && !hadCached) {
                _uiState.update { it.copy(mode = AgendaScreenMode.Empty, errorModal = NETWORK_ERROR_MESSAGE) }
                return@launch
            }

            val syncedAt = nowLabel()
            runCatching {
                dataStore.edit { prefs ->
                    prefs[AgendaPreferences.CACHED_EVENTS_JSON] = json.encodeToString(events)
                    prefs[AgendaPreferences.CACHED_EVENTS_SYNCED_AT] = syncedAt
                }
            }
            _uiState.update { it.copy(mode = buildAgendaMode(events, syncedAt, hadSourceErrors), errorModal = null) }
        }
    }

    private fun buildAgendaMode(
        events: List<AgendaEvent>,
        lastSyncedLabel: String?,
        hadSourceErrors: Boolean,
    ): AgendaScreenMode {
        val groups = events.groupBy { it.dayLabel() }.entries
            .sortedBy { entry -> entry.value.first().startEpochMillis }
            .map { (label, dayEvents) -> DayGroup(label, dayEvents.sortedBy { it.startEpochMillis }) }
        return AgendaScreenMode.Agenda(groups, lastSyncedLabel, hadSourceErrors)
    }

    fun dismissError() {
        _uiState.update { it.copy(errorModal = null) }
    }

    private fun nowLabel(): String =
        java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())

    override fun onCleared() {
        super.onCleared()
        api.close()
    }
}
