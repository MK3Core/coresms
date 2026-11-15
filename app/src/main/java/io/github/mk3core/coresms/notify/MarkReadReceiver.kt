package io.github.mk3core.coresms.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.mk3core.coresms.data.Conversations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MarkReadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(Notifications.EXTRA_THREAD_ID, -1)
        if (threadId < 0) return
        Notifications.cancel(context, threadId)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Conversations.markRead(context, threadId)
            } finally {
                pending.finish()
            }
        }
    }
}
