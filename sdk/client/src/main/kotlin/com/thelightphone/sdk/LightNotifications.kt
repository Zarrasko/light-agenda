package com.thelightphone.sdk

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

private const val DEFAULT_CHANNEL_ID = "light_reminders"
private const val DEFAULT_CHANNEL_NAME = "Reminders"

// One gentle pulse (0ms wait, 250ms buzz) rather than the system default pattern, which repeats
// several times - a single reminder shouldn't feel like an incoming call.
private val SHORT_VIBRATION_PATTERN = longArrayOf(0, 250)

/**
 * Minimal local-notification primitive for Light SDK tools.
 *
 * There was previously no way for a tool to surface anything outside its own UI:
 * [LightPushService]'s [LightEntryPoint.onPushNotification] only fires for a *remote*
 * server-sent push (UnifiedPush), which needs a backend a tool may not have. This posts a
 * plain local notification instead - meant to be called from a periodic [LightJob] (scheduled
 * via [LightWork.enqueuePeriodic]) that decides on-device whether something is worth
 * surfacing, e.g. "a calendar event starts in 15 minutes."
 *
 * Requires `android.permission.POST_NOTIFICATIONS` in the tool's lighttool.toml permissions,
 * and the user granting it at runtime (see [rememberNotificationPermissionRequester]) - [post]
 * silently no-ops if it isn't granted, rather than throwing.
 *
 * Defaults to silent-but-felt (no sound, a short vibration) rather than an audible alert - a
 * fitting default for a device whose whole premise is fewer, calmer interruptions, and it
 * means a tool author has to opt into noise rather than opt out of it.
 *
 * Android locks a channel's sound/vibration once created - calling [post] again with a
 * different [soundEnabled] for the same [channelId] has no effect on an already-showing
 * channel. Recreating one that's wrong requires the user to change it by hand (Settings > Apps
 * > [tool] > Notifications), or a new [channelId] the app has never used before.
 */
object LightNotifications {
    /**
     * Posts a local notification. Tapping it reopens the tool. Safe to call from a background
     * [LightJob] - does nothing if the permission isn't granted.
     *
     * @param notificationId a stable id for this specific notification - reusing an id
     *   updates/replaces a still-showing notification rather than adding a second one.
     * @param soundEnabled off by default (vibrate only) - see the class doc for why, and why
     *   this only takes effect the *first* time [channelId] is ever created on a given device.
     */
    // hasPermission() above is the real, runtime guard lint's NotificationPermission check
    // wants - it just can't trace through a private function call to see that, and a caller's
    // manifest declaring the permission doesn't change whether this guard exists.
    @SuppressLint("NotificationPermission", "MissingPermission")
    fun post(
        lightContext: SealedLightContext,
        notificationId: Int,
        title: String,
        text: String,
        channelId: String = DEFAULT_CHANNEL_ID,
        channelName: String = DEFAULT_CHANNEL_NAME,
        soundEnabled: Boolean = false,
    ) {
        if (!hasPermission(lightContext)) return
        val context = lightContext.androidContext

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT).apply {
                enableVibration(true)
                vibrationPattern = SHORT_VIBRATION_PATTERN
                if (!soundEnabled) setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(context, LightActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.thelightphone.sdk.ui.R.drawable.ic_alarm_white)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        // areNotificationsEnabled() above already gates this, but some OEM skins have been
        // known to throw here anyway - a missed reminder is a far smaller problem than crashing
        // the background job that would otherwise post the next one.
        runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
    }

    /** Whether the user has granted POST_NOTIFICATIONS. Safe to call from any thread. */
    fun hasPermission(lightContext: SealedLightContext): Boolean =
        ContextCompat.checkSelfPermission(
            lightContext.androidContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
}

/**
 * Returns a function that triggers the system "allow notifications?" prompt. [onResult] fires
 * with the outcome (true if granted, including if it was already granted). Call the returned
 * function from a click handler, same as any other Compose permission flow.
 */
@Composable
fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit = {}): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        onResult,
    )
    return { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
