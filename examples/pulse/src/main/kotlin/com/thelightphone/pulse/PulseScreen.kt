package com.thelightphone.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

@InitialScreen
class PulseScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, PulseViewModel>(sealedActivity) {

    override val viewModelClass: Class<PulseViewModel>
        get() = PulseViewModel::class.java

    override fun createViewModel(): PulseViewModel = PulseViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()
        val textFieldState = rememberTextFieldState("")
        val keyboardOptionsFlow = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                when (val mode = state.mode) {
                    is PulseScreenMode.Loading -> LoadingContent(message = mode.message)

                    is PulseScreenMode.Setup -> {
                        if (mode.editingKey) {
                            LaunchedEffect(mode.editSession) {
                                textFieldState.setTextAndPlaceCursorAtEnd(mode.apiKey)
                            }
                            LightTextInputEditor(
                                title = "API Key",
                                editorKey = mode.editSession,
                                keyboardOptionsFlow = keyboardOptionsFlow,
                                state = textFieldState,
                                singleLine = true,
                                onSubmit = { viewModel.submitKey(it.toString()) },
                                onBack = viewModel::cancelEditor,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            SetupContent(
                                mode = mode,
                                onEditKey = viewModel::openEditor,
                                onScanQr = {
                                    navigateTo(screenFactory = { PulseQrScannerScreen(it) }) { key ->
                                        viewModel.onQrScanned(key)
                                    }
                                },
                                onConnect = viewModel::connect,
                                onPreviewSample = viewModel::previewSampleData,
                                onOpenGuide = {
                                    navigateTo(screenFactory = { PulseGuideScreen(it) })
                                },
                            )
                        }
                    }

                    is PulseScreenMode.Activities -> ActivitiesContent(
                        mode = mode,
                        fieldPreferences = state.fieldPreferences,
                        onRefresh = viewModel::refreshActivities,
                        onOpenSettings = viewModel::openSettings,
                        onSelectActivity = { activity ->
                            navigateTo(screenFactory = { PulseActivityDetailScreen(it, activity) })
                        },
                        onShowFitnessInfo = viewModel::showFitnessInfo,
                        onOpenGear = {
                            navigateTo(screenFactory = { PulseGearScreen(it) })
                        },
                        onOpenTodayDetail = { wellness ->
                            navigateTo(screenFactory = { PulseTodayDetailScreen(it, wellness) })
                        },
                        onScheduleWorkout = {
                            navigateTo(screenFactory = { PulseScheduleWorkoutScreen(it) })
                        },
                    )

                    is PulseScreenMode.Settings -> SettingsContent(
                        mode = mode,
                        onBack = viewModel::refreshActivities,
                        onOpenDataFields = {
                            navigateTo(screenFactory = { PulseDataFieldsScreen(it) })
                        },
                        onSignOut = viewModel::disconnect,
                    )
                }

                state.errorModal?.let { message ->
                    LightFullscreenModal(
                        message = message,
                        onClose = viewModel::dismissError,
                    )
                }

                state.infoModal?.let { message ->
                    LightFullscreenModal(
                        message = message,
                        onClose = viewModel::dismissInfo,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(message: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            center = LightTopBarCenter.Text("Pulse"),
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
private fun SetupContent(
    mode: PulseScreenMode.Setup,
    onEditKey: () -> Unit,
    onScanQr: () -> Unit,
    onConnect: () -> Unit,
    onPreviewSample: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            center = LightTopBarCenter.Text("Connect Pulse"),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 1f.gridUnitsAsDp()),
        ) {
            LightText(
                text = "Paste your intervals.icu API key, or scan a QR code generated from " +
                    "it. Free account, no subscription needed. See this tool's README, or tap " +
                    "the list icon below for on-device instructions, for how to get one.",
                variant = LightTextVariant.Detail,
                lighten = true,
                modifier = Modifier.padding(bottom = 1.5f.gridUnitsAsDp()),
            )

            LightTextField(
                label = "API Key",
                value = mode.apiKey.maskedIfLong(),
                placeholder = "paste your API key",
                onClick = onEditKey,
                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
            )

            if (mode.isConnecting) {
                LightText(
                    text = "Connecting...",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                )
            } else {
                LightText(
                    text = "Don't have a key yet? Preview with sample activities →",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.lightClickable(onClick = onPreviewSample),
                )
            }
        }

        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(
                    icon = LightIcons.LIST,
                    onClick = onOpenGuide,
                    contentDescription = "How to get an API key",
                ),
                LightBarButton.Text(text = "CONNECT", onClick = onConnect),
                LightBarButton.LightIcon(
                    icon = LightIcons.CAMERA,
                    onClick = onScanQr,
                    contentDescription = "Scan QR code",
                ),
            ),
        )
    }
}

// API keys are ~25 characters - not worth hiding entirely on a device only the account owner
// uses, but a long unbroken string is just noise on a first read of the screen.
private fun String.maskedIfLong(): String =
    if (length > 12) "${take(4)}…${takeLast(4)}" else this

@Composable
private fun SettingsContent(
    mode: PulseScreenMode.Settings,
    onBack: () -> Unit,
    onOpenDataFields: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(
                icon = LightIcons.BACK,
                onClick = onBack,
                contentDescription = "Back",
            ),
            center = LightTopBarCenter.Text("Settings"),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 1f.gridUnitsAsDp()),
        ) {
            // Answers "am I still connected?" up front - the whole reason this is a separate
            // screen from Setup rather than a reused one.
            val statusText = mode.lastSyncedLabel?.let { "Connected · last synced $it" } ?: "Connected"
            LightText(
                text = statusText,
                variant = LightTextVariant.Copy,
                lighten = true,
                modifier = Modifier.padding(bottom = 1.5f.gridUnitsAsDp()),
            )

            Column(modifier = Modifier.padding(bottom = 1.25f.gridUnitsAsDp())) {
                LightText(
                    text = "API KEY",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(bottom = 0.15f.gridUnitsAsDp()),
                )
                LightText(text = mode.apiKey.maskedIfLong(), variant = LightTextVariant.Heading)
            }

            LightText(
                text = "Customize Data Fields →",
                variant = LightTextVariant.Heading,
                modifier = Modifier
                    .lightClickable(onClick = onOpenDataFields)
                    .padding(bottom = 1.25f.gridUnitsAsDp()),
            )
        }

        LightBottomBar(
            items = listOf(LightBarButton.Text(text = "SIGN OUT", onClick = onSignOut)),
        )
    }
}

@Composable
private fun ActivitiesContent(
    mode: PulseScreenMode.Activities,
    fieldPreferences: DataFieldPreferences,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectActivity: (SummaryActivity) -> Unit,
    onShowFitnessInfo: () -> Unit,
    onOpenGear: () -> Unit,
    onOpenTodayDetail: (Wellness) -> Unit,
    onScheduleWorkout: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            center = LightTopBarCenter.Text("Pulse"),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 1f.gridUnitsAsDp()),
        ) {
            mode.wellness?.let { wellness ->
                val parts = wellness.summaryParts(
                    showRestingHR = fieldPreferences.showRestingHR,
                    showSleep = fieldPreferences.showSleep,
                    showHRV = fieldPreferences.showHRV,
                    showSteps = fieldPreferences.showSteps,
                )
                if (parts.isNotEmpty()) {
                    TodayWellnessRow(
                        parts,
                        modifier = Modifier
                            .lightClickable(onClick = { onOpenTodayDetail(wellness) })
                            .padding(bottom = 1.5f.gridUnitsAsDp()),
                    )
                }
                if (fieldPreferences.showFitnessTrend) {
                    wellness.fitnessTrend()?.let { trend ->
                        FitnessTrendRow(
                            trend = trend,
                            onClick = onShowFitnessInfo,
                            modifier = Modifier.padding(bottom = 1.5f.gridUnitsAsDp()),
                        )
                    }
                }
            }

            WeekSummaryRow(mode.weekSummary, modifier = Modifier.padding(bottom = 1.5f.gridUnitsAsDp()))

            if (mode.activities.isEmpty()) {
                LightText(
                    text = "No recent activities.",
                    variant = LightTextVariant.Copy,
                    lighten = true,
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )
            } else {
                mode.activities.forEach { activity ->
                    ActivityRow(
                        activity,
                        modifier = Modifier
                            .lightClickable(onClick = { onSelectActivity(activity) })
                            .padding(bottom = 1.25f.gridUnitsAsDp()),
                    )
                }
            }

            if (fieldPreferences.showGear) {
                mode.gearSummary?.let { gearSummary ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable(onClick = onOpenGear)
                            .padding(bottom = 1f.gridUnitsAsDp()),
                    ) {
                        LightText(
                            text = "GEAR",
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            modifier = Modifier.padding(bottom = 0.15f.gridUnitsAsDp()),
                        )
                        LightText(text = "$gearSummary →", variant = LightTextVariant.Copy)
                    }
                }
            }

            if (mode.isSample) {
                LightText(
                    text = "Sample data - connect intervals.icu to see your real activities.",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )
            } else {
                mode.lastSyncedLabel?.let { syncedAt ->
                    LightText(
                        text = "Last synced $syncedAt",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                    )
                }
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
                    icon = LightIcons.PENCIL,
                    onClick = onScheduleWorkout,
                    contentDescription = "Schedule a Workout",
                ),
                LightBarButton.LightIcon(
                    icon = LightIcons.SETTINGS,
                    onClick = onOpenSettings,
                    contentDescription = "Settings",
                ),
            ),
        )
    }
}

@Composable
private fun FitnessTrendRow(trend: FitnessTrend, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().lightClickable(onClick = onClick)) {
        LightText(
            text = "FITNESS TREND",
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )
        LightText(text = "${trend.label} (tap for details)", variant = LightTextVariant.Heading)
    }
}

@Composable
private fun TodayWellnessRow(parts: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        LightText(
            text = "TODAY",
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )
        LightText(text = "${parts.joinToString(" · ")} →", variant = LightTextVariant.Heading)
    }
}

@Composable
private fun WeekSummaryRow(summary: WeekSummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        LightText(
            text = "THIS WEEK",
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )
        val summaryText = if (summary.activityCount == 0) {
            "No activities yet"
        } else {
            "${summary.activityCount} activities · " +
                "${summary.distanceMeters.formatMiles()} · " +
                summary.movingTimeSeconds.formatDuration()
        }
        LightText(text = summaryText, variant = LightTextVariant.Heading)
    }
}

@Composable
private fun ActivityRow(activity: SummaryActivity, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        LightText(
            text = "${activity.name} →",
            variant = LightTextVariant.Copy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 0.15f.gridUnitsAsDp()),
        )
        val detail = "${activity.startDateLocal.formatActivityDate()} · ${activity.type} · " +
            "${activity.distance.formatMiles()} · ${activity.movingTimeSeconds.formatDuration()}"
        LightText(
            text = detail,
            variant = LightTextVariant.Detail,
            lighten = true,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
