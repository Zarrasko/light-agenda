package com.thelightphone.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

private data class GuideStep(val title: String, val body: String)

private val GUIDE_STEPS = listOf(
    GuideStep(
        title = "1. Create a free account",
        body = "On a computer or your phone's browser, go to intervals.icu and sign up. " +
            "No credit card, no subscription.",
    ),
    GuideStep(
        title = "2. Connect your watch",
        body = "In intervals.icu Settings, connect Garmin, Coros, Wahoo, Polar, or similar. " +
            "Activities and wellness data sync automatically after that.",
    ),
    GuideStep(
        title = "3. Generate an API key",
        body = "In Settings > Developer Settings, generate a key. That's the whole auth " +
            "setup - no login flow to do here.",
    ),
    GuideStep(
        title = "4. Bring it back here",
        body = "Type the key into the API Key field, or scan a QR code made from it - see " +
            "this tool's README for a script that generates one offline.",
    ),
)

// Static, read-only walkthrough for getting an intervals.icu API key - the account/watch/key
// steps all happen off-device, so this is just something to read before switching away.
class PulseGuideScreen(
    sealedActivity: SealedLightActivity,
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
                    center = LightTopBarCenter.Text("Getting an API Key"),
                    modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    GUIDE_STEPS.forEach { step ->
                        Column(modifier = Modifier.padding(bottom = 1.5f.gridUnitsAsDp())) {
                            LightText(
                                text = step.title,
                                variant = LightTextVariant.Heading,
                                modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                            )
                            LightText(
                                text = step.body,
                                variant = LightTextVariant.Copy,
                                lighten = true,
                            )
                        }
                    }
                }
            }
        }
    }
}
