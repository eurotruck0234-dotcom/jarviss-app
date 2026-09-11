package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

object ContactCaller {

    data class Match(val name: String, val number: String)

    /**
     * Looks through the phone's contacts for a display name that contains the
     * given nickname (case-insensitive, Turkish-friendly, partial match).
     */
    fun findContact(context: Context, nickname: String): Match? {
        val needle = nickname.trim().lowercase()
        if (needle.isBlank()) return null

        val resolver = context.contentResolver
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        ) ?: return null

        var best: Match? = null
        cursor.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val name = it.getString(nameIdx) ?: continue
                val number = it.getString(numIdx) ?: continue
                if (name.lowercase().contains(needle)) {
                    best = Match(name, number)
                    break
                }
            }
        }
        return best
    }

    /**
     * Places a direct phone call. Requires CALL_PHONE permission to already be granted.
     */
    fun call(context: Context, number: String) {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$number")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Tries to extract "X ara" / "X'i ara" style commands.
     * Returns the nickname part, or null if this doesn't look like a call command.
     */
    fun extractCallTarget(message: String): String? {
        val lower = message.lowercase().trim()
        if (!lower.endsWith("ara") && !lower.contains(" ara ") && !lower.contains("aramanı")) return null
        if (!lower.contains("ara")) return null

        // Remove common suffixes/verbs to isolate the name
        var name = lower
            .replace(Regex("\\b(bana|lütfen|hemen|şimdi)\\b"), "")
            .replace(Regex("\\b(ara|aramanı|arasana|istiyorum|ister misin|misin|mısın)\\b"), "")
            .replace(Regex("[’'`\"]"), "")
            .trim()

        // strip trailing possessive suffixes like "'i", "'yi", "'nı" already handled above roughly
        name = name.replace(Regex("\\s+"), " ").trim()
        return if (name.isBlank()) null else name
    }
}
