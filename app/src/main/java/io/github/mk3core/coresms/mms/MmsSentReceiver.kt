package io.github.mk3core.coresms.mms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony.Mms
import android.telephony.SmsManager
import io.github.mk3core.coresms.data.Prefs
import io.github.mk3core.coresms.mmslib.pdu.PduHeaders
import io.github.mk3core.coresms.mmslib.pdu.PduParser
import io.github.mk3core.coresms.mmslib.pdu.SendConf
import io.github.mk3core.coresms.notify.Notifications
import java.io.File

/** Moves an outgoing MMS to sent or failed once the carrier answers. */
class MmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data ?: return
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return
        intent.getStringExtra(EXTRA_FILE)?.let { File(it).delete() }

        val response = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA)
        val conf = response?.let { runCatching { PduParser(it, false).parse() as? SendConf }.getOrNull() }
        val accepted = resultCode == Activity.RESULT_OK &&
            (conf == null || conf.responseStatus == PduHeaders.RESPONSE_STATUS_OK)

        val values = ContentValues().apply {
            put(Mms.MESSAGE_BOX, if (accepted) Mms.MESSAGE_BOX_SENT else Mms.MESSAGE_BOX_FAILED)
            conf?.messageId?.let { put(Mms.MESSAGE_ID, MmsStore.iso(it)) }
            conf?.let { put(Mms.RESPONSE_STATUS, it.responseStatus) }
        }
        runCatching { context.contentResolver.update(uri, values, null, null) }

        if (accepted) {
            Prefs.setMmsError(context, id, null)
            MmsLog.record(context, id, "Carrier accepted it")
        } else {
            val result = MmsTransport.Result(
                ok = false,
                resultCode = resultCode,
                httpStatus = intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, 0),
                data = response,
            )
            val error = MmsTransport.describe(context, result)
            Prefs.setMmsError(context, id, error)
            MmsLog.record(
                context,
                id,
                "Send failed: $error (result ${result.resultCode}" +
                    (if (result.httpStatus != 0) ", HTTP ${result.httpStatus}" else "") +
                    (conf?.let { ", carrier status ${it.responseStatus}" } ?: "") + ")",
            )
            Notifications.showSendFailed(context, threadIdOf(context, id))
        }
    }

    private fun threadIdOf(context: Context, id: Long): Long = runCatching {
        context.contentResolver.query(MmsStore.messageUri(id), arrayOf(Mms.THREAD_ID), null, null, null)
            ?.use { if (it.moveToFirst()) it.getLong(0) else -1L }
    }.getOrNull() ?: -1L

    companion object {
        const val EXTRA_FILE = "file"
        const val EXTRA_SUB_ID = "sub_id"
    }
}
