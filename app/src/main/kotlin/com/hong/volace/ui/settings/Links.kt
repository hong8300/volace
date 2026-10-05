package com.hong.volace.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Pages in the public repository. The app has no network permission: these open in the browser. */
object Links {
    private const val BASE = "https://github.com/hong8300/volace/blob/main"

    /** Google Play wants the privacy policy on the store listing and inside the app. */
    const val PRIVACY = "$BASE/PRIVACY.md"

    /** The app's own license (linked from its first lines), the third-party licenses and their notices. */
    const val NOTICES = "$BASE/THIRD_PARTY_NOTICES.md"
}

/** Opens [url] in the browser; nothing happens on a device without one. */
fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}
