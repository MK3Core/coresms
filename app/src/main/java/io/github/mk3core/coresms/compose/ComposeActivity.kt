package io.github.mk3core.coresms.compose

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import io.github.mk3core.coresms.DefaultSmsApp
import io.github.mk3core.coresms.MainActivity
import io.github.mk3core.coresms.R
import io.github.mk3core.coresms.ThreadActivity
import io.github.mk3core.coresms.data.Contact
import io.github.mk3core.coresms.data.ContactsRepo
import io.github.mk3core.coresms.data.Conversation
import io.github.mk3core.coresms.data.Conversations
import io.github.mk3core.coresms.data.Phone
import io.github.mk3core.coresms.sms.SmsUri
import io.github.mk3core.coresms.ui.Avatar
import io.github.mk3core.coresms.ui.ConversationAvatar
import io.github.mk3core.coresms.ui.theme.CoreSmsTheme
import io.github.mk3core.coresms.ui.theme.LocalChatColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Starting a conversation: from the New message button, "send a text"
 * links in other apps (sms:/smsto:), or photos shared from the gallery.
 * Picks one or more people and opens the conversation with them.
 */
class ComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (!DefaultSmsApp.isDefault(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val link = intent.data?.schemeSpecificPart
        val linkRecipients = SmsUri.recipients(link)
        val draft = intent.getStringExtra("sms_body")
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: SmsUri.body(link)
        val shared = sharedUris()

        if (linkRecipients.isNotEmpty()) {
            openThread(linkRecipients, draft, shared)
            return
        }

        setContent {
            CoreSmsTheme {
                RecipientPicker(
                    onBack = ::finish,
                    onOpenConversation = { conversation ->
                        startActivity(ThreadActivity.intent(this, conversation.threadId, draft, shared))
                        finish()
                    },
                    onStart = { recipients -> openThread(recipients, draft, shared) },
                )
            }
        }
    }

    private fun openThread(recipients: List<String>, draft: String?, attachments: List<Uri>) {
        val threadId = Telephony.Threads.getOrCreateThreadId(this, recipients.toSet())
        startActivity(ThreadActivity.intent(this, threadId, draft, attachments))
        finish()
    }

    private fun sharedUris(): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecipientPicker(
    onBack: () -> Unit,
    onOpenConversation: (Conversation) -> Unit,
    onStart: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    val chat = LocalChatColors.current
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(listOf<Contact>()) }
    var results by remember { mutableStateOf(listOf<Contact>()) }
    var recent by remember { mutableStateOf(listOf<Conversation>()) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        recent = withContext(Dispatchers.IO) { Conversations.load(context).take(30) }
    }
    LaunchedEffect(query) {
        delay(150)
        results = withContext(Dispatchers.IO) { ContactsRepo.search(context, query) }
    }

    fun add(contact: Contact) {
        if (chosen.none { Phone.same(it.address, contact.address) }) chosen = chosen + contact
        query = ""
    }
    fun addTyped() {
        if (Phone.isValidRecipient(query)) add(ContactsRepo.lookup(context, Phone.clean(query)))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.new_message)) },
                actions = {
                    if (chosen.isNotEmpty()) {
                        Button(onClick = { onStart(chosen.map { it.address }) }, modifier = Modifier.padding(end = 8.dp)) {
                            Text(stringResource(if (chosen.size > 1) R.string.compose_start_group else R.string.compose_start))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding()) {
            item {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.compose_to),
                        color = chat.subtle,
                        modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp),
                    )
                    chosen.forEach { contact ->
                        Row(
                            Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                .clickable { chosen = chosen - contact }
                                .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(contact.displayName, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.Close, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        }
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addTyped() }),
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(vertical = 6.dp)
                            .width(160.dp)
                            .focusRequester(focus),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (query.isNotBlank() && Phone.isValidRecipient(query)) {
                item {
                    PersonRow(Contact(Phone.clean(query), null, null), stringResource(R.string.compose_use_number, query.trim())) { addTyped() }
                }
            }
            if (query.isNotBlank()) {
                items(results, key = { it.name + it.address }) { contact ->
                    PersonRow(contact, contact.address) { add(contact) }
                }
            } else if (chosen.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.compose_recent),
                        style = MaterialTheme.typography.labelLarge,
                        color = chat.subtle,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(recent, key = { it.threadId }) { conversation ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenConversation(conversation) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ConversationAvatar(conversation.people, 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(conversation.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonRow(contact: Contact, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(contact, 44.dp)
        Spacer(Modifier.width(14.dp))
        androidx.compose.foundation.layout.Column {
            Text(contact.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != contact.displayName) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = LocalChatColors.current.subtle)
            }
        }
    }
}
