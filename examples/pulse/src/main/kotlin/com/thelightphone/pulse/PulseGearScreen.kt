package com.thelightphone.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightFullscreenModal
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

private const val NETWORK_ERROR_MESSAGE = "Couldn't load gear. Check your connection and try again."

class PulseGearScreen(
    sealedActivity: SealedLightActivity,
) : SimpleLightScreen<Unit>(sealedActivity) {

    private val api = PulseApi()
    private val credentialsRepository = PulseCredentialsRepository(lightContext.dataStore)

    override fun onScreenDestroy() {
        super.onScreenDestroy()
        api.close()
    }

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()

        var gear by remember { mutableStateOf<List<Gear>?>(null) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            val apiKey = credentialsRepository.load() ?: return@LaunchedEffect
            api.fetchGear(apiKey).fold(
                onSuccess = { gear = it },
                onFailure = { errorMessage = NETWORK_ERROR_MESSAGE },
            )
        }

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LightTopBar(
                        leftButton = LightBarButton.LightIcon(
                            icon = LightIcons.BACK,
                            onClick = { goBack() },
                            contentDescription = "Back",
                        ),
                        center = LightTopBarCenter.Text("Gear"),
                        modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                    )

                    val current = gear
                    when {
                        current == null -> Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LightText(text = "loading...", variant = LightTextVariant.Copy)
                        }

                        current.isEmpty() -> Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LightText(
                                text = "No gear logged on intervals.icu yet.",
                                variant = LightTextVariant.Copy,
                                align = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
                            )
                        }

                        else -> LightScrollView(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(start = 1f.gridUnitsAsDp()),
                        ) {
                            current.forEach { item ->
                                Column(modifier = Modifier.padding(bottom = 1.25f.gridUnitsAsDp())) {
                                    LightText(
                                        text = item.name,
                                        variant = LightTextVariant.Copy,
                                        lighten = item.isRetired,
                                    )
                                    val detail = buildString {
                                        append(item.type)
                                        append(" · ")
                                        append(item.distance.formatMiles())
                                        if (item.isRetired) append(" · retired")
                                    }
                                    LightText(
                                        text = detail,
                                        variant = LightTextVariant.Detail,
                                        lighten = true,
                                    )
                                }
                            }
                        }
                    }
                }

                errorMessage?.let { message ->
                    LightFullscreenModal(
                        message = message,
                        onClose = { errorMessage = null },
                    )
                }
            }
        }
    }
}
