package io.github.mk3core.coresms.mms

import android.content.Context
import android.provider.Telephony.Mms
import io.github.mk3core.coresms.data.Prefs
import io.github.mk3core.coresms.mmslib.pdu.PduHeaders

/**
 * Finds MMS that were announced but never downloaded (including ones another
 * messaging app gave up on) and asks the carrier for them again.
 */
object MmsRecovery {

    /** Don't re-ask the carrier for the same message more often than this. */
    private const val MIN_GAP_MS = 60 * 60 * 1000L

    data class Undownloaded(val id: Long, val threadId: Long, val expiryMillis: Long?) {
        val expired get() = expiryMillis != null && expiryMillis < System.currentTimeMillis()
    }

    fun undownloaded(context: Context): List<Undownloaded> {
        val result = mutableListOf<Undownloaded>()
        runCatching {
            context.contentResolver.query(
                Mms.CONTENT_URI,
                arrayOf(Mms._ID, Mms.THREAD_ID, Mms.EXPIRY),
                "${Mms.MESSAGE_TYPE} = ${PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND}",
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val expiry = cursor.getLong(2).takeIf { it > 0 }?.times(1000)
                    result += Undownloaded(cursor.getLong(0), cursor.getLong(1), expiry)
                }
            }
        }
        return result
    }

    /** Still worth showing at the top of the list: not known to be gone or expired. */
    fun needsAttention(context: Context, message: Undownloaded): Boolean =
        !message.expired && !Prefs.isMmsGone(context, message.id)

    /**
     * Quietly re-requests every undownloaded MMS that isn't expired, known to
     * be gone, already queued, or tried within the last hour. Runs at startup.
     */
    fun heal(context: Context) {
        val now = System.currentTimeMillis()
        for (message in undownloaded(context)) {
            if (message.expired || Prefs.isMmsGone(context, message.id)) continue
            if (now - Prefs.mmsLastAttempt(context, message.id) < MIN_GAP_MS) continue
            if (MmsDownloadWorker.isQueued(context, message.id)) continue
            MmsDownloadWorker.enqueue(context, message.id, MmsDownloadWorker.Mode.HEAL)
        }
    }
}
