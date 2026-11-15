package io.github.mk3core.coresms

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mk3core.coresms.data.Prefs
import io.github.mk3core.coresms.data.SelfNumbers
import io.github.mk3core.coresms.ui.theme.CoreSmsTheme
import io.github.mk3core.coresms.ui.theme.LocalChatColors

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { CoreSmsTheme { SettingsScreen(onBack = ::finish) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val fromSim = remember { SelfNumbers.fromSims(context) }
    var number by remember { mutableStateOf(Prefs.myNumber(context).orEmpty()) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = { Text(stringResource(R.string.settings)) },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_number_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (fromSim.isEmpty()) {
                        stringResource(R.string.settings_number_unknown)
                    } else {
                        stringResource(R.string.settings_number_from_sim, fromSim.joinToString(", "))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalChatColors.current.subtle,
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it; saved = false },
                    label = { Text(stringResource(R.string.settings_number_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { Prefs.setMyNumber(context, number); saved = true }) {
                    Text(stringResource(if (saved) R.string.saved else R.string.save))
                }
            }
            SettingRow(stringResource(R.string.change_default), stringResource(R.string.settings_default_body)) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            }
            SettingRow(
                stringResource(R.string.settings_notifications),
                stringResource(R.string.settings_notifications_body),
            ) {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
            Text(
                stringResource(R.string.settings_version, BuildConfigCompat.versionName(context)),
                style = MaterialTheme.typography.labelMedium,
                color = LocalChatColors.current.subtle,
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, body: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = LocalChatColors.current.subtle)
    }
}

private object BuildConfigCompat {
    fun versionName(context: android.content.Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
}
