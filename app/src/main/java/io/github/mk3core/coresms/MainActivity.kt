package io.github.mk3core.coresms

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.github.mk3core.coresms.compose.ComposeActivity
import io.github.mk3core.coresms.mms.SavedMmsNotices
import io.github.mk3core.coresms.ui.SetupScreen
import io.github.mk3core.coresms.ui.SetupState
import io.github.mk3core.coresms.ui.theme.CoreSmsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var isDefault by mutableStateOf(false)
    private var roleBlocked by mutableStateOf(false)
    private var missingPermissions by mutableStateOf(emptyList<String>())
    private var savedMmsNotices by mutableIntStateOf(0)
    private var recentTexts by mutableStateOf(emptyList<RecentText>())

    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refresh()
            // Declined, or blocked by Android's restricted settings for
            // sideloaded apps. Either way, show how to get past the block.
            roleBlocked = !isDefault
        }

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CoreSmsTheme {
                SetupScreen(
                    state = SetupState(
                        isDefault = isDefault,
                        roleBlocked = roleBlocked,
                        missingPermissions = missingPermissions.isNotEmpty(),
                        savedMmsNotices = savedMmsNotices,
                        recentTexts = recentTexts,
                    ),
                    onMakeDefault = ::requestDefault,
                    onOpenAppInfo = ::openAppInfo,
                    onGrantPermissions = { permissionRequest.launch(missingPermissions.toTypedArray()) },
                    onNewMessage = { startActivity(Intent(this, ComposeActivity::class.java)) },
                    onChangeDefault = { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        isDefault = DefaultSmsApp.isDefault(this)
        if (isDefault) roleBlocked = false
        missingPermissions = DefaultSmsApp.missingPermissions(this)
        savedMmsNotices = SavedMmsNotices.count(this)
        if (isDefault) {
            lifecycleScope.launch {
                recentTexts = withContext(Dispatchers.IO) { RecentTexts.load(this@MainActivity) }
            }
        } else {
            recentTexts = emptyList()
        }
    }

    private fun requestDefault() {
        val intent = DefaultSmsApp.requestIntent(this)
        if (intent == null) {
            roleBlocked = true
        } else {
            roleRequest.launch(intent)
        }
    }

    private fun openAppInfo() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
    }
}
