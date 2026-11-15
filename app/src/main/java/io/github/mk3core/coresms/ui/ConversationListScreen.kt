package io.github.mk3core.coresms.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import io.github.mk3core.coresms.ui.theme.LocalChatStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mk3core.coresms.R
import io.github.mk3core.coresms.data.Conversation
import io.github.mk3core.coresms.ui.theme.LocalChatColors

/** What the list needs to know to place each conversation. */
data class ListSections(
    val pinned: List<Conversation>,
    /** Unread, waiting MMS, and recently active: the first screen. */
    val attention: List<Conversation>,
    /** Everything already dealt with, "below the screen". */
    val earlier: List<Conversation>,
)

class ListActions(
    val onOpen: (Conversation) -> Unit,
    val onNew: () -> Unit,
    val onSettings: () -> Unit,
    val onTogglePin: (Conversation) -> Unit,
    val onToggleRead: (Conversation) -> Unit,
    val onDelete: (Conversation) -> Unit,
    val onFixPermissions: () -> Unit,
    val onAddNumber: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    sections: ListSections,
    showPermissionBanner: Boolean,
    showNumberBanner: Boolean,
    actions: ListActions,
) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<Conversation?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (searching) {
                        IconButton(onClick = { searching = false; query = "" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
                title = {
                    if (searching) {
                        SearchField(query, onChange = { query = it })
                    } else {
                        ListTitle()
                    }
                },
                actions = {
                    if (!searching) {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, stringResource(R.string.search)) }
                        IconButton(onClick = actions.onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!searching) {
                val lift = Modifier
                if (LocalChatStyle.current.retro) {
                    NewMessageText(actions.onNew, lift)
                } else {
                    FloatingActionButton(
                        onClick = actions.onNew,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = lift,
                    ) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.new_message))
                    }
                }
            }
        },
    ) { padding ->
        val menu = RowMenu(actions, onAskDelete = { confirmDelete = it })
        if (searching) {
            val all = sections.pinned + sections.attention + sections.earlier
            val results = if (query.isBlank()) emptyList() else all.filter { it.matches(query) }
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(results, key = { it.threadId }) { ConversationRow(it, emphasized = it.needsAttention, menu = menu) }
            }
        } else {
            PlainList(sections, showPermissionBanner, showNumberBanner, actions, menu, Modifier.padding(padding))
        }
    }

    confirmDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_conversation_title)) },
            text = { Text(stringResource(R.string.delete_conversation_body, conversation.title)) },
            confirmButton = {
                TextButton(onClick = { actions.onDelete(conversation); confirmDelete = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * Pinned conversations across the top, then every conversation newest first.
 * Unread ones stand out by weight and their count, not by position.
 */
@Composable
private fun PlainList(
    sections: ListSections,
    showPermissionBanner: Boolean,
    showNumberBanner: Boolean,
    actions: ListActions,
    menu: RowMenu,
    modifier: Modifier,
) {
    val all = remember(sections) { (sections.attention + sections.earlier).sortedByDescending { it.date } }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item(key = "top") {
            Column(Modifier.fillMaxWidth()) {
                if (showPermissionBanner) {
                    InfoCard(
                        title = stringResource(R.string.banner_permissions_title),
                        body = stringResource(R.string.banner_permissions_body),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) { TextButton(onClick = actions.onFixPermissions) { Text(stringResource(R.string.allow)) } }
                }
                if (showNumberBanner) {
                    InfoCard(
                        title = stringResource(R.string.banner_number_title),
                        body = stringResource(R.string.banner_number_body),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) { TextButton(onClick = actions.onAddNumber) { Text(stringResource(R.string.banner_number_button)) } }
                }
                if (sections.pinned.isNotEmpty()) PinnedRow(sections.pinned, menu)
            }
        }
        items(all, key = { it.threadId }) { ConversationRow(it, emphasized = it.needsAttention, menu = menu) }
    }
}

/** Retro's new-message button: plain text in a box, white on black like everything else. */
@Composable
private fun NewMessageText(onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.onBackground, shape)
            .clickable(onClickLabel = stringResource(R.string.new_message), onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            stringResource(R.string.new_message_short),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun ListTitle() {
    Text(stringResource(R.string.list_title), style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun PinnedRow(pinned: List<Conversation>, menu: RowMenu) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(pinned, key = { it.threadId }) { conversation ->
            WithMenu(conversation, menu) { modifier ->
                Column(
                    modifier.widthIn(max = 84.dp).padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        ConversationAvatar(conversation.people, 64.dp)
                        if (conversation.needsAttention) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(16.dp)
                                    .background(MaterialTheme.colorScheme.background, LocalChatStyle.current.roundShape)
                                    .padding(2.dp)
                                    .background(LocalChatColors.current.unreadDot, LocalChatStyle.current.roundShape),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        conversation.people.singleOrNull()?.name?.substringBefore(' ') ?: conversation.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (conversation.needsAttention) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, emphasized: Boolean, menu: RowMenu) {
    val context = LocalContext.current
    val chat = LocalChatColors.current
    val unread = conversation.needsAttention
    WithMenu(conversation, menu) { modifier ->
        Row(
            modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConversationAvatar(conversation.people, if (emphasized) 54.dp else 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        conversation.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (unread) FontWeight.Bold else if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Times.short(context, conversation.date),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (unread) chat.unreadDot else chat.subtle,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        if (conversation.hasPendingMms && conversation.snippet.isBlank()) {
                            stringResource(R.string.preview_mms_waiting)
                        } else {
                            conversation.snippet
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (unread) MaterialTheme.colorScheme.onSurface else chat.subtle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (conversation.unreadCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .padding(top = 2.dp)
                                .heightIn(min = 20.dp)
                                .widthIn(min = 20.dp)
                                .background(chat.unreadDot, if (LocalChatStyle.current.retro) RoundedCornerShape(4.dp) else RoundedCornerShape(10.dp))
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                conversation.unreadCount.coerceAtMost(99).toString(),
                                color = if (LocalChatStyle.current.retro) MaterialTheme.colorScheme.background else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else if (conversation.hasPendingMms) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.padding(top = 6.dp).size(10.dp).background(chat.failed, LocalChatStyle.current.roundShape))
                    }
                }
            }
        }
    }
}

private class RowMenu(val actions: ListActions, val onAskDelete: (Conversation) -> Unit)

/** Tap to open, long-press for pin / read / delete. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WithMenu(conversation: Conversation, menu: RowMenu, content: @Composable (Modifier) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        content(
            Modifier.combinedClickable(
                onClick = { menu.actions.onOpen(conversation) },
                onLongClick = { open = true },
            ),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (conversation.pinned) R.string.unpin else R.string.pin)) },
                onClick = { open = false; menu.actions.onTogglePin(conversation) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (conversation.unreadCount > 0) R.string.mark_read else R.string.mark_unread)) },
                onClick = { open = false; menu.actions.onToggleRead(conversation) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                onClick = { open = false; menu.onAskDelete(conversation) },
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun Conversation.matches(query: String): Boolean {
    val q = query.trim()
    return title.contains(q, ignoreCase = true) ||
        snippet.contains(q, ignoreCase = true) ||
        people.any { it.address.filter(Char::isDigit).contains(q.filter(Char::isDigit).ifEmpty { "\u0000" }) }
}
