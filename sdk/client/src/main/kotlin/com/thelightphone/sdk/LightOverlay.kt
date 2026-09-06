package com.thelightphone.sdk

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibratorManager
import android.os.VibrationEffect
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.graphics.PixelFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

private const val SWIPE_UP_DISMISS_THRESHOLD_PX = -60f

// A ComposeView added directly via WindowManager has no Activity behind it, so it has none of
// the owners Compose needs (lifecycle, saved state, view model store) - this manufactures
// minimal ones, matching the pattern most overlay/bubble libraries use for the same problem.
private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun start() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}

/**
 * A heads-up-style alert that draws over whatever the user is doing, for LightOS builds where
 * the standard notification shade isn't reachable - [LightNotifications] posts a real,
 * correctly-configured notification, but it's invisible if the user has no way to open the
 * shade at all. Needs `android.permission.SYSTEM_ALERT_WINDOW`, which has no one-tap runtime
 * prompt (see [rememberOverlayPermissionRequester]).
 */
object LightOverlay {
    // The one currently-showing overlay, if any - a fresh show() replaces it rather than
    // stacking a second box on top, since this is meant to read as a single persistent status
    // element (closer to a widget than a notification feed).
    private var active: ActiveOverlay? = null

    private class ActiveOverlay(val windowManager: WindowManager, val composeView: ComposeView, val owner: OverlayLifecycleOwner) {
        var removed = false
        fun remove() {
            if (removed) return
            removed = true
            runCatching { windowManager.removeViewImmediate(composeView) }
            owner.destroy()
        }
    }

    /** Whether "Display over other apps" is currently granted. */
    fun canShow(lightContext: SealedLightContext): Boolean =
        Settings.canDrawOverlays(lightContext.androidContext)

    /**
     * Shows a small bordered card near the top of the screen - it stays until the user taps it
     * (reopens the tool) or swipes it up (dismisses), and never times out on its own, closer to
     * a persistent widget than a notification that vanishes unread. Replaces any overlay from a
     * previous [show] call that's still up. Also fires a single short vibration, since this is
     * meant to be felt as much as seen. No-ops if the permission isn't granted.
     */
    fun show(
        lightContext: SealedLightContext,
        title: String,
        text: String,
    ) {
        if (!canShow(lightContext)) return
        val context = lightContext.androidContext
        val mainHandler = Handler(Looper.getMainLooper())

        mainHandler.post {
            active?.remove()
            active = null

            val windowManager = context.getSystemService(WindowManager::class.java)
            val owner = OverlayLifecycleOwner().apply { start() }

            // openTool()/onSwipeDismiss need to close over the ActiveOverlay holder, but it
            // can't be built until composeView exists, and composeView's setContent needs
            // those same callbacks - declared here, assigned once the view is constructed.
            lateinit var overlay: ActiveOverlay

            fun removeIfCurrent() {
                // Only remove if this is still the active one - a later show() may have already
                // replaced (and removed) it, in which case there's nothing left to do here.
                if (active === overlay) active = null
                overlay.remove()
            }

            fun openTool() {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    ?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                launchIntent?.let { context.startActivity(it) }
                removeIfCurrent()
            }

            val composeView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    OverlayCard(
                        title = title,
                        text = text,
                        onTap = ::openTool,
                        onSwipeDismiss = ::removeIfCurrent,
                    )
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    // A reminder that only shows if the screen already happens to be on isn't
                    // much of a reminder - these mirror how an incoming call or alarm wakes the
                    // device, but only for that first instant: no FLAG_KEEP_SCREEN_ON, since
                    // this box is meant to persist indefinitely and forcing the screen to stay
                    // lit the whole time would be a real battery cost. The screen wakes once,
                    // then goes back to its normal sleep timer - the box is simply still there
                    // next time the screen turns on for any reason. FLAG_DISMISS_KEYGUARD
                    // actually unlocks the device to show the box - only acceptable because
                    // this tool is being run with no lock security set; on a device with a real
                    // PIN/pattern/biometric lock this would be a serious security regression and
                    // should be dropped (FLAG_SHOW_WHEN_LOCKED alone is the safe default - it
                    // draws over an unsecured lock screen without bypassing a real one, though
                    // LightOS's own idle/clock screen turned out not to honor even that without
                    // the dismiss flag alongside it).
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP
                y = 24
            }

            overlay = ActiveOverlay(windowManager, composeView, owner)
            runCatching { windowManager.addView(composeView, params) }
                .onFailure { owner.destroy(); return@post }
            active = overlay

            runCatching {
                val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }
}

@Composable
private fun OverlayCard(
    title: String,
    text: String,
    onTap: () -> Unit,
    onSwipeDismiss: () -> Unit,
) {
    var dragAccumulator = 0f
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(Color.Black, RoundedCornerShape(12.dp))
            .border(1.dp, Color.White, RoundedCornerShape(12.dp))
            .padding(14.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                        if (dragAccumulator < SWIPE_UP_DISMISS_THRESHOLD_PX) onSwipeDismiss()
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            },
    ) {
        Icon(
            painter = painterResource(com.thelightphone.sdk.ui.R.drawable.ic_alarm_white),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.padding(top = 2.dp, end = 10.dp),
        )
        Column {
            Text(text = title, color = Color.White, fontSize = 14.sp)
            Text(text = text, color = Color(0xFFB5B5B5), fontSize = 12.sp)
        }
    }
}

/**
 * Returns a function that opens the system "Display over other apps" settings screen for this
 * tool. Unlike [rememberNotificationPermissionRequester], there's no dialog and no reliable
 * granted/denied result code for this permission class - [onResult] fires when the user
 * returns to the app, re-checking [LightOverlay.canShow] directly rather than trusting the
 * activity result.
 */
@Composable
fun rememberOverlayPermissionRequester(onResult: (Boolean) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { onResult(Settings.canDrawOverlays(context)) }

    return {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        launcher.launch(intent)
    }
}
