package io.github.mk3core.coresms

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.mk3core.coresms.compose.ComposeActivity
import io.github.mk3core.coresms.data.ContactsRepo
import io.github.mk3core.coresms.data.Conversation
import io.github.mk3core.coresms.data.Conversations
import io.github.mk3core.coresms.data.MessageChanges
import io.github.mk3core.coresms.data.Prefs
import io.github.mk3core.coresms.data.SearchIndex
import io.github.mk3core.coresms.data.SelfNumbers
import io.github.mk3core.coresms.data.Updates
import io.github.mk3core.coresms.mms.IncomingMms
import io.github.mk3core.coresms.mms.MmsRecovery
import io.github.mk3core.coresms.notify.Notifications
import io.github.mk3core.coresms.ui.ConversationListScreen
import io.github.mk3core.coresms.ui.ListActions
import io.github.mk3core.coresms.ui.ListSections
import io.github.mk3core.coresms.ui.SetupScreen
import io.github.mk3core.coresms.ui.theme.CoreSmsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var isDefault by mutableStateOf(false)
    private var roleBlocked by mutableStateOf(false)
    private var missingPermissions by mutableStateOf(emptyList<String>())
    private var numberUnknown by mutableStateOf(false)
    private var conversations by mutableStateOf(emptyList<Conversation>())
    private var messageSearch by mutableStateOf(false)
    /** The bots tab is showing (otherwise humans). */
    private var showBots by mutableStateOf(false)
    private var searching by mutableStateOf(false)

    /**
     * Conversations that were on the first screen when you opened one. They
     * stay there until you leave the list, so rows never jump under your
     * finger after reading a message.
     */
    private var sticky = emptySet<Long>()
    private var openingThread = false

    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshSetup()
            // Declined, or blocked by Android's restricted settings for
            // sideloaded apps. Either way, show how to get past the block.
            roleBlocked = !isDefault
            if (isDefault) requestMissingPermissions()
        }

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshSetup()
            // Contact names show up as soon as the permission is granted.
            ContactsRepo.clearCache()
            lifecycleScope.launch { reload() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        showBots = savedInstanceState?.getBoolean(STATE_SHOW_BOTS) ?: false
        searching = savedInstanceState?.getBoolean(STATE_SEARCHING) ?: false
        // A rotation keeps whatever tab you'd moved to since.
        if (savedInstanceState == null) applyListIntent(intent)
        refreshSetup()

        lifecycleScope.launch(Dispatchers.IO) {
            val app = applicationContext
            // MMS notices saved by v0.1.0 (before MMS download existed).
            IncomingMms.importSavedNotices(app)
            // Our number from earlier apps' sent MMS, for SIMs that don't report it.
            SelfNumbers.detectFromHistory(app)
            // Ask the carrier again for any MMS that never downloaded.
            MmsRecovery.heal(app)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MessageChanges.observe(this@MainActivity).collect { reload() }
            }
        }

        setContent {
            CoreSmsTheme {
                if (!isDefault) {
                    SetupScreen(roleBlocked = roleBlocked, onMakeDefault = ::requestDefault, onOpenAppInfo = ::openAppInfo)
                } else {
                    ConversationListScreen(
                        sections = sections(),
                        showPermissionBanner = missingPermissions.isNotEmpty(),
                        showNumberBanner = numberUnknown && missingPermissions.isEmpty(),
                        messageSearch = messageSearch,
                        showBots = showBots,
                        searching = searching,
                        actions = ListActions(
                            onOpen = ::openThread,
                            onShowBots = { showBots = it },
                            onSearching = { open ->
                                searching = open
                                // Up from search is the humans list.
                                if (!open) showBots = false
                            },
                            onNew = { startActivity(Intent(this, ComposeActivity::class.java)) },
                            onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                            onTogglePin = { c ->
                                Prefs.setPinned(this, c.threadId, !c.pinned)
                                MessageChanges.notifyLocalChange()
                            },
                            onToggleRead = { c ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    if (c.unreadCount > 0) {
                                        Conversations.markRead(applicationContext, c.threadId)
                                        Notifications.cancel(applicationContext, c.threadId)
                                    } else {
                                        Conversations.markUnread(applicationContext, c.threadId)
                                    }
                                }
                            },
                            onDelete = { c ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    Conversations.delete(applicationContext, c.threadId)
                                    Notifications.cancel(applicationContext, c.threadId)
                                }
                            },
                            onMove = { c, toUpdates ->
                                // Saved and shown right away, so the row leaves with the swipe
                                // instead of springing back until the list reloads.
                                Updates.move(applicationContext, c, toUpdates)
                                conversations = conversations.map {
                                    if (it.threadId == c.threadId) it.copy(isUpdate = toUpdates) else it
                                }
                                MessageChanges.notifyLocalChange()
                            },
                            onFixPermissions = ::requestMissingPermissions,
                            onAddNumber = { startActivity(Intent(this, SettingsActivity::class.java)) },
                            onSearchMessages = { query ->
                                withContext(Dispatchers.IO) { SearchIndex.search(applicationContext, query) }
                            },
                            onOpenMessage = { hit ->
                                startActivity(ThreadActivity.intent(this, hit.threadId, focus = hit))
                            },
                        ),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyListIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SHOW_BOTS, showBots)
        outState.putBoolean(STATE_SEARCHING, searching)
    }

    /** Arriving "up" from a conversation: show its tab, not search. */
    private fun applyListIntent(intent: Intent?) {
        if (intent?.hasExtra(EXTRA_SHOW_BOTS) != true) return
        showBots = intent.getBooleanExtra(EXTRA_SHOW_BOTS, false)
        searching = false
    }

    override fun onStart() {
        super.onStart()
        openingThread = false
        messageSearch = Prefs.searchMessages(this)
        refreshSetup()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the list (not just opening a conversation) resets it.
        if (!openingThread) sticky = emptySet()
    }

    private fun sections(): ListSections {
        val now = System.currentTimeMillis()
        val pinnedOrder = Prefs.pinnedThreads(this)
        val pinned = conversations.filter { it.pinned }.sortedBy { pinnedOrder.indexOf(it.threadId) }
        val rest = conversations.filter { !it.pinned }
        val attention = rest.filter { it.needsAttention || now - it.date < ACTIVE_WINDOW_MS || it.threadId in sticky }
        return ListSections(pinned, attention, rest - attention.toSet())
    }

    private fun openThread(conversation: Conversation) {
        sticky = sections().attention.map { it.threadId }.toSet()
        openingThread = true
        startActivity(ThreadActivity.intent(this, conversation.threadId))
    }

    private suspend fun reload() {
        if (!DefaultSmsApp.isDefault(this)) return
        conversations = withContext(Dispatchers.IO) { Conversations.load(applicationContext) }
        numberUnknown = withContext(Dispatchers.IO) { SelfNumbers.all(applicationContext).isEmpty() }
    }

    private fun refreshSetup() {
        val wasDefault = isDefault
        isDefault = DefaultSmsApp.isDefault(this)
        if (isDefault) roleBlocked = false
        missingPermissions = DefaultSmsApp.missingPermissions(this)
        if (isDefault && !wasDefault) lifecycleScope.launch { reload() }
    }

    private fun requestDefault() {
        val intent = DefaultSmsApp.requestIntent(this)
        if (intent == null) roleBlocked = true else roleRequest.launch(intent)
    }

    private fun requestMissingPermissions() {
        val missing = DefaultSmsApp.missingPermissions(this)
        if (missing.isNotEmpty()) permissionRequest.launch(missing.toTypedArray())
    }

    private fun openAppInfo() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    companion object {
        private const val EXTRA_SHOW_BOTS = "show_bots"
        private const val STATE_SHOW_BOTS = "show_bots"
        private const val STATE_SEARCHING = "searching"

        /** The conversation list on the humans or bots tab, reusing the open list if there is one. */
        fun listIntent(context: Context, showBots: Boolean): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_SHOW_BOTS, showBots)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** A conversation you're actively texting in stays on the first screen this long after its last message. */
        private const val ACTIVE_WINDOW_MS = 20 * 60 * 1000L
    }
}
