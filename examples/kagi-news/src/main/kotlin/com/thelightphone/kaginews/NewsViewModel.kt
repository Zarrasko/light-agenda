package com.thelightphone.kaginews

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

sealed class NewsScreenMode {
    data class Loading(val message: String) : NewsScreenMode()
    data class Categories(val categories: List<NewsCategory>, val totalCategoryCount: Int) : NewsScreenMode()
    data class CategorySettings(val categories: List<NewsCategory>, val hiddenFiles: Set<String>) : NewsScreenMode()
    data class Stories(val category: NewsCategory, val clusters: List<NewsCluster>) : NewsScreenMode()
    data class Story(val category: NewsCategory, val cluster: NewsCluster) : NewsScreenMode()
}

data class NewsUiState(
    val mode: NewsScreenMode = NewsScreenMode.Loading(LOADING_CATEGORIES_MESSAGE),
    val errorModal: String? = null,
)

internal const val LOADING_CATEGORIES_MESSAGE = "loading kagi news..."
internal const val LOADING_STORIES_MESSAGE = "loading stories..."

private const val NETWORK_ERROR_MESSAGE =
    "Kagi News requires a network connection. Please insert a data sim or connect to wi-fi to load the latest stories."

class NewsViewModel(
    private val dataStore: DataStore<Preferences>,
) : LightViewModel<Unit>() {
    private val api = NewsApi()
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(NewsUiState())
    val uiState: StateFlow<NewsUiState> = _uiState.asStateFlow()

    // Snapshots of the screen we came from, so "back" is free of an extra fetch or IO read.
    private var categoriesBeforeStories: NewsScreenMode.Categories? = null
    private var storiesBeforeStory: NewsScreenMode.Stories? = null

    // The unfiltered index and the user's hide-list, kept separately from the mode so
    // toggling a category in settings doesn't require a re-fetch to recompute the list.
    private var allCategories: List<NewsCategory> = emptyList()
    private var hiddenCategoryFiles: Set<String> = emptySet()

    // True once we know whether the user (or a previous default-application) has ever
    // written a hide-list. Distinguishes "never configured" (null in DataStore) from
    // "explicitly cleared" (empty string) so the one-time all-hidden default only fires once.
    private var hasStoredHiddenPreference: Boolean = false

    private val apiExceptionHandler = CoroutineExceptionHandler { _, _ ->
        viewModelScope.launch(Dispatchers.Main) {
            showApiFailure()
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            loadCachedIndexIfAny()
            refreshCategories()
        }
    }

    private suspend fun loadCachedIndexIfAny() {
        val prefs = dataStore.data.first()
        val storedHidden = prefs[NewsPreferences.HIDDEN_CATEGORY_FILES]
        hasStoredHiddenPreference = storedHidden != null
        hiddenCategoryFiles = storedHidden
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

        val cachedIndex = prefs[NewsPreferences.INDEX_JSON] ?: return
        val categories = runCatching { json.decodeFromString<List<NewsCategory>>(cachedIndex) }.getOrNull()
        if (!categories.isNullOrEmpty()) {
            allCategories = categories
            applyDefaultHiddenCategoriesIfNeeded()
            _uiState.update { it.copy(mode = categoriesMode()) }
        }
    }

    private fun visibleCategories(): List<NewsCategory> =
        allCategories.filterNot { it.file in hiddenCategoryFiles }

    private fun categoriesMode(): NewsScreenMode.Categories =
        NewsScreenMode.Categories(visibleCategories(), allCategories.size)

    // First time ever (no stored hide-list at all): start with everything off so the
    // user opts into categories rather than having to turn off a wall of them one by one.
    private fun applyDefaultHiddenCategoriesIfNeeded() {
        if (hasStoredHiddenPreference || allCategories.isEmpty()) return
        hiddenCategoryFiles = allCategories.map { it.file }.toSet()
        hasStoredHiddenPreference = true
        persistHiddenCategories()
    }

    private fun persistHiddenCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                dataStore.edit { prefs ->
                    prefs[NewsPreferences.HIDDEN_CATEGORY_FILES] = hiddenCategoryFiles.joinToString(",")
                }
            }
        }
    }

    fun refreshCategories() {
        val hadCachedCategories = allCategories.isNotEmpty()
        if (!hadCachedCategories) {
            _uiState.update { it.copy(mode = NewsScreenMode.Loading(LOADING_CATEGORIES_MESSAGE), errorModal = null) }
        }
        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            api.fetchIndex().fold(
                onSuccess = { categories ->
                    runCatching {
                        dataStore.edit { prefs -> prefs[NewsPreferences.INDEX_JSON] = json.encodeToString(categories) }
                    }
                    allCategories = categories
                    applyDefaultHiddenCategoriesIfNeeded()
                    _uiState.update { it.copy(mode = categoriesMode(), errorModal = null) }
                },
                onFailure = { error ->
                    // Fall back to whatever categories we had (possibly none) so the user always
                    // lands on an actionable screen instead of a dead-end loading spinner.
                    showApiFailure(error, recoveryMode = categoriesMode())
                },
            )
        }
    }

    fun openCategorySettings() {
        _uiState.update {
            it.copy(mode = NewsScreenMode.CategorySettings(allCategories, hiddenCategoryFiles), errorModal = null)
        }
    }

    fun toggleCategoryVisibility(category: NewsCategory) {
        hiddenCategoryFiles = if (category.file in hiddenCategoryFiles) {
            hiddenCategoryFiles - category.file
        } else {
            hiddenCategoryFiles + category.file
        }
        hasStoredHiddenPreference = true
        _uiState.update { state ->
            val settings = state.mode as? NewsScreenMode.CategorySettings ?: return@update state
            state.copy(mode = settings.copy(hiddenFiles = hiddenCategoryFiles))
        }
        persistHiddenCategories()
    }

    fun closeCategorySettings() {
        _uiState.update { it.copy(mode = categoriesMode(), errorModal = null) }
    }

    fun openCategory(category: NewsCategory) {
        val current = _uiState.value.mode as? NewsScreenMode.Categories
        if (current != null) categoriesBeforeStories = current

        val prefsCache = _uiState.value.mode as? NewsScreenMode.Stories
        val cachedClusters = prefsCache?.takeIf { it.category.file == category.file }?.clusters

        _uiState.update {
            it.copy(
                mode = if (cachedClusters != null) {
                    NewsScreenMode.Stories(category, cachedClusters)
                } else {
                    NewsScreenMode.Loading(LOADING_STORIES_MESSAGE)
                },
                errorModal = null,
            )
        }

        viewModelScope.launch(Dispatchers.IO + apiExceptionHandler) {
            val storedClusters = cachedClusters ?: loadCachedClusters(category)
            if (storedClusters != null && cachedClusters == null) {
                _uiState.update { it.copy(mode = NewsScreenMode.Stories(category, storedClusters)) }
            }

            api.fetchCategory(category.file).fold(
                onSuccess = { clusters ->
                    runCatching {
                        dataStore.edit { prefs ->
                            prefs[NewsPreferences.LAST_CATEGORY_FILE] = category.file
                            prefs[NewsPreferences.LAST_CATEGORY_NAME] = category.name
                            prefs[NewsPreferences.LAST_CATEGORY_CLUSTERS_JSON] = json.encodeToString(clusters)
                        }
                    }
                    _uiState.update { it.copy(mode = NewsScreenMode.Stories(category, clusters), errorModal = null) }
                },
                onFailure = { error ->
                    val fallback = storedClusters ?: cachedClusters
                    val recoveryMode = if (fallback != null) {
                        null // keep showing the stale stories, just surface the error banner
                    } else {
                        categoriesBeforeStories ?: categoriesMode()
                    }
                    showApiFailure(error, recoveryMode = recoveryMode)
                },
            )
        }
    }

    private suspend fun loadCachedClusters(category: NewsCategory): List<NewsCluster>? {
        val prefs = dataStore.data.first()
        if (prefs[NewsPreferences.LAST_CATEGORY_FILE] != category.file) return null
        val clustersJson = prefs[NewsPreferences.LAST_CATEGORY_CLUSTERS_JSON] ?: return null
        // Defensive: a cache written before the top-N limit existed could hold more.
        return runCatching { json.decodeFromString<List<NewsCluster>>(clustersJson) }
            .getOrNull()
            ?.take(TOP_STORIES_PER_CATEGORY)
    }

    fun refreshCurrentCategory() {
        val category = (_uiState.value.mode as? NewsScreenMode.Stories)?.category ?: return
        openCategory(category)
    }

    fun openStory(cluster: NewsCluster) {
        val stories = _uiState.value.mode as? NewsScreenMode.Stories ?: return
        storiesBeforeStory = stories
        _uiState.update { it.copy(mode = NewsScreenMode.Story(stories.category, cluster), errorModal = null) }
    }

    fun closeStory() {
        val stories = storiesBeforeStory ?: return
        _uiState.update { it.copy(mode = stories, errorModal = null) }
    }

    fun closeCategory() {
        val categories = categoriesBeforeStories ?: categoriesMode()
        _uiState.update { it.copy(mode = categories, errorModal = null) }
    }

    private fun showApiFailure(error: Throwable? = null, recoveryMode: NewsScreenMode? = null) {
        _uiState.update {
            it.copy(
                mode = recoveryMode ?: it.mode,
                errorModal = NETWORK_ERROR_MESSAGE,
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorModal = null) }
    }

    override fun onBackPressed(): Boolean {
        return when (_uiState.value.mode) {
            is NewsScreenMode.Story -> {
                closeStory()
                true
            }
            is NewsScreenMode.Stories -> {
                closeCategory()
                true
            }
            is NewsScreenMode.CategorySettings -> {
                closeCategorySettings()
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
