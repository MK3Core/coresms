package io.github.mk3core.coresms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import io.github.mk3core.coresms.sms.SmsManagers
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mk3core.coresms.data.Attachment
import io.github.mk3core.coresms.data.Carrier
import io.github.mk3core.coresms.data.DownloadState
import io.github.mk3core.coresms.data.Contact
import io.github.mk3core.coresms.data.Conversations
import io.github.mk3core.coresms.data.Message
import io.github.mk3core.coresms.data.MessageChanges
import io.github.mk3core.coresms.data.MessageStatus
import io.github.mk3core.coresms.data.Messages
import io.github.mk3core.coresms.mms.MediaTooLargeException
import io.github.mk3core.coresms.mms.MmsDownloadWorker
import io.github.mk3core.coresms.mms.MmsLog
import io.github.mk3core.coresms.notify.Notifications
import io.github.mk3core.coresms.reactions.ReactionFolding
import io.github.mk3core.coresms.reactions.Reactions
import io.github.mk3core.coresms.send.MessageSender
import io.github.mk3core.coresms.ui.MessageDetails
import io.github.mk3core.coresms.ui.ThreadActions
import io.github.mk3core.coresms.ui.ThreadScreen
import io.github.mk3core.coresms.ui.ThreadState
import io.github.mk3core.coresms.ui.theme.CoreSmsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One conversation: the back-and-forth, with a composer. */
class ThreadActivity : ComponentActivity() {

    private var threadId = -1L
    private var people by mutableStateOf(emptyList<Contact>())
    private var messages by mutableStateOf(emptyList<Message>())
    private var loaded by mutableStateOf(false)
    private var hasOlder by mutableStateOf(false)
    private var limit = PAGE_SIZE
    private var loadingOlder = false
    private var draft by mutableStateOf("")
    private var attachments by mutableStateOf(emptyList<Uri>())
    private var sending by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var details by mutableStateOf<MessageDetails?>(null)
    private var resendFor by mutableStateOf<Message?>(null)

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) attachments = (attachments + uris).distinct().take(10)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1)
        if (threadId < 0) {
            finish()
            return
        }
        draft = savedInstanceState?.getString(STATE_DRAFT) ?: intent.getStringExtra(EXTRA_DRAFT).orEmpty()
        attachments = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_ATTACHMENTS, Uri::class.java).orEmpty()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MessageChanges.observe(this@ThreadActivity).collect { reload() }
            }
        }

        setContent {
            CoreSmsTheme {
                ThreadScreen(
                    state = ThreadState(
                        people = people,
                        messages = messages,
                        loaded = loaded,
                        hasOlder = hasOlder,
                        draft = draft,
                        attachments = attachments,
                        sending = sending,
                        error = error,
                        details = details,
                        resendFor = resendFor,
                    ),
                    actions = ThreadActions(
                        onBack = ::finish,
                        onDraftChange = { draft = it },
                        onAttach = {
                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                        onPickGif = ::attachGif,
                        onInsertFace = { face ->
                            draft = if (draft.isBlank()) face else draft.trimEnd() + " " + face
                        },
                        onRemoveAttachment = { uri -> attachments = attachments - uri },
                        onSend = ::send,
                        onDismissError = { error = null },
                        onOpenAttachment = ::openAttachment,
                        onRetry = ::retry,
                        onReact = ::react,
                        onCopy = ::copy,
                        onDelete = { message ->
                            lifecycleScope.launch(Dispatchers.IO) { Messages.delete(applicationContext, message) }
                        },
                        onCall = { address -> startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", address, null))) },
                        onLoadOlder = ::loadOlder,
                        onShowDetails = ::showDetails,
                        onDismissDetails = { details = null },
                        onAskResend = { resendFor = it },
                        onConfirmResend = ::askToResend,
                        onCancelResend = { resendFor = null },
                    ),
                )
            }
        }
    }

    /** Downloads the largest size of [gif] that fits the carrier's MMS limit and attaches it. */
    private suspend fun attachGif(gif: io.github.mk3core.coresms.gif.Gif): String? {
        val limits = io.github.mk3core.coresms.mms.CarrierLimits.forSubscription(this, SmsManagers.defaultSubscription())
        // Leave room for the message's other attachments and text, like MediaPrep does.
        val budget = (limits.maxMessageBytes - 16 * 1024) / (attachments.size + 1)
        val rendition = io.github.mk3core.coresms.gif.Giphy.bestFit(gif, budget)
            ?: return getString(R.string.gif_too_big, budget / 1024)
        return try {
            val uri = io.github.mk3core.coresms.gif.Giphy.download(applicationContext, gif, rendition)
            attachments = (attachments + uri).distinct().take(10)
            null
        } catch (e: Exception) {
            Log.w(TAG, "GIF download failed", e)
            getString(R.string.gif_load_failed)
        }
    }

    override fun onResume() {
        super.onResume()
        Notifications.visibleThreadId = threadId
        Notifications.cancel(this, threadId)
        lifecycleScope.launch(Dispatchers.IO) { Conversations.markRead(applicationContext, threadId) }
    }

    override fun onPause() {
        super.onPause()
        if (Notifications.visibleThreadId == threadId) Notifications.visibleThreadId = -1
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_DRAFT, draft)
    }

    private suspend fun reload() {
        val context = applicationContext
        val pageSize = limit
        val (loadedPeople, page) = withContext(Dispatchers.IO) {
            Conversations.recipients(context, threadId) to Messages.loadPage(context, threadId, pageSize)
        }
        val loadedMessages = withContext(Dispatchers.Default) { ReactionFolding.fold(page.messages) }
        people = loadedPeople
        messages = loadedMessages
        hasOlder = page.hasOlder
        loaded = true
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && loadedMessages.any { it.incoming }) {
            withContext(Dispatchers.IO) { Conversations.markRead(context, threadId) }
        }
    }

    private fun recipients() = people.map { it.address }

    /** Scrolled near the oldest loaded message: load another page of history. */
    private fun loadOlder() {
        if (loadingOlder || !hasOlder) return
        loadingOlder = true
        limit += PAGE_SIZE
        lifecycleScope.launch {
            try {
                reload()
            } finally {
                loadingOlder = false
            }
        }
    }

    private fun send() {
        val text = draft.trim()
        val media = attachments
        if ((text.isEmpty() && media.isEmpty()) || sending) return
        val to = recipients()
        if (to.isEmpty()) {
            error = getString(R.string.thread_error_no_recipients)
            return
        }
        sending = true
        lifecycleScope.launch {
            val failure = withContext(Dispatchers.IO) {
                runCatching { MessageSender.send(applicationContext, to, text, media) }.exceptionOrNull()
            }
            sending = false
            when (failure) {
                null -> {
                    draft = ""
                    attachments = emptyList()
                }
                is MediaTooLargeException -> error = getString(
                    R.string.thread_error_too_large,
                    failure.fileName,
                    failure.limitBytes / 1024,
                )
                else -> {
                    Log.e(TAG, "Send failed", failure)
                    error = getString(R.string.compose_error_send)
                }
            }
        }
    }

    private fun retry(message: Message) {
        if (message.pending != null) {
            MmsDownloadWorker.enqueue(applicationContext, message.id, MmsDownloadWorker.Mode.MANUAL)
            MessageChanges.notifyLocalChange()
            return
        }
        if (message.status != MessageStatus.Failed) return
        val to = recipients()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { MessageSender.retry(applicationContext, message, to) }
                .onFailure { Log.e(TAG, "Retry failed", it) }
        }
    }

    /** Texts the sender (only them, even in a group) asking for a lost picture message again. */
    private fun askToResend(message: Message) {
        resendFor = null
        val sender = message.sender ?: return
        val text = getString(R.string.resend_request_text)
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { MessageSender.send(applicationContext, listOf(sender), text) }
                .onFailure { Log.e(TAG, "Resend request failed", it) }
        }
        Toast.makeText(this, getString(R.string.resend_sent), Toast.LENGTH_SHORT).show()
    }

    private fun showDetails(message: Message) {
        lifecycleScope.launch {
            details = withContext(Dispatchers.IO) { buildDetails(message) }
        }
    }

    private fun buildDetails(message: Message): MessageDetails {
        val format = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.MEDIUM)
        val facts = mutableListOf<Pair<String, String>>()
        facts += getString(R.string.details_type) to getString(R.string.details_type_mms)
        facts += getString(if (message.incoming) R.string.details_received else R.string.details_sent) to format.format(java.util.Date(message.date))
        val pending = message.pending
        if (pending != null) {
            facts += getString(R.string.details_status) to when (pending.state) {
                DownloadState.Waiting -> getString(R.string.details_status_waiting)
                DownloadState.Downloading -> getString(R.string.mms_downloading)
                DownloadState.Failed -> getString(R.string.details_status_failed)
                DownloadState.NotDownloaded -> getString(R.string.details_status_not_downloaded)
                DownloadState.Expired -> getString(R.string.details_status_expired)
                DownloadState.Gone -> getString(R.string.mms_gone)
            }
            if (pending.sizeBytes > 0) facts += getString(R.string.details_size) to android.text.format.Formatter.formatShortFileSize(this, pending.sizeBytes)
            pending.expiresAtMillis?.let { facts += getString(R.string.details_expires) to format.format(java.util.Date(it)) }
            pending.error?.let { facts += getString(R.string.details_last_error) to it }
        } else {
            facts += getString(R.string.details_status) to when (message.status) {
                MessageStatus.Received -> getString(R.string.details_status_received)
                MessageStatus.Sending -> getString(R.string.thread_sending)
                MessageStatus.Sent -> getString(R.string.thread_sent)
                MessageStatus.Failed -> getString(R.string.details_status_send_failed)
            }
            message.error?.let { facts += getString(R.string.details_last_error) to it }
            if (message.attachments.isNotEmpty()) {
                facts += getString(R.string.details_attachments) to message.attachments.joinToString(", ") { it.mimeType }
            }
        }
        Carrier.name(this, message.subId)?.let { facts += getString(R.string.details_carrier) to it }
        val timeFormat = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.MEDIUM)
        val history = MmsLog.events(this, message.id).map { timeFormat.format(java.util.Date(it.time)) to it.text }
        return MessageDetails(facts, history)
    }

    private fun react(message: Message, emoji: String) {
        val alreadyMine = message.reactions.any { it.fromMe && Reactions.canonical(it.emoji) == Reactions.canonical(emoji) }
        val text = Reactions.compose(emoji, message.body, message.attachments.isNotEmpty(), removed = alreadyMine)
        val to = recipients()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { MessageSender.send(applicationContext, to, text) }
                .onFailure { Log.e(TAG, "Reaction failed", it) }
        }
    }

    private fun copy(message: Message) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), message.body))
    }

    private fun openAttachment(attachment: Attachment) {
        if (attachment.isImage || attachment.isVideo || attachment.isAudio) {
            startActivity(ViewerActivity.intent(this, attachment))
        } else {
            lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) { AttachmentFiles.openExternally(this@ThreadActivity, attachment) }
                if (!ok) Toast.makeText(this@ThreadActivity, R.string.attachment_no_app, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val TAG = "ThreadActivity"
        private const val EXTRA_THREAD_ID = "thread_id"
        private const val EXTRA_DRAFT = "draft"
        private const val EXTRA_ATTACHMENTS = "attachments"
        private const val STATE_DRAFT = "draft"
        private const val PAGE_SIZE = 300

        fun intent(context: Context, threadId: Long, draft: String? = null, attachments: List<Uri> = emptyList()): Intent =
            Intent(context, ThreadActivity::class.java)
                .putExtra(EXTRA_THREAD_ID, threadId)
                .apply {
                    draft?.let { putExtra(EXTRA_DRAFT, it) }
                    if (attachments.isNotEmpty()) {
                        putParcelableArrayListExtra(EXTRA_ATTACHMENTS, ArrayList(attachments))
                        // Pass on read access to files shared from other apps.
                        clipData = ClipData.newRawUri(null, attachments.first()).apply {
                            attachments.drop(1).forEach { addItem(ClipData.Item(it)) }
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
    }
}
