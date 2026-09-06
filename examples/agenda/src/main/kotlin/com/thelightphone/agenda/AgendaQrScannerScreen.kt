package com.thelightphone.agenda

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.LightQrCodeScanner
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens

// Scans a QR code containing just the raw ICS URL - generate one offline (see this tool's
// README) so a private calendar link never touches a third-party web service. Same pattern as
// Pulse's PulseQrScannerScreen, for the same reason: typing a 100+ character URL on the
// embedded keyboard one letter at a time is painful, and there's no clipboard/paste support to
// fall back on (LightTextInputEditor has no ClipboardManager/TextToolbar wiring).
class AgendaQrScannerScreen(
    sealedActivity: SealedLightActivity,
) : SimpleLightScreen<String?>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        var pendingScan by remember { mutableStateOf<String?>(null) }

        LightTheme(colors = themeColors) {
            LightQrCodeScanner(
                title = "Scan ICS URL",
                onScanned = { pendingScan = it },
                onBack = { goBack() },
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }

        LaunchedEffect(pendingScan) {
            val value = pendingScan?.trim()?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
            goBack(value)
        }
    }
}
