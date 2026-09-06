package com.thelightphone.agenda

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.datastore.preferences.core.edit
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightWork
import com.thelightphone.sdk.LightOverlay
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberOverlayPermissionRequester
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

@InitialScreen
class AgendaScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, AgendaViewModel>(sealedActivity) {

    override val viewModelClass: Class<AgendaViewModel>
        get() = AgendaViewModel::class.java

    override fun createViewModel(): AgendaViewModel = AgendaViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()
        val scope = rememberCoroutineScope()

        var remindersGranted by remember { mutableStateOf(LightOverlay.canShow(lightContext)) }
        var hasPromptedReminders by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            hasPromptedReminders =
                lightContext.dataStore.data.first()[AgendaPreferences.HAS_PROMPTED_REMINDERS] ?: false
        }
        val requestOverlayPermission = rememberOverlayPermissionRequester { granted ->
            remindersGranted = granted
        }

        // Reminders only run once permission is granted - re-checking (and re-enqueuing, which
        // is idempotent) every time this screen is shown with calendars configured means a
        // permission granted later via Android's own Settings still gets picked up.
        LaunchedEffect(state.mode, remindersGranted) {
            if (state.mode is AgendaScreenMode.Agenda && remindersGranted) {
                LightWork.enqueuePeriodic(lightContext, "agenda-reminder-check", 15.minutes)
            }
        }

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                when (val mode = state.mode) {
                    is AgendaScreenMode.Loading -> LoadingContent(message = mode.message)

                    is AgendaScreenMode.Empty -> EmptyContent(
                        onAddCalendar = {
                            navigateTo(screenFactory = { AddCalendarScreen(it) }) { viewModel.refresh() }
                        },
                    )

                    is AgendaScreenMode.Agenda -> AgendaContent(
                        mode = mode,
                        showReminderBanner = !remindersGranted && !hasPromptedReminders,
                        onEnableReminders = {
                            hasPromptedReminders = true
                            scope.launch {
                                lightContext.dataStore.edit { it[AgendaPreferences.HAS_PROMPTED_REMINDERS] = true }
                            }
                            requestOverlayPermission()
                        },
                        onRefresh = viewModel::refresh,
                        onManageCalendars = {
                            navigateTo(screenFactory = { ManageCalendarsScreen(it) }) { viewModel.refresh() }
                        },
                        onSelectEvent = { event ->
                            navigateTo(screenFactory = { EventDetailScreen(it, event) })
                        },
                    )
                }

                state.errorModal?.let { message ->
                    LightFullscreenModal(message = message, onClose = viewModel::dismissError)
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(message: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            center = LightTopBarCenter.Text("Agenda"),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            LightText(
                text = message,
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
            )
        }
    }
}

@Composable
private fun EmptyContent(onAddCalendar: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            center = LightTopBarCenter.Text("Agenda"),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )
        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 1f.gridUnitsAsDp()),
        ) {
            LightText(
                text = "No calendars yet. Add a read-only ICS feed URL from Outlook, Proton, " +
                    "Google, or iCloud to see your agenda here.",
                variant = LightTextVariant.Detail,
                lighten = true,
            )
        }
        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(
                    icon = LightIcons.PENCIL,
                    onClick = onAddCalendar,
                    contentDescription = "Add a Calendar",
                ),
            ),
        )
    }
}

@Composable
private fun AgendaContent(
    mode: AgendaScreenMode.Agenda,
    showReminderBanner: Boolean,
    onEnableReminders: () -> Unit,
    onRefresh: () -> Unit,
    onManageCalendars: () -> Unit,
    onSelectEvent: (AgendaEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            center = LightTopBarCenter.Text("Agenda"),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 1f.gridUnitsAsDp()),
        ) {
            if (showReminderBanner) {
                LightText(
                    text = "Enable reminders for upcoming events →",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier
                        .lightClickable(onClick = onEnableReminders)
                        .padding(bottom = 1.5f.gridUnitsAsDp()),
                )
            }

            if (mode.dayGroups.isEmpty()) {
                LightText(
                    text = "Nothing on your calendars in the next two weeks.",
                    variant = LightTextVariant.Copy,
                    lighten = true,
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )
            } else {
                mode.dayGroups.forEach { group ->
                    DayGroupSection(
                        group,
                        onSelectEvent = onSelectEvent,
                        modifier = Modifier.padding(bottom = 1.5f.gridUnitsAsDp()),
                    )
                }
            }

            if (mode.hadSourceErrors) {
                LightText(
                    text = "Couldn't reach one or more calendars - showing what did load.",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )
            }

            mode.lastSyncedLabel?.let { syncedAt ->
                LightText(
                    text = "Last synced $syncedAt",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )
            }
        }

        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(
                    icon = LightIcons.REFRESH,
                    onClick = onRefresh,
                    contentDescription = "Refresh",
                ),
                LightBarButton.LightIcon(
                    icon = LightIcons.SETTINGS,
                    onClick = onManageCalendars,
                    contentDescription = "Manage Calendars",
                ),
            ),
        )
    }
}

@Composable
private fun DayGroupSection(
    group: DayGroup,
    onSelectEvent: (AgendaEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LightText(
            text = group.label.uppercase(),
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )
        group.events.forEach { event ->
            EventRow(
                event,
                modifier = Modifier
                    .lightClickable(onClick = { onSelectEvent(event) })
                    .padding(bottom = 0.75f.gridUnitsAsDp()),
            )
        }
    }
}

@Composable
private fun EventRow(event: AgendaEvent, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        LightText(
            text = "${event.title} →",
            variant = LightTextVariant.Copy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 0.1f.gridUnitsAsDp()),
        )
        val detail = listOfNotNull(event.timeRangeLabel(), event.calendarLabel, event.location)
            .joinToString(" · ")
        LightText(
            text = detail,
            variant = LightTextVariant.Detail,
            lighten = true,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
