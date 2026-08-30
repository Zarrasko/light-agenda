package com.thelightphone.pulse

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
import kotlinx.serialization.json.Json

sealed class PulseScreenMode {
    data class Loading(val message: String) : PulseScreenMode()

    // Exclusively the disconnected/first-run flow - never reused for a connected state, so it
    // never risks looking like "you've been signed out."
    data class Setup(
        val apiKey: String = "",
        val editingKey: Boolean = false,
        val editSession: Int = 0,
        val isConnecting: Boolean = false,
    ) : PulseScreenMode()

    data class Activities(
        val weekSummary: WeekSummary,
        val activities: List<SummaryActivity>,
        val wellness: Wellness?,
        val gearSummary: String?,
        val lastSyncedLabel: String?,
        val isSample: Boolean = false,
    ) : PulseScreenMode()

    // Account view for an already-connected key.
    data class Settings(
        val apiKey: String,
        val lastSyncedLabel: String?,
    ) : PulseScreenMode()
}

// Which glanceable fields the home screen shows - all default to shown, edited from the
// Customize Data Fields submenu off Settings.
data class DataFieldPreferences(
    val showRestingHR: Boolean = true,
    val showSleep: Boolean = true,
    val showHRV: Boolean = true,
    val showSteps: Boolean = true,
    val showFitnessTrend: Boolean = true,
    val showGear: Boolean = true,
)

data class PulseUiState(
    val mode: PulseScreenMode = PulseScreenMode.Loading("checking connection..."),
    val fieldPreferences: DataFieldPreferences = DataFieldPreferences(),
    val errorModal: String? = null,
    val infoModal: String? = null,
)

private const val NETWORK_ERROR_MESSAGE =
    "Pulse requires a network connection. Please insert a data sim or connect to wi-fi and try again."

class PulseViewModel(
    private val dataStore: DataStore<Preferences>,
) : LightViewModel<Unit>() {
    private val api = PulseApi()
    private val credentialsRepository = PulseCredentialsRepository(dataStore)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val _uiState = MutableStateFlow(PulseUiState())
    val uiState: StateFlow<PulseUiState> = _uiState.asStateFlow()

    private val apiExceptionHandler = CoroutineExceptionHandler { _, _ ->
        viewModelScope.launch(Dispatchers.Main) { showApiFailure() }
    }

    init {
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            val apiKey = credentialsRepository.load()
            if (apiKey == null) {
                _uiState.update { it.copy(mode = PulseScreenMode.Setup()) }
                return@launch
            }
            loadCachedActivitiesIfAny()
            syncActivities(apiKey)
        }
    }

    private suspend fun loadCachedActivitiesIfAny() {
        val prefs = dataStore.data.first()
        val cachedJson = prefs[PulsePreferences.CACHED_ACTIVITIES_JSON] ?: return
        val syncedAt = prefs[PulsePreferences.CACHED_ACTIVITIES_SYNCED_AT]
        val activities = runCatching { json.decodeFromString<List<SummaryActivity>>(cachedJson) }.getOrNull()
        if (!activities.isNullOrEmpty()) {
            _uiState.update {
                it.copy(mode = activitiesMode(activities, wellness = null, gearSummary = null, lastSyncedLabel = syncedAt))
            }
        }
    }

    private fun activitiesMode(
        activities: List<SummaryActivity>,
        wellness: Wellness?,
        gearSummary: String?,
        lastSyncedLabel: String?,
        isSample: Boolean = false,
    ) = PulseScreenMode.Activities(
        weekSummary = activities.weekSummary(System.currentTimeMillis() / 1000),
        activities = activities,
        wellness = wellness,
        gearSummary = gearSummary,
        lastSyncedLabel = lastSyncedLabel,
        isSample = isSample,
    )

    // Lets the setup screen be reviewed end-to-end before anyone has a real API key entered.
    // Deliberately not persisted - reopening the tool (or connecting for real) drops it.
    fun previewSampleData() {
        _uiState.update {
            it.copy(
                mode = activitiesMode(
                    sampleActivities(),
                    sampleWellness(),
                    gearSummary = gearSummaryText(sampleGear()),
                    lastSyncedLabel = null,
                    isSample = true,
                ),
                errorModal = null,
            )
        }
    }

    private suspend fun loadFieldPreferences(): DataFieldPreferences {
        val prefs = dataStore.data.first()
        return DataFieldPreferences(
            showRestingHR = prefs[PulsePreferences.SHOW_RESTING_HR] ?: true,
            showSleep = prefs[PulsePreferences.SHOW_SLEEP] ?: true,
            showHRV = prefs[PulsePreferences.SHOW_HRV] ?: true,
            showSteps = prefs[PulsePreferences.SHOW_STEPS] ?: true,
            showFitnessTrend = prefs[PulsePreferences.SHOW_FITNESS_TREND] ?: true,
            showGear = prefs[PulsePreferences.SHOW_GEAR] ?: true,
        )
    }

    private suspend fun syncActivities(apiKey: String) {
        val hadCachedActivities = _uiState.value.mode is PulseScreenMode.Activities
        if (!hadCachedActivities) {
            _uiState.update { it.copy(mode = PulseScreenMode.Loading("loading activities..."), errorModal = null) }
        }
        // Re-read every time (not just once at startup) so toggling a field in Customize Data
        // Fields and coming back here takes effect immediately.
        val fieldPreferences = loadFieldPreferences()
        _uiState.update { it.copy(fieldPreferences = fieldPreferences) }

        api.fetchActivities(apiKey).fold(
            onSuccess = { activities ->
                // Wellness/gear can legitimately be empty (nothing synced yet, no gear logged)
                // - never let either block showing the activities that did load.
                val wellness = api.fetchTodayWellness(apiKey).getOrNull()?.takeIf { it.hasAnyData }
                val gearSummary = api.fetchGear(apiKey).getOrNull()?.let { gearSummaryText(it) }
                val syncedAt = nowLabel()
                runCatching {
                    dataStore.edit { prefs ->
                        prefs[PulsePreferences.CACHED_ACTIVITIES_JSON] = json.encodeToString(activities)
                        prefs[PulsePreferences.CACHED_ACTIVITIES_SYNCED_AT] = syncedAt
                    }
                }
                _uiState.update {
                    it.copy(mode = activitiesMode(activities, wellness, gearSummary, syncedAt), errorModal = null)
                }
            },
            onFailure = { error ->
                // Setup() here would look identical to "never connected," even though the key
                // is still safely stored - Settings makes that unambiguous instead.
                val recoveryMode = if (hadCachedActivities) {
                    null
                } else {
                    PulseScreenMode.Settings(apiKey = apiKey, lastSyncedLabel = null)
                }
                showApiFailure(error, recoveryMode = recoveryMode)
            },
        )
    }

    private fun gearSummaryText(gear: List<Gear>): String = when {
        gear.isEmpty() -> "No gear logged yet"
        gear.size == 1 -> "1 item"
        else -> "${gear.size} items"
    }

    fun openEditor() {
        _uiState.update { state ->
            val setup = state.mode as? PulseScreenMode.Setup ?: return@update state
            state.copy(mode = setup.copy(editingKey = true, editSession = setup.editSession + 1))
        }
    }

    fun submitKey(value: String) {
        _uiState.update { state ->
            val setup = state.mode as? PulseScreenMode.Setup ?: return@update state
            state.copy(mode = setup.copy(apiKey = value.trim(), editingKey = false))
        }
    }

    fun cancelEditor() {
        _uiState.update { state ->
            val setup = state.mode as? PulseScreenMode.Setup ?: return@update state
            state.copy(mode = setup.copy(editingKey = false))
        }
    }

    fun onQrScanned(key: String?) {
        if (key == null) {
            _uiState.update { it.copy(errorModal = "That QR code doesn't look like an intervals.icu API key.") }
            return
        }
        _uiState.update { state ->
            val setup = state.mode as? PulseScreenMode.Setup ?: return@update state
            state.copy(mode = setup.copy(apiKey = key))
        }
        connect()
    }

    fun connect() {
        val setup = _uiState.value.mode as? PulseScreenMode.Setup ?: return
        if (setup.apiKey.isBlank()) {
            _uiState.update { it.copy(errorModal = "Enter your API key, or scan a QR code, before connecting.") }
            return
        }

        _uiState.update { it.copy(mode = setup.copy(isConnecting = true), errorModal = null) }

        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            api.fetchActivities(setup.apiKey).fold(
                onSuccess = {
                    credentialsRepository.save(setup.apiKey)
                    syncActivities(setup.apiKey)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            mode = setup.copy(isConnecting = false),
                            errorModal = "Couldn't connect: ${error.message ?: "check your API key"}",
                        )
                    }
                },
            )
        }
    }

    fun refreshActivities() {
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            val apiKey = credentialsRepository.load() ?: return@launch
            syncActivities(apiKey)
        }
    }

    fun showFitnessInfo() {
        val wellness = (_uiState.value.mode as? PulseScreenMode.Activities)?.wellness ?: return
        _uiState.update { it.copy(infoModal = wellness.fitnessTrendExplanation()) }
    }

    fun dismissInfo() {
        _uiState.update { it.copy(infoModal = null) }
    }

    fun openSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val apiKey = credentialsRepository.load() ?: return@launch
            val lastSyncedLabel = (_uiState.value.mode as? PulseScreenMode.Activities)?.lastSyncedLabel
            _uiState.update {
                it.copy(mode = PulseScreenMode.Settings(apiKey = apiKey, lastSyncedLabel = lastSyncedLabel), errorModal = null)
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            credentialsRepository.clear()
            _uiState.update { it.copy(mode = PulseScreenMode.Setup(), errorModal = null) }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorModal = null) }
    }

    private fun showApiFailure(error: Throwable? = null, recoveryMode: PulseScreenMode? = null) {
        _uiState.update {
            it.copy(
                mode = recoveryMode ?: it.mode,
                errorModal = NETWORK_ERROR_MESSAGE,
            )
        }
    }

    private fun nowLabel(): String =
        java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())

    override fun onBackPressed(): Boolean {
        val mode = _uiState.value.mode
        return when {
            mode is PulseScreenMode.Setup && mode.editingKey -> {
                cancelEditor()
                true
            }
            mode is PulseScreenMode.Settings -> {
                refreshActivities()
                true
            }
            else -> false
        }
    }

    override fun onCleared() {
        super.onCleared()
        api.close()
    }
}
