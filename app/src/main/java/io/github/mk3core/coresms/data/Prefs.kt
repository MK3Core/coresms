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

    // Why an MMS last failed to download, shown on its bubble.

    fun mmsError(context: Context, messageId: Long): String? =
        prefs(context).getString(KEY_MMS_ERROR_PREFIX + messageId, null)

    fun setMmsError(context: Context, messageId: Long, error: String?) {
        prefs(context).edit {
            if (error == null) remove(KEY_MMS_ERROR_PREFIX + messageId)
            else putString(KEY_MMS_ERROR_PREFIX + messageId, error)
        }
    }
}
