package com.thelightphone.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
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

private data class Stat(val label: String, val value: String)

// The activity is already fully loaded from the list fetch - no network call needed here,
// just a different view of data already in hand.
private fun SummaryActivity.stats(): List<Stat> = listOfNotNull(
    Stat("Distance", distance.formatMiles()),
    Stat("Moving Time", movingTimeSeconds.formatDuration()),
    paceOrSpeed()?.let { Stat(it.label, it.value) },
    if (elevationGainMeters > 0) Stat("Elevation Gain", elevationGainMeters.formatFeet()) else null,
    if (hasHeartrate && averageHeartrate != null) Stat("Avg Heart Rate", "$averageHeartrate bpm") else null,
    if (hasHeartrate && maxHeartrate != null) Stat("Max Heart Rate", "$maxHeartrate bpm") else null,
    averageCadence?.takeIf { it > 0 }?.let { Stat("Avg Cadence", "${it.toInt()} rpm") },
    calories?.takeIf { it > 0 }?.let { Stat("Calories", "$it") },
)

class PulseActivityDetailScreen(
    sealedActivity: SealedLightActivity,
    private val activity: SummaryActivity,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()

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
                    center = LightTopBarCenter.Text(activity.type),
                    modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    LightText(
                        text = activity.name,
                        variant = LightTextVariant.Heading,
                        modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                    )
                    LightText(
                        text = activity.startDateLocal.formatActivityDateTime(),
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(bottom = 1.5f.gridUnitsAsDp()),
                    )

                    activity.stats().forEach { stat ->
                        Column(modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp())) {
                            LightText(
                                text = stat.label.uppercase(),
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                modifier = Modifier.padding(bottom = 0.15f.gridUnitsAsDp()),
                            )
                            LightText(text = stat.value, variant = LightTextVariant.Heading)
                        }
                    }
                }
            }
        }
    }
}
