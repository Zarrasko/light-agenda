package com.thelightphone.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.launch

private data class FieldToggle(
    val label: String,
    val key: Preferences.Key<Boolean>,
)

private val FIELD_TOGGLES = listOf(
    FieldToggle("Resting Heart Rate", PulsePreferences.SHOW_RESTING_HR),
    FieldToggle("Sleep", PulsePreferences.SHOW_SLEEP),
    FieldToggle("HRV", PulsePreferences.SHOW_HRV),
    FieldToggle("Steps", PulsePreferences.SHOW_STEPS),
    FieldToggle("Fitness Trend", PulsePreferences.SHOW_FITNESS_TREND),
    FieldToggle("Gear", PulsePreferences.SHOW_GEAR),
)

// Self-sufficient like PulseGearScreen - reads/writes DataStore directly rather than routing
// through PulseViewModel, since it's reached by navigateTo, not a mode of the main screen.
class PulseDataFieldsScreen(
    sealedActivity: SealedLightActivity,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val scope = rememberCoroutineScope()
        val dataStore = lightContext.dataStore
        val prefs by dataStore.data.collectAsState(initial = null)

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                        contentDescription = "Back",
                    ),
                    center = LightTopBarCenter.Text("Data Fields"),
                    modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 1f.gridUnitsAsDp()),
                ) {
                    FIELD_TOGGLES.forEach { field ->
                        val isOn = prefs?.get(field.key) ?: true
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .lightClickable(onClick = {
                                    scope.launch { dataStore.edit { it[field.key] = !isOn } }
                                })
                                .padding(vertical = 0.75f.gridUnitsAsDp()),
                        ) {
                            LightIcon(
                                icon = if (isOn) LightIcons.TOGGLE_STATE_ON else LightIcons.TOGGLE_STATE_OFF,
                                contentDescription = if (isOn) "Shown" else "Hidden",
                                modifier = Modifier.padding(end = 0.75f.gridUnitsAsDp()),
                            )
                            LightText(
                                text = field.label,
                                variant = LightTextVariant.Copy,
                                lighten = !isOn,
                            )
                        }
                    }
                }
            }
        }
    }
}
