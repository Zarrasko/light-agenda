package com.thelightphone.agenda

import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
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
import kotlinx.coroutines.launch

private enum class EditingField { NONE, LABEL, URL }

class AddCalendarScreen(
    sealedActivity: SealedLightActivity,
) : SimpleLightScreen<Unit>(sealedActivity) {

    // Hoisted onto the screen instance rather than `remember`-ed inside Content(): this SDK's
    // navigation host composes exactly one screen's Content() at a time, so navigating to
    // AgendaQrScannerScreen fully tears this composable down. A `remember { mutableStateOf(...) }`
    // local would be recreated blank when Content() re-enters composition on the way back,
    // orphaning whatever the QR scanner's result callback wrote into the old instance. These
    // fields live on the screen object itself, which the back stack keeps alive across that
    // round trip, so the scanned URL (or a name typed before scanning) survives it.
    private var label by mutableStateOf("")
    private var icsUrl by mutableStateOf("")
    private var errorMessage by mutableStateOf<String?>(null)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val scope = rememberCoroutineScope()
        val repository = remember { AgendaSourcesRepository(lightContext.dataStore) }
        val keyboardOptionsFlow = rememberKeyboardOptions()

        var editingField by remember { mutableStateOf(EditingField.NONE) }
        val textFieldState = rememberTextFieldState("")

        LightTheme(colors = themeColors) {
            when (editingField) {
                EditingField.LABEL -> {
                    LaunchedEffect(Unit) { textFieldState.setTextAndPlaceCursorAtEnd(label) }
                    LightTextInputEditor(
                        title = "Calendar Name",
                        keyboardOptionsFlow = keyboardOptionsFlow,
                        state = textFieldState,
                        singleLine = true,
                        onSubmit = {
                            label = it.toString()
                            editingField = EditingField.NONE
                        },
                        onBack = { editingField = EditingField.NONE },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                EditingField.URL -> {
                    LaunchedEffect(Unit) { textFieldState.setTextAndPlaceCursorAtEnd(icsUrl) }
                    LightTextInputEditor(
                        title = "ICS URL",
                        keyboardOptionsFlow = keyboardOptionsFlow,
                        state = textFieldState,
                        singleLine = true,
                        onSubmit = {
                            icsUrl = it.toString()
                            editingField = EditingField.NONE
                        },
                        onBack = { editingField = EditingField.NONE },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                EditingField.NONE -> {
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
                            center = LightTopBarCenter.Text("Add Calendar"),
                            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
                        )

                        LightScrollView(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 1f.gridUnitsAsDp()),
                        ) {
                            LightText(
                                text = "Paste a read-only ICS feed URL - from Outlook's \"publish a " +
                                    "calendar\" link, a Proton Calendar share link, or Google/iCloud. " +
                                    "Events sync one-way into this agenda; nothing is written back.",
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                            )
                            LightText(
                                text = "Long URL? Tap the camera below to scan a QR code instead of " +
                                    "typing it - see this tool's README for how to generate one.",
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                modifier = Modifier.padding(bottom = 1.5f.gridUnitsAsDp()),
                            )

                            LightTextField(
                                label = "Name",
                                value = label,
                                placeholder = "e.g. Work",
                                onClick = { editingField = EditingField.LABEL },
                                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                            )

                            LightTextField(
                                label = "ICS URL",
                                value = icsUrl,
                                placeholder = "https://…/calendar.ics",
                                onClick = { editingField = EditingField.URL },
                                modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                            )

                            errorMessage?.let {
                                LightText(text = it, variant = LightTextVariant.Detail, lighten = true)
                            }
                        }

                        LightBottomBar(
                            items = listOf(
                                LightBarButton.Text(
                                    text = "SAVE",
                                    onClick = {
                                        if (label.isBlank() || icsUrl.isBlank()) {
                                            errorMessage = "Enter a name and an ICS URL before saving."
                                        } else {
                                            scope.launch {
                                                repository.add(label.trim(), icsUrl.trim())
                                                goBack(Unit)
                                            }
                                        }
                                    },
                                ),
                                LightBarButton.LightIcon(
                                    icon = LightIcons.CAMERA,
                                    onClick = {
                                        navigateTo(screenFactory = { AgendaQrScannerScreen(it) }) { scanned ->
                                            if (scanned != null) {
                                                icsUrl = scanned
                                                errorMessage = null
                                            }
                                        }
                                    },
                                    contentDescription = "Scan ICS URL QR code",
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }
}
