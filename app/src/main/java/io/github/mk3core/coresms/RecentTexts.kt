package io.github.mk3core.coresms

import android.content.Context
import android.provider.Telephony

/** A message as shown in the setup screen's "Recent texts" check. */
data class RecentText(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val incoming: Boolean,
    val failed: Boolean,
)

object RecentTexts {

    /** The newest SMS from the system database, so you can see receiving and sending work. */
    fun load(context: Context, limit: Int = 20): List<RecentText> {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
        )
        val rows = mutableListOf<RecentText>()
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                while (rows.size < limit && cursor.moveToNext()) {
                    val type = cursor.getInt(4)
                    rows += RecentText(
                        id = cursor.getLong(0),
                        address = cursor.getString(1).orEmpty(),
                        body = cursor.getString(2).orEmpty(),
                        date = cursor.getLong(3),
                        incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                        failed = type == Telephony.Sms.MESSAGE_TYPE_FAILED,
                    )
                }
            }
        }
        return rows
    }
}
