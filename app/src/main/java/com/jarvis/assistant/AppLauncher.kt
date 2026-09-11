package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri

object AppLauncher {

    // Turkish trigger word -> possible package names (first match found on device wins)
    private val APP_MAP = mapOf(
        "youtube" to listOf("com.google.android.youtube"),
        "tv" to listOf(
            "com.google.android.videos",
            "com.android.tv.settings",
            "com.google.android.tvlauncher"
        ),
        "google tv" to listOf("com.google.android.videos", "com.google.android.tvlauncher"),
        "netflix" to listOf("com.netflix.mediaclient"),
        "spotify" to listOf("com.spotify.music"),
        "whatsapp" to listOf("com.whatsapp"),
        "instagram" to listOf("com.instagram.android"),
        "kamera" to listOf("com.android.camera", "com.tecno.camera"),
        "ayarlar" to listOf("com.android.settings"),
        "chrome" to listOf("com.android.chrome")
    )

    /**
     * Returns the app keyword mentioned in the message ("aç" command), or null.
     */
    fun extractAppTarget(message: String): String? {
        val lower = message.lowercase().trim()
        if (!lower.contains("aç")) return null
        for (key in APP_MAP.keys) {
            if (lower.contains(key)) return key
        }
        return null
    }

    /**
     * Tries to open the requested app. Returns true if it managed to launch something.
     */
    fun openApp(context: Context, keyword: String): Boolean {
        val candidates = APP_MAP[keyword] ?: return false
        val pm = context.packageManager
        for (pkg in candidates) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
        }
        // Fallback for YouTube: open via web/deeplink even if app not resolved by package
        if (keyword == "youtube") {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        }
        return false
    }
}
