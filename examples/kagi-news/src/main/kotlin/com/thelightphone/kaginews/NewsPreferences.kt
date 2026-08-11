package com.thelightphone.kaginews

import androidx.datastore.preferences.core.stringPreferencesKey

internal object NewsPreferences {
    val INDEX_JSON = stringPreferencesKey("index_json")
    val LAST_CATEGORY_FILE = stringPreferencesKey("last_category_file")
    val LAST_CATEGORY_NAME = stringPreferencesKey("last_category_name")
    val LAST_CATEGORY_CLUSTERS_JSON = stringPreferencesKey("last_category_clusters_json")
    val HIDDEN_CATEGORY_FILES = stringPreferencesKey("hidden_category_files")
}
