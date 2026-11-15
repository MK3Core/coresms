package io.github.mk3core.coresms.ui

import android.text.format.Formatter
import android.util.Patterns
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.mk3core.coresms.R
import io.github.mk3core.coresms.data.Attachment
import io.github.mk3core.coresms.data.Carrier
import io.github.mk3core.coresms.data.Contact
import io.github.mk3core.coresms.data.DownloadState
import io.github.mk3core.coresms.data.Message
import io.github.mk3core.coresms.data.MessageStatus
import io.github.mk3core.coresms.data.Phone
import io.github.mk3core.coresms.reactions.Reaction
import io.github.mk3core.coresms.reactions.Reactions
import io.github.mk3core.coresms.ui.theme.BubbleText
import io.github.mk3core.coresms.ui.theme.LocalChatColors

data class ThreadState(
    val people: List<Contact>,
    val messages: List<Message>,
    val loaded: Boolean,
    val hasOlder: Boolean,
    val draft: String,
    val attachments: List<android.net.Uri>,
    val sending: Boolean,
    val error: String?,
    val details: MessageDetails? = null,
    val resendFor: Message? = null,
) {
    val isGroup get() = people.size > 1
    val title: String
        get() = when {
            people.isEmpty() -> ""
            people.size == 1 -> people[0].displayName
            else -> people.joinToString(", ") { it.name?.substringBefore(' ') ?: it.address }
        }
}

class ThreadActions(
    val onBack: () -> Unit,
    val onDraftChange: (String) -> Unit,
    val onAttach: () -> Unit,
    val onRemoveAttachment: (android.net.Uri) -> Unit,
    val onSend: () -> Unit,
    val onDismissError: () -> Unit,
    val onOpenAttachment: (Attachment) -> Unit,
    val onRetry: (Message) -> Unit,
    val onReact: (Message, String) -> Unit,
    val onCopy: (Message) -> Unit,
    val onDelete: (Message) -> Unit,
    val onCall: (String) -> Unit,
    val onLoadOlder: () -> Unit,
    val onShowDetails: (Message) -> Unit,
    val onDismissDetails: () -> Unit,
    val onAskResend: (Message) -> Unit,
    val onConfirmResend: (Message) -> Unit,
    val onCancelResend: () -> Unit,
)

/** What the Details dialog shows for one message. */
data class MessageDetails(
    val facts: List<Pair<String, String>>,
    val history: List<Pair<String, String>>,
)

private const val GROUP_GAP_MS = 3 * 60 * 1000L
private const val DIVIDER_GAP_MS = 60 * 60 * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(state: ThreadState, actions: ThreadActions) {
    var showPeople by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showPeople = true },
                    ) {
                        ConversationAvatar(state.people, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                state.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val subtitle = when {
                                state.isGroup -> pluralStringResource(R.plurals.thread_people, state.people.size, state.people.size)
                                state.people.singleOrNull()?.name != null -> state.people.single().address
                                else -> null
                            }
                            if (subtitle != null) {
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = LocalChatColors.current.subtle,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                },
                actions = {
                    state.people.singleOrNull()?.let { person ->
                        IconButton(onClick = { actions.onCall(person.address) }) {
                            Icon(Icons.Filled.Call, stringResource(R.string.call))
                        }
                    }
                },
            )
        },
        bottomBar = { Composer(state, actions) },
    ) { padding ->
        MessageList(state, actions, Modifier.padding(padding))
    }

    state.details?.let { DetailsDialog(it, actions.onDismissDetails) }

    state.resendFor?.let { message ->
        val name = message.sender?.let { address ->
            state.people.firstOrNull { Phone.same(it.address, address) }?.displayName ?: address
        }.orEmpty()
        AlertDialog(
            onDismissRequest = actions.onCancelResend,
            title = { Text(stringResource(R.string.mms_ask_resend)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.resend_explain, name), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "“" + stringResource(R.string.resend_request_text) + "”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalChatColors.current.subtle,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { actions.onConfirmResend(message) }) { Text(stringResource(R.string.send)) } },
            dismissButton = { TextButton(onClick = actions.onCancelResend) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showPeople) {
        AlertDialog(
            onDismissRequest = { showPeople = false },
            confirmButton = { TextButton(onClick = { showPeople = false }) { Text(stringResource(R.string.close)) } },
            title = { Text(pluralStringResource(R.plurals.thread_people, state.people.size, state.people.size)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.people.forEach { person ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(person, 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(person.displayName, style = MaterialTheme.typography.bodyLarge)
                                if (person.name != null) {
                                    Text(person.address, style = MaterialTheme.typography.labelMedium, color = LocalChatColors.current.subtle)
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

private class Placement(
    val message: Message,
    val divider: Boolean,
    val firstInGroup: Boolean,
    val lastInGroup: Boolean,
    val showStatus: Boolean,
)

private fun sameAuthor(a: Message, b: Message): Boolean = when {
    !a.incoming && !b.incoming -> true
    a.incoming && b.incoming -> a.sender == b.sender || (a.sender != null && b.sender != null && Phone.same(a.sender, b.sender))
    else -> false
}

private fun place(messages: List<Message>): List<Placement> {
    val lastOutgoing = messages.indexOfLast { !it.incoming }
    return messages.mapIndexed { i, message ->
        val prev = messages.getOrNull(i - 1)
        val next = messages.getOrNull(i + 1)
        val divider = prev == null || message.date - prev.date > DIVIDER_GAP_MS
        val joinsPrev = !divider && prev != null && sameAuthor(prev, message) && message.date - prev.date < GROUP_GAP_MS
        val nextDivider = next == null || next.date - message.date > DIVIDER_GAP_MS
        val joinsNext = !nextDivider && next != null && sameAuthor(message, next) && next.date - message.date < GROUP_GAP_MS
        Placement(
            message = message,
            divider = divider,
            firstInGroup = !joinsPrev,
            lastInGroup = !joinsNext,
            showStatus = message.status == MessageStatus.Failed || message.status == MessageStatus.Sending ||
                (i == lastOutgoing && message.status == MessageStatus.Sent),
        )
    }
}

@Composable
private fun MessageList(state: ThreadState, actions: ThreadActions, modifier: Modifier) {
    val placements = remember(state.messages) { place(state.messages).asReversed() }
    val peopleByAddress = remember(state.people) { state.people }
    val listState = rememberLazyListState()
    val nearTop by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 20
        }
    }
    LaunchedEffect(nearTop, state.hasOlder, placements.size) {
        if (nearTop && state.hasOlder) actions.onLoadOlder()
    }
    // Snap to the newest message whenever one is sent or arrives (or a picture
    // message finishes downloading). Loading older history doesn't change the
    // newest message, so it never yanks you down while scrolling back.
    val newestKey = placements.firstOrNull()?.message?.key
    LaunchedEffect(newestKey) {
        if (newestKey != null) listState.scrollToItem(0)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        items(placements, key = { it.message.key }) { placement ->
            Column {
                if (placement.divider) TimeDivider(placement.message.date)
                MessageRow(placement, state.isGroup, peopleByAddress, actions)
            }
        }
    }
}

@Composable
private fun TimeDivider(date: Long) {
    val context = LocalContext.current
    Text(
        Times.divider(context, date),
        style = MaterialTheme.typography.labelSmall,
        color = LocalChatColors.current.subtle,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(placement: Placement, isGroup: Boolean, people: List<Contact>, actions: ThreadActions) {
    val message = placement.message
    val context = LocalContext.current
    val chat = LocalChatColors.current
    val outgoing = !message.incoming
    var menuOpen by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val sender = message.sender?.let { address -> people.firstOrNull { Phone.same(it.address, address) } ?: Contact(address, null, null) }

    Column(
        Modifier.fillMaxWidth().padding(top = if (placement.firstInGroup) 8.dp else 2.dp),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        if (isGroup && !outgoing && placement.firstInGroup && sender != null) {
            Text(
                sender.name ?: sender.address,
                style = MaterialTheme.typography.labelSmall,
                color = chat.subtle,
                modifier = Modifier.padding(start = 48.dp, bottom = 2.dp),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            if (isGroup && !outgoing) {
                if (placement.lastInGroup && sender != null) Avatar(sender, 30.dp) else Spacer(Modifier.width(30.dp))
                Spacer(Modifier.width(8.dp))
            }
            Box(
                Modifier.padding(top = if (message.reactions.isNotEmpty()) 14.dp else 0.dp),
            ) {
                Box(
                    Modifier.combinedClickable(
                        onClick = {
                            when {
                                message.pending != null && message.pending.canRetry -> actions.onRetry(message)
                                message.status == MessageStatus.Failed -> actions.onRetry(message)
                                else -> showTime = !showTime
                            }
                        },
                        onLongClick = { menuOpen = true },
                    ),
                ) {
                    BubbleContent(message, placement, actions)
                }
                if (message.reactions.isNotEmpty()) {
                    ReactionChips(
                        message.reactions,
                        Modifier
                            .align(if (outgoing) Alignment.TopStart else Alignment.TopEnd)
                            .offset(x = if (outgoing) (-8).dp else 8.dp, y = (-14).dp),
                    )
                }
                MessageMenu(message, menuOpen, onDismiss = { menuOpen = false }, actions)
            }
        }
        if (placement.showStatus || showTime) {
            val text = when {
                message.status == MessageStatus.Failed ->
                    stringResource(R.string.thread_failed) + (message.error?.let { " · $it" } ?: "")
                message.status == MessageStatus.Sending -> stringResource(R.string.thread_sending)
                showTime -> Times.time(context, message.date)
                else -> stringResource(R.string.thread_sent)
            }
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = if (message.status == MessageStatus.Failed) chat.failed else chat.subtle,
                modifier = Modifier.padding(horizontal = if (isGroup && !outgoing) 52.dp else 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun BubbleContent(message: Message, placement: Placement, actions: ThreadActions) {
    val chat = LocalChatColors.current
    val outgoing = !message.incoming
    val big = 20.dp
    val small = 6.dp
    val shape = if (outgoing) {
        RoundedCornerShape(
            topStart = big,
            topEnd = if (placement.firstInGroup) big else small,
            bottomEnd = if (placement.lastInGroup) big else small,
            bottomStart = big,
        )
    } else {
        RoundedCornerShape(
            topStart = if (placement.firstInGroup) big else small,
            topEnd = big,
            bottomEnd = big,
            bottomStart = if (placement.lastInGroup) big else small,
        )
    }
    val background = when {
        message.status == MessageStatus.Failed -> chat.failed.copy(alpha = 0.85f)
        outgoing -> chat.outgoingBubble
        else -> chat.incomingBubble
    }
    val textColor = if (outgoing) chat.onOutgoingBubble else chat.onIncomingBubble

    Column(
        Modifier.widthIn(max = 290.dp),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        message.pending?.let { PendingBubble(message, shape, actions) }
        val visual = message.attachments.filter { it.isImage || it.isVideo }
        if (visual.size > 1) {
            MediaGrid(visual, onOpen = actions.onOpenAttachment)
        } else {
            visual.forEach { AttachmentView(it, outgoing, onOpen = { actions.onOpenAttachment(it) }) }
        }
        message.attachments.filterNot { it.isImage || it.isVideo }.forEach { attachment ->
            AttachmentView(attachment, outgoing, onOpen = { actions.onOpenAttachment(attachment) })
        }
        val body = message.body
        if (body.isNotEmpty()) {
            if (isBigEmoji(body)) {
                Text(body, fontSize = 44.sp, modifier = Modifier.padding(horizontal = 4.dp))
            } else {
                Box(Modifier.clip(shape).background(background).padding(horizontal = 14.dp, vertical = 9.dp)) {
                    Text(linkify(body, textColor), style = BubbleText, color = textColor)
                }
            }
        }
    }
}

@Composable
private fun PendingBubble(message: Message, shape: RoundedCornerShape, actions: ThreadActions) {
    val pending = message.pending ?: return
    val context = LocalContext.current
    val chat = LocalChatColors.current
    Row(
        Modifier.clip(shape).background(chat.incomingBubble).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (pending.state) {
            DownloadState.Waiting, DownloadState.Downloading ->
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = chat.subtle)
            DownloadState.Failed, DownloadState.NotDownloaded ->
                Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.primary)
            DownloadState.Expired -> Icon(Icons.Filled.Refresh, null, tint = chat.subtle)
            DownloadState.Gone -> Icon(Icons.Filled.Close, null, tint = chat.subtle)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                when (pending.state) {
                    DownloadState.Waiting, DownloadState.Downloading -> stringResource(R.string.mms_downloading)
                    DownloadState.Failed -> stringResource(R.string.mms_failed_tap)
                    DownloadState.NotDownloaded -> stringResource(R.string.mms_not_downloaded)
                    DownloadState.Expired -> stringResource(R.string.mms_expired)
                    DownloadState.Gone -> Carrier.name(context, message.subId)
                        ?.let { stringResource(R.string.mms_gone_named, it) }
                        ?: stringResource(R.string.mms_gone)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = chat.onIncomingBubble,
            )
            val expires = pending.expiresAtMillis
                ?.takeIf { pending.state != DownloadState.Expired && pending.state != DownloadState.Gone }
                ?.let { stringResource(R.string.mms_expires, Times.short(context, it)) }
            val goneHint = stringResource(R.string.mms_gone_hint)
            val details = buildList {
                if (pending.state == DownloadState.Gone) add(goneHint)
                else if (pending.state != DownloadState.Downloading) pending.error?.let(::add)
                if (pending.sizeBytes > 0) add(Formatter.formatShortFileSize(context, pending.sizeBytes))
                expires?.let(::add)
            }
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = chat.subtle,
                )
            }
            if (pending.state == DownloadState.Gone && message.sender != null) {
                TextButton(
                    onClick = { actions.onAskResend(message) },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                ) { Text(stringResource(R.string.mms_ask_resend)) }
            }
        }
    }
}

@Composable
private fun AttachmentView(attachment: Attachment, outgoing: Boolean, onOpen: () -> Unit) {
    val chat = LocalChatColors.current
    when {
        attachment.isImage || attachment.isVideo -> Box(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = attachment.uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(min = 120.dp, max = 260.dp)
                    .heightIn(min = 80.dp, max = 340.dp)
                    .background(chat.incomingBubble),
            )
            if (attachment.isVideo) {
                Box(
                    Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, stringResource(R.string.preview_video), tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
        else -> Row(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (outgoing) chat.outgoingBubble else chat.incomingBubble)
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (outgoing) chat.onOutgoingBubble else chat.onIncomingBubble
            Icon(
                when {
                    attachment.isAudio -> Icons.Filled.PlayArrow
                    attachment.isContactCard -> Icons.Filled.Person
                    else -> Icons.Filled.Add
                },
                null,
                tint = tint,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    attachment.isAudio -> stringResource(R.string.preview_audio)
                    attachment.isContactCard -> stringResource(R.string.preview_contact)
                    else -> attachment.name ?: stringResource(R.string.preview_attachment)
                },
                color = tint,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Two or more photos/videos in one message, as a tidy grid of square tiles. */
@Composable
private fun MediaGrid(items: List<Attachment>, onOpen: (Attachment) -> Unit) {
    val chat = LocalChatColors.current
    Column(
        Modifier.width(250.dp).clip(RoundedCornerShape(18.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { attachment ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(chat.incomingBubble)
                            .clickable { onOpen(attachment) },
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = attachment.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (attachment.isVideo) {
                            Icon(Icons.Filled.PlayArrow, stringResource(R.string.preview_video), tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                }
                // Keep a lone last tile half-width.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReactionChips(reactions: List<Reaction>, modifier: Modifier) {
    // Group by the emoji without its variation selector, show the first form seen.
    val counts = reactions.groupBy { Reactions.canonical(it.emoji) }
        .map { (_, same) -> same.first().emoji to same.size }
    Row(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp))
            .border(2.dp, MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        counts.forEach { (emoji, count) ->
            Text(emoji, fontSize = 15.sp)
            if (count > 1) Text(count.toString(), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MessageMenu(message: Message, open: Boolean, onDismiss: () -> Unit, actions: ThreadActions) {
    val context = LocalContext.current
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        if (message.pending == null && message.status != MessageStatus.Failed) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                (Reactions.QUICK + listOf("🔥", "🙏")).forEach { emoji ->
                    val mine = message.reactions.any { it.fromMe && Reactions.canonical(it.emoji) == Reactions.canonical(emoji) }
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable { onDismiss(); actions.onReact(message, emoji) },
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 22.sp) }
                }
            }
            HorizontalDivider()
        }
        if (message.body.isNotEmpty()) {
            DropdownMenuItem(text = { Text(stringResource(R.string.copy)) }, onClick = { onDismiss(); actions.onCopy(message) })
        }
        message.attachments.firstOrNull()?.let { attachment ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.save)) },
                onClick = {
                    onDismiss()
                    val saved = message.attachments.map { io.github.mk3core.coresms.AttachmentFiles.saveToDevice(context, it) }.all { it }
                    android.widget.Toast.makeText(
                        context,
                        if (saved) R.string.saved else R.string.save_failed,
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share)) },
                onClick = { onDismiss(); io.github.mk3core.coresms.AttachmentFiles.share(context, attachment) },
            )
        }
        if (message.status == MessageStatus.Failed || message.pending?.canRetry == true) {
            DropdownMenuItem(text = { Text(stringResource(R.string.retry)) }, onClick = { onDismiss(); actions.onRetry(message) })
        }
        if (message.isMms) {
            DropdownMenuItem(text = { Text(stringResource(R.string.details)) }, onClick = { onDismiss(); actions.onShowDetails(message) })
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
            onClick = { onDismiss(); actions.onDelete(message) },
        )
    }
}

@Composable
private fun DetailsDialog(details: MessageDetails, onDismiss: () -> Unit) {
    val chat = LocalChatColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.details)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(details.facts) { (label, value) ->
                    Column {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = chat.subtle)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (details.history.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.details_history),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    items(details.history) { (time, text) ->
                        Row {
                            Text(time, style = MaterialTheme.typography.labelSmall, color = chat.subtle, modifier = Modifier.width(84.dp))
                            Text(text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun Composer(state: ThreadState, actions: ThreadActions) {
    val chat = LocalChatColors.current
    val isMms = state.isGroup || state.attachments.isNotEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        state.error?.let { error ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(error, color = chat.failed, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = actions.onDismissError) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
            }
        }
        if (state.attachments.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 56.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.attachments) { uri ->
                    Box {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(chat.incomingBubble),
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(22.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .clickable { actions.onRemoveAttachment(uri) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Close, stringResource(R.string.remove), tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconButton(onClick = actions.onAttach) {
                Icon(Icons.Filled.Add, stringResource(R.string.attach), tint = MaterialTheme.colorScheme.primary)
            }
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.draft.isEmpty()) {
                    Text(
                        stringResource(if (isMms) R.string.composer_hint_mms else R.string.composer_hint_sms),
                        style = MaterialTheme.typography.bodyLarge,
                        color = chat.subtle,
                    )
                }
                BasicTextField(
                    value = state.draft,
                    onValueChange = actions.onDraftChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            val canSend = (state.draft.isNotBlank() || state.attachments.isNotEmpty()) && !state.sending
            Box(
                Modifier
                    .padding(bottom = 2.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (canSend) MaterialTheme.colorScheme.primary else chat.incomingBubble)
                    .clickable(enabled = canSend, onClick = actions.onSend),
                contentAlignment = Alignment.Center,
            ) {
                if (state.sending) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        stringResource(R.string.send),
                        tint = if (canSend) Color.White else chat.subtle,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** Makes links tappable. */
private fun linkify(text: String, color: Color): AnnotatedString {
    val matcher = Patterns.WEB_URL.matcher(text)
    if (!matcher.find()) return AnnotatedString(text)
    matcher.reset()
    return buildAnnotatedString {
        var last = 0
        while (matcher.find()) {
            append(text.substring(last, matcher.start()))
            val raw = matcher.group()
            val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
            withLink(
                LinkAnnotation.Url(
                    url,
                    TextLinkStyles(SpanStyle(color = color, textDecoration = TextDecoration.Underline)),
                ),
            ) { append(raw) }
            last = matcher.end()
        }
        append(text.substring(last))
    }
}

/** One to three emoji and nothing else are shown large, without a bubble. */
private fun isBigEmoji(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > 24) return false
    if (trimmed.any { it.isLetterOrDigit() || it.isWhitespace() }) return false
    val iterator = java.text.BreakIterator.getCharacterInstance()
    iterator.setText(trimmed)
    var graphemes = 0
    while (iterator.next() != java.text.BreakIterator.DONE) graphemes++
    return graphemes in 1..3 && trimmed.codePoints().allMatch { it >= 0x2000 || it == 0xFE0F || it == 0x200D }
}
