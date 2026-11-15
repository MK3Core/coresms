package io.github.mk3core.coresms.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import io.github.mk3core.coresms.data.Conversations
import io.github.mk3core.coresms.send.MessageSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Sends a reply typed into a notification, to everyone in the conversation. */
class ReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(Notifications.EXTRA_THREAD_ID, -1)
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifications.KEY_REPLY)?.toString()
        if (threadId < 0 || text.isNullOrBlank()) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val recipients = Conversations.recipients(context, threadId).map { it.address }
                if (recipients.isNotEmpty()) MessageSender.send(context, recipients, text)
                Conversations.markRead(context, threadId)
                Notifications.cancel(context, threadId)
            } catch (e: Exception) {
                Log.e("ReplyReceiver", "Reply failed", e)
                Notifications.showSendFailed(context, threadId)
            } finally {
                pending.finish()
            }
        }
    }
}
