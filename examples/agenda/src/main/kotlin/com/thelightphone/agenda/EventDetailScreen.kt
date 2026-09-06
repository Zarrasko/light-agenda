package com.thelightphone.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.LightLinks
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
import com.thelightphone.sdk.ui.lightClickable

// The event is already fully loaded from the agenda fetch - no network call needed here, just
// a fuller view of data already in hand (notably DESCRIPTION, which the list row has no room
// for).
class EventDetailScreen(
    sealedActivity: SealedLightActivity,
    private val event: AgendaEvent,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        var linkError by remember { mutableStateOf<String?>(null) }

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
                        center = LightTopBarCenter.Text(event.calendarLabel),
                        modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                    )

                    LightScrollView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 1f.gridUnitsAsDp()),
                    ) {
                        LightText(
                            text = event.title,
                            variant = LightTextVariant.Heading,
                            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                        )
                        LightText(
                            text = "${event.dayLabel()} · ${event.timeRangeLabel()}",
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            modifier = Modifier.padding(bottom = 1.5f.gridUnitsAsDp()),
                        )

                        event.location?.let { location ->
                            Column(modifier = Modifier.padding(bottom = 1.25f.gridUnitsAsDp())) {
                                LightText(
                                    text = "LOCATION",
                                    variant = LightTextVariant.Detail,
                                    lighten = true,
                                    modifier = Modifier.padding(bottom = 0.15f.gridUnitsAsDp()),
                                )
                                LightText(text = location, variant = LightTextVariant.Copy)
                            }
                        }

                        if (event.description != null) {
                            Column(modifier = Modifier.padding(bottom = 1.25f.gridUnitsAsDp())) {
                                LightText(
                                    text = "NOTES",
                                    variant = LightTextVariant.Detail,
                                    lighten = true,
                                    modifier = Modifier.padding(bottom = 0.15f.gridUnitsAsDp()),
                                )
                                LightText(text = event.description, variant = LightTextVariant.Copy)
                            }
                        } else {
                            LightText(
                                text = "No notes on this event.",
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                            )
                        }

                        val links = event.links()
                        if (links.isNotEmpty()) {
                            Column {
                                LightText(
                                    text = "LINKS",
                                    variant = LightTextVariant.Detail,
                                    lighten = true,
                                    modifier = Modifier.padding(bottom = 0.15f.gridUnitsAsDp()),
                                )
                                links.forEach { url ->
                                    LightText(
                                        text = url,
                                        variant = LightTextVariant.Copy,
                                        underline = true,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .lightClickable(onClick = {
                                                if (!LightLinks.open(lightContext, url)) {
                                                    linkError = "Nothing on this device can open that link."
                                                }
                                            })
                                            .padding(bottom = 0.5f.gridUnitsAsDp()),
                                    )
                                }
                            }
                        }
                    }
                }

                linkError?.let { message ->
                    LightFullscreenModal(message = message, onClose = { linkError = null })
                }
            }
        }
    }
}
