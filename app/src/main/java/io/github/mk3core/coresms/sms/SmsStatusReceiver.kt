package io.github.mk3core.coresms.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import io.github.mk3core.coresms.notify.Notifications

/** Moves an outgoing SMS from the outbox to sent or failed once the radio reports back. */
class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SENT) return
        val uri = intent.data ?: return
        val sent = resultCode == Activity.RESULT_OK
        markResult(context, uri, sent, resultCode)
        if (!sent) {
            val threadId = runCatching {
                context.contentResolver.query(uri, arrayOf(Telephony.Sms.THREAD_ID), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getLong(0) else -1L }
            }.getOrNull() ?: -1L
            Notifications.showSendFailed(context, threadId)
        }
    }

    companion object {
        private const val ACTION_SENT = "io.github.mk3core.coresms.action.SMS_SENT"
        private const val EXTRA_ADDRESS = "address"

        fun sentIntent(context: Context, messageUri: Uri, address: String, part: Int): PendingIntent {
            val intent = Intent(context, SmsStatusReceiver::class.java)
                .setAction(ACTION_SENT)
                .setData(messageUri)
                .putExtra(EXTRA_ADDRESS, address)
            return PendingIntent.getBroadcast(
                context,
                part,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        fun markResult(context: Context, messageUri: Uri, sent: Boolean, errorCode: Int) {
            val values = ContentValues().apply {
                put(
                    Telephony.Sms.TYPE,
                    if (sent) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_FAILED,
                )
                if (!sent) put(Telephony.Sms.ERROR_CODE, errorCode)
            }
            runCatching {
                if (sent) {
                    // Only move it out of the outbox, so one failed part of a
                    // long text isn't overwritten by a later part's success.
                    context.contentResolver.update(
                        messageUri,
                        values,
                        "${Telephony.Sms.TYPE} = ?",
                        arrayOf(Telephony.Sms.MESSAGE_TYPE_OUTBOX.toString()),
                    )
                } else {
                    context.contentResolver.update(messageUri, values, null, null)
                }
            }
        }
    }
}
