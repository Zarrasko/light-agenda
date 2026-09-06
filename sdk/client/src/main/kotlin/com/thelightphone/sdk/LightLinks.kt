package com.thelightphone.sdk

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri

/**
 * Hands a URL off to whatever app on the device claims it - a browser for http(s), a mail app
 * for mailto:, a dialer for tel:, or a FaceTime-web link opened in whatever the default browser
 * is. Agenda-style tools don't need camera/mic permissions of their own for this: the app that
 * actually resolves the link (a browser joining a video call, say) requests those itself.
 *
 * `startActivity()` for an implicit ACTION_VIEW intent isn't subject to Android 11+ package
 * visibility restrictions the way introspection APIs (queryIntentActivities, resolveActivity)
 * are, so this doesn't need a `<queries>` manifest entry - it either launches a matching
 * activity or throws, never silently "sees nothing" due to visibility filtering.
 */
object LightLinks {
    /** Returns false if nothing on the device can open [url], true otherwise. */
    fun open(lightContext: SealedLightContext, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            lightContext.androidContext.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
