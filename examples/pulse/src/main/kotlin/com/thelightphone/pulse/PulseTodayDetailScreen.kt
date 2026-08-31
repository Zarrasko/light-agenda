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
import kotlin.math.roundToInt

private data class TodayStat(val label: String, val value: String)

private fun Wellness.sleepStats(): List<TodayStat> = listOfNotNull(
    sleepScore?.let { TodayStat("Score", it.roundToInt().toString()) },
    sleepSecs?.let { TodayStat("Duration", it.formatSleepDuration()) },
    sleepQuality?.let { TodayStat("Quality", it.toString()) },
    avgSleepingHR?.let { TodayStat("Avg sleeping HR", "${it.roundToInt()} bpm") },
)

private fun Wellness.heartStats(): List<TodayStat> = listOfNotNull(
    restingHR?.let { TodayStat("Resting", "$it bpm") },
    hrv?.let { TodayStat("HRV", "${it.roundToInt()} ms") },
)

private fun Wellness.bodyStats(): List<TodayStat> = listOfNotNull(
    spO2?.let { TodayStat("SpO2", "${it.roundToInt()}%") },
    stress?.let { TodayStat("Stress", it.toString()) },
)

// Takes the already-loaded Wellness object rather than fetching its own - same pattern as
// PulseActivityDetailScreen, since the home screen already has this data in hand.
class PulseTodayDetailScreen(
    sealedActivity: SealedLightActivity,
    private val wellness: Wellness,
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
                    center = LightTopBarCenter.Text("Today"),
                    modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    wellness.readiness?.let { readiness ->
                        Column(modifier = Modifier.padding(bottom = 2f.gridUnitsAsDp())) {
                            LightText(text = readiness.roundToInt().toString(), variant = LightTextVariant.Heading)
                            LightText(
                                text = "READINESS",
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp()),
                            )
                        }
                    }

                    StatSection("SLEEP", wellness.sleepStats())
                    StatSection("HEART", wellness.heartStats())
                    StatSection("BODY", wellness.bodyStats())
                }
            }
        }
    }
}

@Composable
private fun StatSection(title: String, stats: List<TodayStat>) {
    if (stats.isEmpty()) return

    Column(modifier = Modifier.padding(bottom = 1.5f.gridUnitsAsDp())) {
        LightText(
            text = title,
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )
        stats.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 1f.gridUnitsAsDp())) {
                pair.forEach { stat ->
                    Column(modifier = Modifier.weight(1f)) {
                        LightText(text = stat.value, variant = LightTextVariant.Heading)
                        LightText(
                            text = stat.label,
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            modifier = Modifier.padding(top = 0.15f.gridUnitsAsDp()),
                        )
                    }
                }
            }
        }
    }
}
