package io.github.mk3core.coresms.mms

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Raw MMS notices (M-Notification.ind PDUs) received before CoreSMS could
 * download MMS, kept in app storage as `<received millis>-sub<id>-<random>.pdu`.
 */
object SavedMmsNotices {

    private fun dir(context: Context) = File(context.filesDir, "mms-notices").apply { mkdirs() }

    fun save(context: Context, pdu: ByteArray, subId: Int) {
        val name = "${System.currentTimeMillis()}-sub$subId-${UUID.randomUUID().toString().take(8)}"
        val dir = dir(context)
        // Write then rename, so a crash never leaves a half-written notice.
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(pdu)
        tmp.renameTo(File(dir, "$name.pdu"))
    }

    fun count(context: Context): Int =
        dir(context).listFiles { file -> file.name.endsWith(".pdu") }?.size ?: 0
}
