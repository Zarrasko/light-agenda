package com.thelightphone.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.LightOverlay
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberNotificationPermissionRequester
import com.thelightphone.sdk.rememberOverlayPermissionRequester
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
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

// Self-sufficient - reads/writes DataStore directly rather than routing through
// AgendaViewModel, since it's reached by navigateTo, not a mode of the main screen.
class ManageCalendarsScreen(
    sealedActivity: SealedLightActivity,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val scope = rememberCoroutineScope()
        val repository = remember { AgendaSourcesRepository(lightContext.dataStore) }
        var sources by remember { mutableStateOf<List<CalendarSource>>(emptyList()) }
        var reloadKey by remember { mutableStateOf(0) }
        // Freshly computed each time this screen is opened (a new instance, via navigateTo) -
        // unlike AgendaScreen's one-shot banner, this is the permanent way back in if a first
        // prompt was denied, or if the permission was granted later from Android's own Settings.
        var remindersGranted by remember { mutableStateOf(LightOverlay.canShow(lightContext)) }
        val requestOverlayPermission = rememberOverlayPermissionRequester { granted ->
            remindersGranted = granted
        }
        // See AgendaScreen: requested alongside the overlay, unconditionally, as an independent
        // second path to the same alert for launchers that bury the overlay window.
        val requestNotificationPermission = rememberNotificationPermissionRequester()

        LaunchedEffect(reloadKey) { sources = repository.load() }

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        // Always deliver a result (not a bare goBack()) so AgendaScreen's
                        // navigateTo callback fires and refreshes - goBack(result) is what
                        // populates screen.result, which BackStackEntry.deliverResult()
                        // requires to be non-null before it'll invoke the callback at all.
                        onClick = { goBack(Unit) },
                        contentDescription = "Back",
                    ),
                    center = LightTopBarCenter.Text("Calendars"),
                    modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                )

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 1f.gridUnitsAsDp()),
                ) {
                    Column(modifier = Modifier.padding(bottom = 1.5f.gridUnitsAsDp())) {
                        LightText(
                            text = "REMINDERS",
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            modifier = Modifier.padding(bottom = 0.15f.gridUnitsAsDp()),
                        )
                        LightText(
                            text = if (remindersGranted) {
                                "On - a heads-up box 15 minutes before an event"
                            } else {
                                "Off - tap to enable →"
                            },
                            variant = LightTextVariant.Heading,
                            modifier = if (remindersGranted) {
                                Modifier
                            } else {
                                Modifier.lightClickable(onClick = {
                                    requestOverlayPermission()
                                    requestNotificationPermission()
                                })
                            },
                        )
                        if (!remindersGranted) {
                            LightText(
                                text = "Opens Settings > Display over other apps - flip it on, then back out.",
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                modifier = Modifier.padding(top = 0.15f.gridUnitsAsDp()),
                            )
                        }
                    }

                    if (sources.isEmpty()) {
                        LightText(
                            text = "No calendars yet.",
                            variant = LightTextVariant.Copy,
                            lighten = true,
                            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                        )
                    } else {
                        sources.forEach { source ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 1f.gridUnitsAsDp()),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    LightText(
                                        text = source.label,
                                        variant = LightTextVariant.Copy,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    LightText(
                                        text = source.icsUrl,
                                        variant = LightTextVariant.Detail,
                                        lighten = true,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                LightIcon(
                                    icon = LightIcons.TRASH,
                                    contentDescription = "Remove ${source.label}",
                                    modifier = Modifier
                                        .padding(start = 0.75f.gridUnitsAsDp())
                                        .lightClickable(onClick = {
                                            scope.launch {
                                                repository.remove(source.id)
                                                reloadKey++
                                            }
                                        }),
                                )
                            }
                        }
                    }
                }

                LightBottomBar(
                    items = listOf(
                        LightBarButton.LightIcon(
                            icon = LightIcons.PENCIL,
                            onClick = {
                                navigateTo(screenFactory = { AddCalendarScreen(it) }) { reloadKey++ }
                            },
                            contentDescription = "Add a Calendar",
                        ),
                    ),
                )
            }
        }
    }
}
