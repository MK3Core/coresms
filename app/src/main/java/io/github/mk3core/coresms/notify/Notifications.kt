package io.github.mk3core.coresms.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.mk3core.coresms.MainActivity
import io.github.mk3core.coresms.R
import io.github.mk3core.coresms.ThreadActivity
import io.github.mk3core.coresms.data.Contact
import io.github.mk3core.coresms.data.Conversation
import io.github.mk3core.coresms.data.Conversations
import io.github.mk3core.coresms.data.Message
import io.github.mk3core.coresms.data.Messages
import io.github.mk3core.coresms.reactions.Reactions

/**
 * One notification per conversation, listing its unread messages, with
 * Reply and Mark as read. Each is tied to a conversation shortcut so Android
 * shows it in the Conversations section and allows per-chat settings.
 */
object Notifications {

    private const val CHANNEL_MESSAGES = "messages"
    private const val CHANNEL_ERRORS = "errors"
    private const val OLD_CHANNEL_MMS = "mms" // v0.1.0's "saved for later" channel

    const val EXTRA_THREAD_ID = "thread_id"
    const val KEY_REPLY = "reply"

    /** The conversation on screen right now, which shouldn't notify. */
    @Volatile var visibleThreadId: Long = -1

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_MESSAGES, context.getString(R.string.channel_messages), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_ERRORS, context.getString(R.string.channel_errors), NotificationManager.IMPORTANCE_HIGH),
            ),
        )
        manager.deleteNotificationChannel(OLD_CHANNEL_MMS)
    }

    /** A message arrived in [threadId]: show or update its notification. */
    fun updateForNewMessage(context: Context, threadId: Long) {
        if (threadId == visibleThreadId) return
        update(context, threadId, alert = true)
    }

    /** Rebuilds a conversation's notification from its unread messages, or removes it if there are none. */
    fun update(context: Context, threadId: Long, alert: Boolean) {
        val unread = Messages.unread(context, threadId)
        if (unread.isEmpty()) {
            cancel(context, threadId)
            return
        }
        val conversation = Conversations.find(context, threadId) ?: return
        val shortcutId = pushShortcut(context, conversation)
        val people = conversation.people.associateBy { it.address }

        val me = Person.Builder().setName(context.getString(R.string.notification_me)).build()
        val style = NotificationCompat.MessagingStyle(me)
            .setGroupConversation(conversation.isGroup)
        if (conversation.isGroup) style.conversationTitle = conversation.title
        unread.takeLast(8).forEach { message ->
            val contact = message.sender?.let { address ->
                people[address] ?: io.github.mk3core.coresms.data.ContactsRepo.lookup(context, address)
            }
            style.addMessage(previewText(context, message), message.date, contact?.let { person(context, it) })
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShortcutId(shortcutId)
            .setLocusId(LocusIdCompat(shortcutId))
            .setWhen(unread.last().date)
            .setShowWhen(true)
            .setOnlyAlertOnce(!alert)
            .setAutoCancel(true)
            .setContentIntent(openThread(context, threadId))
            .addAction(replyAction(context, threadId))
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    context.getString(R.string.notification_mark_read),
                    broadcast(context, MarkReadReceiver::class.java, threadId, mutable = false),
                ).setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                    .setShowsUserInterface(false)
                    .build(),
            )
            .build()
        post(context, threadId.toInt(), notification)
    }

    fun cancel(context: Context, threadId: Long) {
        NotificationManagerCompat.from(context).cancel(threadId.toInt())
    }

    fun showMmsDownloadFailed(context: Context, threadId: Long) {
        val name = Conversations.find(context, threadId)?.title ?: context.getString(R.string.unknown_sender)
        val notification = NotificationCompat.Builder(context, CHANNEL_ERRORS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_mms_failed_title))
            .setContentText(context.getString(R.string.notification_mms_failed_text, name))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(openThread(context, threadId))
            .setAutoCancel(true)
            .build()
        post(context, ("mms-failed:$threadId").hashCode(), notification)
    }

    fun showSendFailed(context: Context, threadId: Long) {
        val name = Conversations.find(context, threadId)?.title ?: context.getString(R.string.unknown_sender)
        val notification = NotificationCompat.Builder(context, CHANNEL_ERRORS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_send_failed_title))
            .setContentText(context.getString(R.string.notification_send_failed_text, name))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(if (threadId > 0) openThread(context, threadId) else openApp(context))
            .setAutoCancel(true)
            .build()
        post(context, ("send-failed:$threadId").hashCode(), notification)
    }

    private fun previewText(context: Context, message: Message): CharSequence {
        Reactions.parse(message.body)?.let { return Reactions.describe(it) }
        if (message.pending != null) return context.getString(R.string.preview_mms_waiting)
        val media = message.attachments.firstOrNull()
        val mediaLabel = when {
            media == null -> null
            media.isImage -> context.getString(R.string.preview_photo)
            media.isVideo -> context.getString(R.string.preview_video)
            media.isAudio -> context.getString(R.string.preview_audio)
            media.isContactCard -> context.getString(R.string.preview_contact)
            else -> context.getString(R.string.preview_attachment)
        }
        return listOfNotNull(mediaLabel, message.body.takeIf { it.isNotBlank() }).joinToString(" ")
    }

    private fun pushShortcut(context: Context, conversation: Conversation): String {
        val id = "thread-${conversation.threadId}"
        val icon = conversation.people.singleOrNull()?.let { photo(context, it) }
            ?.let { IconCompat.createWithAdaptiveBitmap(it) }
            ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        val shortcut = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(conversation.title)
            .setLongLived(true)
            .setLocusId(LocusIdCompat(id))
            .setIcon(icon)
            .setPersons(conversation.people.map { person(context, it) }.toTypedArray())
            .setIntent(ThreadActivity.intent(context, conversation.threadId).setAction(Intent.ACTION_VIEW))
            .setCategories(setOf("android.shortcut.conversation"))
            .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
        return id
    }

    private fun person(context: Context, contact: Contact): Person = Person.Builder()
        .setName(contact.displayName)
        .setKey(contact.address)
        .apply { photo(context, contact)?.let { setIcon(IconCompat.createWithBitmap(it)) } }
        .build()

    private fun photo(context: Context, contact: Contact): Bitmap? {
        val uri = contact.photoUri ?: return null
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    private fun replyAction(context: Context, threadId: Long): NotificationCompat.Action {
        val input = RemoteInput.Builder(KEY_REPLY).setLabel(context.getString(R.string.notification_reply)).build()
        return NotificationCompat.Action.Builder(
            0,
            context.getString(R.string.notification_reply),
            broadcast(context, ReplyReceiver::class.java, threadId, mutable = true),
        ).addRemoteInput(input)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .build()
    }

    private fun broadcast(context: Context, receiver: Class<*>, threadId: Long, mutable: Boolean): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            threadId.toInt(),
            Intent(context, receiver).putExtra(EXTRA_THREAD_ID, threadId),
            PendingIntent.FLAG_UPDATE_CURRENT or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openThread(context: Context, threadId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        threadId.toInt(),
        ThreadActivity.intent(context, threadId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // The permission is checked on the line above notify().
    @SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
