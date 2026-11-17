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
import io.github.mk3core.coresms.data.MessageHit
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import io.github.mk3core.coresms.data.Words
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import kotlinx.coroutines.delay
import io.github.mk3core.coresms.ui.theme.LocalChatColors
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.launch

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
    /** Moves a conversation to bots (or back to humans), remembered for its sender. */
    val onMove: (Conversation, toUpdates: Boolean) -> Unit,
    val onFixPermissions: () -> Unit,
    val onAddNumber: () -> Unit,
    /** Messages containing the query's words, best first (see SearchIndex). */
    val onSearchMessages: suspend (String) -> List<MessageHit>,
    val onOpenMessage: (MessageHit) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    sections: ListSections,
    showPermissionBanner: Boolean,
    showNumberBanner: Boolean,
    /** Search looks inside every message, not just names and latest messages. */
    messageSearch: Boolean,
    actions: ListActions,
) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    fun closeSearch() {
        keyboard?.hide()
        searching = false
        query = ""
    }
    // Back (button or gesture) leaves search for the conversation list, not the app.
    BackHandler(enabled = searching) { closeSearch() }
    var confirmDelete by remember { mutableStateOf<Conversation?>(null) }
    var showUpdates by rememberSaveable { mutableStateOf(false) }
    // Back from bots returns to humans before leaving the app.
    BackHandler(enabled = showUpdates && !searching) { showUpdates = false }
    // The tab a conversation was just moved to, which pulses to show where it went.
    var arrival by remember { mutableStateOf<Arrival?>(null) }
    fun move(conversation: Conversation, toUpdates: Boolean) {
        actions.onMove(conversation, toUpdates)
        arrival = Arrival(toUpdates, (arrival?.id ?: 0) + 1)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    navigationIcon = {
                        if (searching) {
                            IconButton(onClick = ::closeSearch) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                            }
                        }
                    },
                    title = {
                        if (searching) {
                            SearchField(query, messageSearch, onChange = { query = it })
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
                if (!searching) {
                    val updatesWaiting = remember(sections) { sections.all().count { it.isUpdate && it.needsAttention } }
                    InboxTabs(showUpdates, updatesWaiting, arrival, onSelect = { showUpdates = it })
                }
            }
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
        val menu = RowMenu(actions, onAskDelete = { confirmDelete = it }, onMove = ::move)
        if (searching) {
            SearchResults(sections, query, messageSearch, actions, menu, Modifier.padding(padding))
        } else {
            PlainList(sections, showUpdates, showPermissionBanner, showNumberBanner, actions, menu, Modifier.padding(padding))
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

private fun ListSections.all(): List<Conversation> = pinned + attention + earlier

/** A conversation just moved into humans (false) or bots (true); [id] tells repeat moves apart. */
private data class Arrival(val toUpdates: Boolean, val id: Int)

/** humans | bots, under the title. bots shows how many are waiting. */
@Composable
private fun InboxTabs(showUpdates: Boolean, updatesWaiting: Int, arrival: Arrival?, onSelect: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        InboxTab(
            stringResource(R.string.tab_conversations),
            selected = !showUpdates,
            arrival = arrival?.takeIf { !it.toUpdates },
            onClick = { onSelect(false) },
        )
        InboxTab(
            stringResource(R.string.tab_updates) + if (updatesWaiting > 0) " $updatesWaiting" else "",
            selected = showUpdates,
            arrival = arrival?.takeIf { it.toUpdates },
            onClick = { onSelect(true) },
        )
    }
}

/** One tab. When a conversation lands in it, its name swells briefly and a "+1" floats up and fades. */
@Composable
private fun InboxTab(label: String, selected: Boolean, arrival: Arrival?, onClick: () -> Unit) {
    val pulse = remember { Animatable(1f) }
    val plusOne = remember { Animatable(0f) }
    LaunchedEffect(arrival?.id) {
        if (arrival == null) return@LaunchedEffect
        launch {
            pulse.animateTo(1.25f, tween(140))
            pulse.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        plusOne.snapTo(1f)
        plusOne.animateTo(0f, tween(1100, delayMillis = 300))
    }
    val highlighted = selected || plusOne.value > 0f
    val color = if (highlighted) MaterialTheme.colorScheme.onBackground else LocalChatColors.current.subtle
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = color,
                modifier = Modifier.graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                },
            )
            if (plusOne.value > 0f) {
                Text(
                    "+1",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        // Takes up no width, so the tabs never shift while it shows;
                        // it's drawn just past the label's end instead.
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                            layout(0, placeable.height) { placeable.place(4.dp.roundToPx(), 0) }
                        }
                        .graphicsLayer {
                            alpha = plusOne.value
                            translationY = -(1f - plusOne.value) * 18.dp.toPx()
                        },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .width(28.dp)
                .height(2.dp)
                .background(if (selected) color else Color.Transparent, RoundedCornerShape(1.dp)),
        )
    }
}

/**
 * Pinned conversations across the top, then every conversation newest first.
 * Unread ones stand out by weight and their count, not by position.
 * [showUpdates] picks the bots tab instead of humans.
 */
@Composable
private fun PlainList(
    sections: ListSections,
    showUpdates: Boolean,
    showPermissionBanner: Boolean,
    showNumberBanner: Boolean,
    actions: ListActions,
    menu: RowMenu,
    modifier: Modifier,
) {
    val all = remember(sections, showUpdates) {
        (sections.attention + sections.earlier).filter { it.isUpdate == showUpdates }.sortedByDescending { it.date }
    }
    val pinned = remember(sections, showUpdates) { sections.pinned.filter { it.isUpdate == showUpdates } }
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
                if (pinned.isNotEmpty()) PinnedRow(pinned, menu)
                if (showUpdates && all.isEmpty() && pinned.isEmpty()) {
                    Text(
                        stringResource(R.string.updates_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalChatColors.current.subtle,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                    )
                }
            }
        }
        items(all, key = { it.threadId }) {
            SwipeToMove(it, menu, Modifier.animateItem()) { ConversationRow(it, emphasized = it.needsAttention, menu = menu) }
        }
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

private class RowMenu(
    val actions: ListActions,
    val onAskDelete: (Conversation) -> Unit,
    val onMove: (Conversation, toUpdates: Boolean) -> Unit,
)

/**
 * Swipe a row toward the tab it should go to: bots is on the right, so a
 * conversation swipes right into it, and an update swipes left back out.
 */
@Composable
private fun SwipeToMove(conversation: Conversation, menu: RowMenu, modifier: Modifier, content: @Composable () -> Unit) {
    val toUpdates = !conversation.isUpdate
    val threshold = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    // A fresh state per tab, and deliberately not saved: the list saves state by
    // conversation, so a saved one would come back "swiped off" when the
    // conversation shows up in the other tab, where that direction is
    // disabled, and the swipe layout crashes placing it (v0.7.1).
    val state = remember(conversation.threadId, toUpdates) {
        SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { threshold })
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = toUpdates,
        enableDismissFromEndToStart = !toUpdates,
        // The row has slid off screen; the list drops it right away and closes the gap.
        onDismiss = { menu.onMove(conversation, toUpdates) },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(LocalChatColors.current.subtle.copy(alpha = 0.18f)).padding(horizontal = 24.dp),
                // The label sits in the space the row uncovers as it slides away.
                contentAlignment = if (toUpdates) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Text(
                    stringResource(if (toUpdates) R.string.swipe_to_updates else R.string.swipe_to_conversations),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
    }
}

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
        ChatMenu(open, onDismiss = { open = false }) {
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

/** humans, then bots, whose name or latest message matches, then matching messages from any conversation. */
@Composable
private fun SearchResults(
    sections: ListSections,
    query: String,
    messageSearch: Boolean,
    actions: ListActions,
    menu: RowMenu,
    modifier: Modifier,
) {
    val q = query.trim()
    val all = remember(sections) { sections.all() }
    val byThread = remember(all) { all.associateBy { it.threadId } }
    val conversations = if (q.isEmpty()) emptyList() else all.filter { it.matches(q) }
    var hits by remember { mutableStateOf(emptyList<MessageHit>()) }
    var searchedFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(q, messageSearch) {
        if (q.isEmpty() || !messageSearch) {
            hits = emptyList()
            searchedFor = q
            return@LaunchedEffect
        }
        delay(SEARCH_PAUSE_MS) // wait for typing to pause
        hits = actions.onSearchMessages(q)
        searchedFor = q
    }
    // Messages from conversations that have since been deleted are left out.
    val shown = hits.filter { it.threadId in byThread }
    // Scrolling through results puts the keyboard away.
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) keyboard?.hide()
    }
    LazyColumn(modifier.fillMaxSize(), state = listState) {
        // Grouped by where each conversation lives now, so a moved one shows under its new tab.
        val humans = conversations.filter { !it.isUpdate }
        val bots = conversations.filter { it.isUpdate }
        if (humans.isNotEmpty()) {
            item(key = "h-humans") { SectionHeader(stringResource(R.string.search_section_conversations)) }
            items(humans, key = { it.threadId }) { ConversationRow(it, emphasized = it.needsAttention, menu = menu) }
        }
        if (bots.isNotEmpty()) {
            item(key = "h-bots") { SectionHeader(stringResource(R.string.search_section_bots)) }
            items(bots, key = { it.threadId }) { ConversationRow(it, emphasized = it.needsAttention, menu = menu) }
        }
        if (shown.isNotEmpty()) {
            item(key = "h-msg") { SectionHeader(stringResource(R.string.search_section_messages)) }
            items(shown, key = { it.messageKey }) { hit ->
                MessageHitRow(hit, byThread.getValue(hit.threadId), q, onClick = { actions.onOpenMessage(hit) })
            }
        }
        if (q.isNotEmpty() && searchedFor == q && conversations.isEmpty() && shown.isEmpty()) {
            item(key = "none") {
                Text(
                    stringResource(R.string.search_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalChatColors.current.subtle,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                )
            }
        }
    }
}

private const val SEARCH_PAUSE_MS = 200L

/** How far a row must slide before letting go moves it (Material's default). */
private val SWIPE_THRESHOLD = 56.dp

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalChatColors.current.subtle,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** One message found by search: who it's with, when, and the words that matched in bold. */
@Composable
private fun MessageHitRow(hit: MessageHit, conversation: Conversation, query: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val chat = LocalChatColors.current
    val strong = MaterialTheme.colorScheme.onSurface
    val text = remember(hit.body, query) {
        val (snippet, ranges) = Words.snippet(hit.body, Words.matches(hit.body, query))
        buildAnnotatedString {
            append(snippet)
            ranges.forEach { range ->
                if (range.last < snippet.length) addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = strong), range.first, range.last + 1)
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConversationAvatar(conversation.people, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                if (conversation.isUpdate) {
                    Text(stringResource(R.string.search_bot_tag), style = MaterialTheme.typography.labelMedium, color = chat.subtle)
                    Text(" · ", style = MaterialTheme.typography.labelMedium, color = chat.subtle)
                }
                Text(Times.short(context, hit.date), style = MaterialTheme.typography.labelMedium, color = chat.subtle)
            }
            Spacer(Modifier.height(2.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = chat.subtle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SearchField(query: String, messageSearch: Boolean, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Ready to type as soon as search opens.
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(stringResource(if (messageSearch) R.string.search_hint_messages else R.string.search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // The keyboard's search key tucks the keyboard away to show the results.
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
}

private fun Conversation.matches(query: String): Boolean {
    val q = query.trim()
    return title.contains(q, ignoreCase = true) ||
        snippet.contains(q, ignoreCase = true) ||
        people.any { it.address.filter(Char::isDigit).contains(q.filter(Char::isDigit).ifEmpty { "\u0000" }) }
}
