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
 * Downloads one MMS from the carrier. Retries on its own with growing gaps
 * (from 30 seconds up to about an hour, and whenever a connection comes
 * back) before giving up and showing a Retry button, which works until the
 * carrier's expiry time.
 */
class MmsDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private data class Pending(
        val threadId: Long,
        val contentLocation: String,
        val transactionId: String?,
        val expiry: Long,
        val subId: Int,
    )

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_MESSAGE_ID, -1)
        val pending = load(id) ?: return Result.success() // already downloaded or deleted
        val context = applicationContext

        val nowSeconds = System.currentTimeMillis() / 1000
        if (pending.expiry in 1 until nowSeconds) {
            fail(id, pending, context.getString(R.string.mms_error_expired), permanent = true)
            return Result.failure()
        }

        MmsStore.setDownloadState(context, id, MmsStore.ST_DOWNLOADING)
        val (result, file) = try {
            MmsTransport.download(context, pending.contentLocation, pending.subId)
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

            val newId = MmsStore.persistRetrieved(
                context,
                conf,
                pending.subId,
                pending.transactionId,
                pending.contentLocation,
            ) ?: return retryOrFail(id, pending, context.getString(R.string.mms_error_save))

            // The real message is saved; remove the placeholder.
            MmsStore.delete(context, id)
            Prefs.setMmsError(context, id, null)
            acknowledge(pending)
            Notifications.updateForNewMessage(context, threadIdOf(newId) ?: pending.threadId)
            return Result.success()
        } finally {
            file.delete()
        }
    }

    private fun retryOrFail(id: Long, pending: Pending, error: String): Result {
        Prefs.setMmsError(applicationContext, id, error)
        return if (runAttemptCount + 1 >= MAX_AUTOMATIC_ATTEMPTS) {
            fail(id, pending, error, permanent = false)
            Result.failure()
        } else {
            MmsStore.setDownloadState(applicationContext, id, MmsStore.ST_TRANSIENT_FAILURE)
            // The first try failed, so this may take a while: let the person
            // know a message is waiting rather than staying silent.
            if (runAttemptCount == 0) Notifications.updateForNewMessage(applicationContext, pending.threadId)
            Result.retry()
        }
    }

    private fun fail(id: Long, pending: Pending, error: String, permanent: Boolean) {
        val context = applicationContext
        Prefs.setMmsError(context, id, error)
        MmsStore.setDownloadState(
            context,
            id,
            if (permanent) MmsStore.ST_PERMANENT_FAILURE else MmsStore.ST_TRANSIENT_FAILURE,
        )
        Notifications.showMmsDownloadFailed(context, pending.threadId)
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
            arrayOf(Mms.THREAD_ID, Mms.CONTENT_LOCATION, Mms.TRANSACTION_ID, Mms.EXPIRY, Mms.SUBSCRIPTION_ID, Mms.MESSAGE_TYPE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            if (cursor.getInt(5) != PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) return@use null
            val location = cursor.getString(1) ?: return@use null
            Pending(cursor.getLong(0), location, cursor.getString(2), cursor.getLong(3), cursor.getInt(4))
        }
    }.getOrNull()

    private fun threadIdOf(id: Long): Long? = runCatching {
        applicationContext.contentResolver.query(MmsStore.messageUri(id), arrayOf(Mms.THREAD_ID), null, null, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }.getOrNull()

    companion object {
        private const val TAG = "MmsDownloadWorker"
        private const val KEY_MESSAGE_ID = "message_id"
        private const val MAX_AUTOMATIC_ATTEMPTS = 8

        fun enqueue(context: Context, messageId: Long) {
            MmsStore.setDownloadState(context, messageId, MmsStore.ST_UNSTARTED)
            val request = OneTimeWorkRequestBuilder<MmsDownloadWorker>()
                .setInputData(workDataOf(KEY_MESSAGE_ID to messageId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("mms-download-$messageId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
