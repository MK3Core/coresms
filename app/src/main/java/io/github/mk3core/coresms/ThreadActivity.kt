package io.github.mk3core.coresms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import io.github.mk3core.coresms.data.Contact
import io.github.mk3core.coresms.data.Conversations
import io.github.mk3core.coresms.data.Message
import io.github.mk3core.coresms.data.MessageChanges
import io.github.mk3core.coresms.data.MessageStatus
import io.github.mk3core.coresms.data.Messages
import io.github.mk3core.coresms.data.Prefs
import io.github.mk3core.coresms.mms.MediaTooLargeException
import io.github.mk3core.coresms.mms.MmsDownloadWorker
import io.github.mk3core.coresms.notify.Notifications
import io.github.mk3core.coresms.reactions.ReactionFolding
import io.github.mk3core.coresms.reactions.Reactions
import io.github.mk3core.coresms.send.MessageSender
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
    private var draft by mutableStateOf("")
    private var attachments by mutableStateOf(emptyList<Uri>())
    private var sending by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)

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
                        draft = draft,
                        attachments = attachments,
                        sending = sending,
                        error = error,
                    ),
                    actions = ThreadActions(
                        onBack = ::finish,
                        onDraftChange = { draft = it },
                        onAttach = {
                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
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
                    ),
                )
            }
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
        val (loadedPeople, loadedMessages) = withContext(Dispatchers.IO) {
            Conversations.recipients(context, threadId) to ReactionFolding.fold(Messages.load(context, threadId))
        }
        people = loadedPeople
        messages = loadedMessages
        loaded = true
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && loadedMessages.any { it.incoming }) {
            withContext(Dispatchers.IO) { Conversations.markRead(context, threadId) }
        }
    }

    private fun recipients() = people.map { it.address }

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
            Prefs.setMmsError(this, message.id, null)
            MmsDownloadWorker.enqueue(applicationContext, message.id)
            return
        }
        if (message.status != MessageStatus.Failed) return
        val to = recipients()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { MessageSender.retry(applicationContext, message, to) }
                .onFailure { Log.e(TAG, "Retry failed", it) }
        }
    }

    private fun react(message: Message, emoji: String) {
        val alreadyMine = message.reactions.any { it.fromMe && it.emoji == emoji }
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
