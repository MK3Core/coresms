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

    enum class Mode { NEW, MANUAL, HEAL }

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

        val location = pending.contentLocation
        if (location.isNullOrBlank()) {
            fail(id, pending, context.getString(R.string.mms_error_no_location), permanent = true)
            return Result.failure()
        }
        val nowSeconds = System.currentTimeMillis() / 1000
        if (mode != Mode.MANUAL && pending.expiry in 1 until nowSeconds) {
            fail(id, pending, context.getString(R.string.mms_error_expired), permanent = false)
            return Result.failure()
        }

        MmsStore.setDownloadState(context, id, MmsStore.ST_DOWNLOADING)
        val (result, file) = try {
            MmsTransport.download(context, location, pending.subId)
        } catch (e: Exception) {
            Log.e(TAG, "Download of MMS $id couldn't start", e)
            return retryOrFail(id, pending, context.getString(R.string.mms_error_start, e.message.orEmpty()))
        }

        try {
            if (!result.ok || file.length() == 0L) {
                val error = MmsTransport.describe(context, result)
                Log.w(TAG, "Download of MMS $id failed: ${result.resultCode}/${result.httpStatus}")
                return if (MmsTransport.isPermanent(result)) {
                    fail(id, pending, error, permanent = true)
                    Result.failure()
                } else {
                    retryOrFail(id, pending, error)
                }
            }

            val conf = runCatching { PduParser(file.readBytes(), true).parse() as? RetrieveConf }.getOrNull()
            if (conf == null) {
                return retryOrFail(id, pending, context.getString(R.string.mms_error_unreadable))
            }
            val status = conf.retrieveStatus
            if (status != 0 && status != PduHeaders.RETRIEVE_STATUS_OK) {
                fail(id, pending, context.getString(R.string.mms_error_carrier_status, status), permanent = true)
                return Result.failure()
            }

            val newId = MmsStore.persistRetrieved(context, conf, pending.subId, pending.transactionId, location, pending.announcedAt)
                ?: return retryOrFail(id, pending, context.getString(R.string.mms_error_save))

            // The real message is saved; remove the placeholder.
            MmsStore.delete(context, id)
            Prefs.clearMmsState(context, id)
            acknowledge(pending)
            Notifications.updateForNewMessage(context, threadIdOf(newId) ?: pending.threadId)
            return Result.success()
        } finally {
            file.delete()
        }
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
    private suspend fun acknowledge(pending: Pending) {
        val transactionId = pending.transactionId ?: return
        runCatching {
            val ack = NotifyRespInd(
                PduHeaders.CURRENT_MMS_VERSION,
                transactionId.toByteArray(Charsets.ISO_8859_1),
                PduHeaders.STATUS_RETRIEVED,
            )
            val bytes = PduComposer(applicationContext, ack).make() ?: return
            MmsTransport.sendAndWait(applicationContext, bytes, pending.subId)
        }.onFailure { Log.w(TAG, "Couldn't acknowledge MMS", it) }
    }

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
