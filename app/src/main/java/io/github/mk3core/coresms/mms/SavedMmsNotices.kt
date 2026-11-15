package io.github.mk3core.coresms.mms

import android.content.Context
import android.telephony.SubscriptionManager
import java.io.File

/**
 * Raw MMS notices (M-Notification.ind PDUs) saved by v0.1.0, which couldn't
 * download MMS yet, as `files/mms-notices/<received millis>-sub<id>-<random>.pdu`.
 * CoreSMS now processes them once at startup and deletes them.
 */
object SavedMmsNotices {

    private fun dir(context: Context) = File(context.filesDir, "mms-notices")

    fun drain(context: Context, handle: (pdu: ByteArray, subId: Int) -> Unit) {
        val files = dir(context).listFiles { file -> file.name.endsWith(".pdu") } ?: return
        for (file in files.sortedBy { it.name }) {
            val subId = SUB_ID.find(file.name)?.groupValues?.get(1)?.toIntOrNull()
                ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
            runCatching { handle(file.readBytes(), subId) }
            file.delete()
        }
        dir(context).listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.delete() }
    }

    private val SUB_ID = Regex("-sub(-?\\d+)-")
}
