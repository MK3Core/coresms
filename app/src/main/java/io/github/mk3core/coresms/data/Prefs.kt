package io.github.mk3core.coresms.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** CoreSMS's own small settings. Messages themselves live in Android's SMS/MMS database. */
object Prefs {

    private const val FILE = "coresms"
    private const val KEY_PINNED = "pinned_threads"
    private const val KEY_MY_NUMBER = "my_number"
    private const val KEY_MMS_ERROR_PREFIX = "mms_error_"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // Pinned conversations, in the order they were pinned.

    fun pinnedThreads(context: Context): List<Long> =
        prefs(context).getString(KEY_PINNED, "").orEmpty()
            .split(',').mapNotNull { it.toLongOrNull() }

    fun setPinned(context: Context, threadId: Long, pinned: Boolean) {
        val current = pinnedThreads(context).toMutableList()
        current.remove(threadId)
        if (pinned) current += threadId
        prefs(context).edit { putString(KEY_PINNED, current.joinToString(",")) }
    }

    // Our own number, typed in by hand when the SIM doesn't report it.

    fun myNumber(context: Context): String? =
        prefs(context).getString(KEY_MY_NUMBER, null)?.takeIf { it.isNotBlank() }

    fun setMyNumber(context: Context, number: String?) {
        prefs(context).edit { putString(KEY_MY_NUMBER, number?.trim()) }
    }

    // Our number as found in messages sent by an earlier app (see SelfNumbers).

    fun detectedNumber(context: Context): String? =
        prefs(context).getString(KEY_DETECTED_NUMBER, null)?.takeIf { it.isNotBlank() }

    fun setDetectedNumber(context: Context, number: String?) {
        prefs(context).edit { putString(KEY_DETECTED_NUMBER, number) }
    }

    // Download state for MMS that haven't downloaded yet.

    /** Why an MMS last failed to download, shown on its bubble. */
    fun mmsError(context: Context, messageId: Long): String? =
        prefs(context).getString(KEY_MMS_ERROR_PREFIX + messageId, null)

    fun setMmsError(context: Context, messageId: Long, error: String?) {
        prefs(context).edit {
            if (error == null) remove(KEY_MMS_ERROR_PREFIX + messageId)
            else putString(KEY_MMS_ERROR_PREFIX + messageId, error)
        }
    }

    fun mmsLastAttempt(context: Context, messageId: Long): Long =
        prefs(context).getLong(KEY_MMS_ATTEMPT_PREFIX + messageId, 0)

    fun setMmsLastAttempt(context: Context, messageId: Long, millis: Long) {
        prefs(context).edit { putLong(KEY_MMS_ATTEMPT_PREFIX + messageId, millis) }
    }

    /** The carrier said it no longer has this message; don't retry it automatically. */
    fun isMmsGone(context: Context, messageId: Long): Boolean =
        prefs(context).getBoolean(KEY_MMS_GONE_PREFIX + messageId, false)

    fun setMmsGone(context: Context, messageId: Long) {
        prefs(context).edit { putBoolean(KEY_MMS_GONE_PREFIX + messageId, true) }
    }

    /** Forgets everything about an MMS's download (on success, or before a manual retry). */
    fun clearMmsState(context: Context, messageId: Long) {
        prefs(context).edit {
            remove(KEY_MMS_ERROR_PREFIX + messageId)
            remove(KEY_MMS_ATTEMPT_PREFIX + messageId)
            remove(KEY_MMS_GONE_PREFIX + messageId)
        }
    }

    private const val KEY_DETECTED_NUMBER = "detected_number"
    private const val KEY_MMS_ATTEMPT_PREFIX = "mms_attempt_"
    private const val KEY_MMS_GONE_PREFIX = "mms_gone_"
}
