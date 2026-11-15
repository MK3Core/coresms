package io.github.mk3core.coresms.mms

import android.content.Context
import android.provider.Telephony.Mms
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.mk3core.coresms.R
import io.github.mk3core.coresms.data.Prefs
import io.github.mk3core.coresms.mmslib.pdu.NotifyRespInd
import io.github.mk3core.coresms.mmslib.pdu.PduComposer
import io.github.mk3core.coresms.mmslib.pdu.PduHeaders
import io.github.mk3core.coresms.mmslib.pdu.PduParser
import io.github.mk3core.coresms.mmslib.pdu.RetrieveConf
import io.github.mk3core.coresms.notify.Notifications
import java.util.concurrent.TimeUnit

/**
 * Downloads (or re-requests) one MMS from the carrier.
 *
 * Three ways in:
 * - [Mode.NEW]: a message just arrived. Retries on its own with growing gaps
 *   (30 seconds up to about an hour, and whenever a connection comes back),
 *   then shows a Retry button.
 * - [Mode.MANUAL]: you tapped Retry. Tries even past the carrier's stated
 *   expiry, since carriers often keep messages longer than they say.
 * - [Mode.HEAL]: CoreSMS found an old undownloaded message (for example one
 *   another app gave up on) and quietly asks the carrier for it again.
 */
class MmsDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    enum class Mode(val label: String) {
        NEW("new message"),
        MANUAL("tapped retry"),
        HEAL("recovering an old message"),
    }

    private data class Pending(
        val threadId: Long,
        val contentLocation: String?,
        val transactionId: String?,
        val expiry: Long,
        val subId: Int,
        /** When the carrier announced it, in seconds: the message's place in the conversation. */
        val announcedAt: Long,
    )

    private val mode get() = runCatching { Mode.valueOf(inputData.getString(KEY_MODE) ?: "") }.getOrDefault(Mode.NEW)
    private val quiet get() = mode == Mode.HEAL

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_MESSAGE_ID, -1)
        val pending = load(id) ?: return Result.success() // already downloaded or deleted
        val context = applicationContext
        Prefs.setMmsLastAttempt(context, id, System.currentTimeMillis())
        val attempt = "Attempt ${runAttemptCount + 1} (${mode.label})"

        val location = pending.contentLocation
        if (location.isNullOrBlank()) {
            MmsLog.record(context, id, "$attempt: no carrier link stored for this message")
            fail(id, pending, context.getString(R.string.mms_error_no_location), permanent = true)
            return Result.failure()
        }
        val nowSeconds = System.currentTimeMillis() / 1000
        if (mode != Mode.MANUAL && pending.expiry in 1 until nowSeconds) {
            MmsLog.record(context, id, "$attempt: skipped, past the carrier's expiry")
            fail(id, pending, context.getString(R.string.mms_error_expired), permanent = false)
            return Result.failure()
        }

        // Some carriers hand a message out only once, so a downloaded copy is
        // kept on disk until it's safely saved. A later attempt uses that copy
        // instead of asking the carrier again.
        val kept = keptDownload(context, id)
        var downloadedFrom = MmsTransport.downloadUrl(context, location, pending.transactionId, pending.subId)
        if (kept.length() > 0) {
            MmsLog.record(context, id, "$attempt: using the copy downloaded earlier (${kb(kept.length())})")
        } else {
            MmsLog.record(context, id, "$attempt: asking the carrier")
            MmsStore.setDownloadState(context, id, MmsStore.ST_DOWNLOADING)
            val url = MmsTransport.downloadUrl(context, location, pending.transactionId, pending.subId)
            if (url != location) MmsLog.record(context, id, "Using the carrier's link with the transaction id added")
            var (result, file) = try {
                MmsTransport.download(context, url, pending.subId)
            } catch (e: Exception) {
                Log.e(TAG, "Download of MMS $id couldn't start", e)
                MmsLog.record(context, id, "Couldn't start the download: ${e.javaClass.simpleName}")
                return retryOrFail(id, pending, context.getString(R.string.mms_error_start, e.message.orEmpty()))
            }
            // A 404 may just mean the link's form was wrong for this carrier; try the other form once.
            val alternate = MmsTransport.alternateUrl(location, pending.transactionId, url)
            if (MmsTransport.isPermanent(result) && alternate != null) {
                file.delete()
                MmsLog.record(
                    context,
                    id,
                    "Carrier answered HTTP ${result.httpStatus}; trying the link " +
                        if (alternate == location) "without the transaction id" else "with the transaction id added",
                )
                val second = runCatching { MmsTransport.download(context, alternate, pending.subId) }.getOrNull()
                if (second != null) {
                    result = second.first
                    file = second.second
                    if (result.ok) downloadedFrom = alternate
                }
            }
            try {
                if (!result.ok || file.length() == 0L) {
                    val error = MmsTransport.describe(context, result)
                    Log.w(TAG, "Download of MMS $id failed: ${result.resultCode}/${result.httpStatus}")
                    MmsLog.record(
                        context,
                        id,
                        "Carrier answered: $error (result ${result.resultCode}" +
                            (if (result.httpStatus != 0) ", HTTP ${result.httpStatus})" else ")"),
                    )
                    return if (MmsTransport.isPermanent(result)) {
                        fail(id, pending, error, permanent = true)
                        Result.failure()
                    } else {
                        retryOrFail(id, pending, error)
                    }
                }
                file.copyTo(kept, overwrite = true)
                MmsLog.record(context, id, "Downloaded ${kb(kept.length())}")
            } finally {
                file.delete()
            }
        }

        val conf = runCatching { PduParser(kept.readBytes(), true).parse() as? RetrieveConf }.getOrNull()
        if (conf == null) {
            MmsLog.record(context, id, "The downloaded message couldn't be read; the copy is kept")
            return retryOrFail(id, pending, context.getString(R.string.mms_error_unreadable))
        }
        val status = conf.retrieveStatus
        if (status != 0 && status != PduHeaders.RETRIEVE_STATUS_OK) {
            MmsLog.record(context, id, "Carrier sent an error instead of the message (status $status)")
            kept.delete()
            fail(id, pending, context.getString(R.string.mms_error_carrier_status, status), permanent = true)
            return Result.failure()
        }

        val newId = MmsStore.persistRetrieved(context, conf, pending.subId, pending.transactionId, location, pending.announcedAt)
        if (newId == null) {
            MmsLog.record(context, id, "Couldn't save the message; the copy is kept")
            return retryOrFail(id, pending, context.getString(R.string.mms_error_save))
        }

        // The real message is saved; remove the placeholder and the kept copy.
        MmsStore.delete(context, id)
        Prefs.clearMmsState(context, id)
        MmsLog.record(context, id, "Saved")
        MmsLog.move(context, id, newId)
        kept.delete()
        acknowledge(pending, newId, downloadedFrom)
        Notifications.updateForNewMessage(context, threadIdOf(newId) ?: pending.threadId)
        return Result.success()
    }

    private fun retryOrFail(id: Long, pending: Pending, error: String): Result {
        Prefs.setMmsError(applicationContext, id, error)
        val maxAttempts = if (mode == Mode.NEW) MAX_ATTEMPTS_NEW else MAX_ATTEMPTS_OTHER
        return if (runAttemptCount + 1 >= maxAttempts) {
            fail(id, pending, error, permanent = false)
            Result.failure()
        } else {
            MmsStore.setDownloadState(applicationContext, id, MmsStore.ST_TRANSIENT_FAILURE)
            // The first try of a new message failed, so this may take a while:
            // let the person know a message is waiting rather than staying silent.
            if (mode == Mode.NEW && runAttemptCount == 0) {
                Notifications.updateForNewMessage(applicationContext, pending.threadId)
            }
            Result.retry()
        }
    }

    private fun fail(id: Long, pending: Pending, error: String, permanent: Boolean) {
        val context = applicationContext
        MmsLog.record(context, id, if (permanent) "Gave up: the carrier won't provide it" else "Stopped retrying on its own")
        Prefs.setMmsError(context, id, error)
        if (permanent) Prefs.setMmsGone(context, id)
        MmsStore.setDownloadState(
            context,
            id,
            if (permanent) MmsStore.ST_PERMANENT_FAILURE else MmsStore.ST_TRANSIENT_FAILURE,
        )
        if (!quiet) Notifications.showMmsDownloadFailed(context, pending.threadId)
    }

    /** Tells the carrier we have the message, so it stops resending the notification. */
    private suspend fun acknowledge(pending: Pending, messageId: Long, downloadUrl: String) {
        val transactionId = pending.transactionId ?: return
        runCatching {
            val ack = NotifyRespInd(
                PduHeaders.CURRENT_MMS_VERSION,
                transactionId.toByteArray(Charsets.ISO_8859_1),
                PduHeaders.STATUS_RETRIEVED,
            )
            val bytes = PduComposer(applicationContext, ack).make() ?: return
            // Some carriers (Verizon) want the confirmation at the download address.
            val locationUrl = downloadUrl.takeIf { MmsTransport.notifyAtDownloadUrl(applicationContext, pending.subId) }
            val result = MmsTransport.sendAndWait(applicationContext, bytes, pending.subId, locationUrl)
            MmsLog.record(
                applicationContext,
                messageId,
                if (result.ok) "Told the carrier it was received" else "Couldn't confirm receipt to the carrier (result ${result.resultCode})",
            )
        }.onFailure { Log.w(TAG, "Couldn't acknowledge MMS", it) }
    }

    private fun kb(bytes: Long) = "${(bytes + 1023) / 1024} KB"

    private fun load(id: Long): Pending? = runCatching {
        applicationContext.contentResolver.query(
            MmsStore.messageUri(id),
            arrayOf(Mms.THREAD_ID, Mms.CONTENT_LOCATION, Mms.TRANSACTION_ID, Mms.EXPIRY, Mms.SUBSCRIPTION_ID, Mms.MESSAGE_TYPE, Mms.DATE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            if (cursor.getInt(5) != PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) return@use null
            Pending(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3), cursor.getInt(4), cursor.getLong(6))
        }
    }.getOrNull()

    private fun threadIdOf(id: Long): Long? = runCatching {
        applicationContext.contentResolver.query(MmsStore.messageUri(id), arrayOf(Mms.THREAD_ID), null, null, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }.getOrNull()

    companion object {
        private const val TAG = "MmsDownloadWorker"
        private const val KEY_MESSAGE_ID = "message_id"
        private const val KEY_MODE = "mode"
        private const val MAX_ATTEMPTS_NEW = 8
        private const val MAX_ATTEMPTS_OTHER = 3

        fun uniqueName(messageId: Long) = "mms-download-$messageId"

        /** Where a downloaded message waits until it's safely saved. */
        fun keptDownload(context: Context, messageId: Long): java.io.File =
            java.io.File(java.io.File(context.filesDir, "mms-downloads").apply { mkdirs() }, "$messageId.pdu")

        fun enqueue(context: Context, messageId: Long, mode: Mode = Mode.NEW) {
            MmsStore.setDownloadState(context, messageId, MmsStore.ST_UNSTARTED)
            if (mode == Mode.MANUAL) Prefs.clearMmsState(context, messageId)
            val request = OneTimeWorkRequestBuilder<MmsDownloadWorker>()
                .setInputData(workDataOf(KEY_MESSAGE_ID to messageId, KEY_MODE to mode.name))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueName(messageId), ExistingWorkPolicy.REPLACE, request)
        }

        /** True while a download is queued, running or waiting to retry. */
        fun isQueued(context: Context, messageId: Long): Boolean = runCatching {
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(uniqueName(messageId)).get()
                .any { !it.state.isFinished }
        }.getOrDefault(false)
    }
}
