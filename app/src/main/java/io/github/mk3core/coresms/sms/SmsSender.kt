package io.github.mk3core.coresms.sms

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log

/** Sends a text to one recipient and records it in the system SMS database. */
object SmsSender {

    private const val TAG = "SmsSender"

    fun send(
        context: Context,
        address: String,
        body: String,
        subId: Int = SubscriptionManager.getDefaultSmsSubscriptionId(),
    ) {
        val messageUri = insertOutbox(context, address, body, subId)

        try {
            val manager = smsManager(context, subId)
            val parts = manager.divideMessage(body)
            if (messageUri == null) {
                manager.sendMultipartTextMessage(address, null, parts, null, null)
                return
            }
            // One result per part. SmsStatusReceiver marks the message failed
            // if any part fails.
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            parts.indices.forEach { part ->
                sentIntents += SmsStatusReceiver.sentIntent(context, messageUri, address, part)
            }
            manager.sendMultipartTextMessage(address, null, parts, sentIntents, null)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Couldn't send SMS", e)
            if (messageUri != null) SmsStatusReceiver.markResult(context, messageUri, sent = false, errorCode = 0)
            throw e
        }
    }

    /** Saves the message as queued (outbox) and returns its content://sms/ID uri. */
    private fun insertOutbox(context: Context, address: String, body: String, subId: Int): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }
        return runCatching {
            val inserted = context.contentResolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
            inserted?.let { ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, ContentUris.parseId(it)) }
        }.onFailure { Log.e(TAG, "Couldn't store outgoing SMS", it) }.getOrNull()
    }

    private fun smsManager(context: Context, subId: Int): SmsManager {
        val valid = SubscriptionManager.isValidSubscriptionId(subId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(SmsManager::class.java)
            if (valid) manager.createForSubscriptionId(subId) else manager
        } else {
            @Suppress("DEPRECATION")
            if (valid) SmsManager.getSmsManagerForSubscriptionId(subId) else SmsManager.getDefault()
        }
    }
}
