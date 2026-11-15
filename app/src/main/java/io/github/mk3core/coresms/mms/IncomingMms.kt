package io.github.mk3core.coresms.mms

import android.content.Context
import android.util.Log
import io.github.mk3core.coresms.mmslib.pdu.NotificationInd
import io.github.mk3core.coresms.mmslib.pdu.PduParser

/** Handles MMS notifications pushed by the carrier. */
object IncomingMms {

    private const val TAG = "IncomingMms"

    /**
     * Saves the notification as a not-yet-downloaded message straight away,
     * so it's never lost, then queues the download.
     */
    fun handlePush(context: Context, pdu: ByteArray, subId: Int) {
        val parsed = runCatching { PduParser(pdu, true).parse() }
            .onFailure { Log.e(TAG, "Couldn't parse MMS push", it) }
            .getOrNull()
        when (parsed) {
            is NotificationInd -> handleNotification(context, parsed, subId)
            null -> Log.w(TAG, "Unreadable MMS push (${pdu.size} bytes)")
            // Delivery and read reports aren't shown yet.
            else -> Log.i(TAG, "Ignoring MMS push of type ${parsed.messageType}")
        }
    }

    private fun handleNotification(context: Context, ind: NotificationInd, subId: Int) {
        // Carriers resend notifications until they're acknowledged; skip
        // ones we already have (downloaded or still waiting).
        val transactionId = MmsStore.iso(ind.transactionId)
        val existing = transactionId?.let { MmsStore.findByTransactionId(context, it) }
        if (existing != null) {
            Log.i(TAG, "Duplicate MMS notification")
            MmsLog.record(context, existing, "Carrier announced it again (ignored, already have it)")
            return
        }
        val id = MmsStore.persistNotification(context, ind, subId)
        if (id == null) {
            Log.e(TAG, "Couldn't save MMS notification")
            return
        }
        val size = ind.messageSize.takeIf { it > 0 }?.let { " (${(it + 1023) / 1024} KB)" }.orEmpty()
        val expiry = ind.expiry.takeIf { it > 0 }?.let {
            ", expires " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                .format(java.util.Date(it * 1000))
        }.orEmpty()
        MmsLog.record(context, id, "Carrier announced a picture message$size$expiry")
        MmsDownloadWorker.enqueue(context, id)
    }

    /** Processes MMS notices saved by v0.1.0, before MMS download existed. */
    fun importSavedNotices(context: Context) {
        SavedMmsNotices.drain(context) { pdu, subId -> handlePush(context, pdu, subId) }
    }
}
