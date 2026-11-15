package io.github.mk3core.coresms.mms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.github.mk3core.coresms.R
import io.github.mk3core.coresms.sms.SmsManagers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Moves MMS between the phone and the carrier through Android's MMS service
 * (SmsManager), which brings up the carrier's MMS data connection for us.
 * PDUs are handed over as files shared through our FileProvider.
 */
object MmsTransport {

    data class Result(val ok: Boolean, val resultCode: Int, val httpStatus: Int, val data: ByteArray?)

    private fun dir(context: Context) = File(context.cacheDir, "mms").apply { mkdirs() }

    fun newFile(context: Context, prefix: String): File = File(dir(context), "$prefix-${UUID.randomUUID()}.pdu")

    fun shareUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.mmsfiles", file)

    /** Downloads the MMS at [contentLocation] into a file. Returns the result and the file. */
    suspend fun download(context: Context, contentLocation: String, subId: Int): Pair<Result, File> {
        val file = newFile(context, "download")
        file.createNewFile()
        val uri = shareUri(context, file)
        val result = withTimeoutOrNull(TIMEOUT_MS) {
            awaitResult(context) { sent ->
                SmsManagers.forSubscription(context, subId)
                    .downloadMultimediaMessage(context, contentLocation, uri, null, sent)
            }
        } ?: Result(ok = false, resultCode = ERROR_TIMEOUT, httpStatus = 0, data = null)
        return result to file
    }

    /** Sends a PDU and waits for the result. Used for small protocol messages like acknowledgements. */
    suspend fun sendAndWait(context: Context, pdu: ByteArray, subId: Int): Result {
        val file = newFile(context, "ack")
        file.writeBytes(pdu)
        val uri = shareUri(context, file)
        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                awaitResult(context) { sent ->
                    SmsManagers.forSubscription(context, subId)
                        .sendMultimediaMessage(context, uri, null, null, sent)
                }
            } ?: Result(ok = false, resultCode = ERROR_TIMEOUT, httpStatus = 0, data = null)
        } finally {
            file.delete()
        }
    }

    private suspend fun awaitResult(context: Context, start: (PendingIntent) -> Unit): Result =
        suspendCancellableCoroutine { continuation ->
            val app = context.applicationContext
            val action = "${app.packageName}.action.MMS_RESULT.${UUID.randomUUID()}"
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    runCatching { app.unregisterReceiver(this) }
                    val result = Result(
                        ok = resultCode == Activity.RESULT_OK,
                        resultCode = resultCode,
                        httpStatus = intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, 0),
                        data = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA),
                    )
                    if (continuation.isActive) continuation.resume(result)
                }
            }
            ContextCompat.registerReceiver(app, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
            // Mutable so the MMS service can add the HTTP status and response.
            val pending = PendingIntent.getBroadcast(
                app,
                0,
                Intent(action).setPackage(app.packageName),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT,
            )
            continuation.invokeOnCancellation { runCatching { app.unregisterReceiver(receiver) } }
            try {
                start(pending)
            } catch (e: Exception) {
                runCatching { app.unregisterReceiver(receiver) }
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }

    /** A plain-words explanation of a failed download or send. */
    fun describe(context: Context, result: Result): String = when (result.resultCode) {
        ERROR_INVALID_APN, ERROR_CONFIGURATION -> context.getString(R.string.mms_error_settings)
        ERROR_UNABLE_CONNECT -> context.getString(R.string.mms_error_connect)
        ERROR_HTTP -> when (result.httpStatus) {
            404, 410 -> context.getString(R.string.mms_error_gone)
            else -> context.getString(R.string.mms_error_http, result.httpStatus)
        }
        ERROR_IO -> context.getString(R.string.mms_error_io)
        ERROR_RETRY -> context.getString(R.string.mms_error_retry)
        ERROR_NO_DATA_NETWORK -> context.getString(R.string.mms_error_no_data)
        ERROR_INVALID_SUBSCRIPTION, ERROR_INACTIVE_SUBSCRIPTION -> context.getString(R.string.mms_error_sim)
        ERROR_DATA_DISABLED -> context.getString(R.string.mms_error_data_off)
        ERROR_DISABLED_BY_CARRIER -> context.getString(R.string.mms_error_carrier_disabled)
        ERROR_TIMEOUT -> context.getString(R.string.mms_error_timeout)
        else -> context.getString(R.string.mms_error_unknown, result.resultCode)
    }

    /** Errors where trying again can't help: the carrier no longer has the message. */
    fun isPermanent(result: Result) = result.resultCode == ERROR_HTTP && result.httpStatus in setOf(404, 410)

    private const val TIMEOUT_MS = 3 * 60 * 1000L

    // SmsManager.MMS_ERROR_* values, spelled out because some only exist on newer Android versions.
    private const val ERROR_INVALID_APN = 2
    private const val ERROR_UNABLE_CONNECT = 3
    private const val ERROR_HTTP = 4
    private const val ERROR_IO = 5
    private const val ERROR_RETRY = 6
    private const val ERROR_CONFIGURATION = 7
    private const val ERROR_NO_DATA_NETWORK = 8
    private const val ERROR_INVALID_SUBSCRIPTION = 9
    private const val ERROR_INACTIVE_SUBSCRIPTION = 10
    private const val ERROR_DATA_DISABLED = 11
    private const val ERROR_DISABLED_BY_CARRIER = 12
    private const val ERROR_TIMEOUT = -1000
}
