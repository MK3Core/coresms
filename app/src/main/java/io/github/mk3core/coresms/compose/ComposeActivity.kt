package io.github.mk3core.coresms.compose

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mk3core.coresms.DefaultSmsApp
import io.github.mk3core.coresms.MainActivity
import io.github.mk3core.coresms.R
import io.github.mk3core.coresms.sms.SmsSender
import io.github.mk3core.coresms.sms.SmsUri
import io.github.mk3core.coresms.ui.theme.CoreSmsTheme

/**
 * Opened by "send a text" links from other apps (and the New message button).
 * A bare-bones composer for testing sending; the real one comes with the
 * conversation screens.
 */
class ComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val link = intent.data?.schemeSpecificPart
        val initialTo = SmsUri.recipients(link).joinToString(", ")
        val initialBody = intent.getStringExtra("sms_body")
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: SmsUri.body(link)
            ?: ""

        setContent {
            CoreSmsTheme {
                ComposeScreen(
                    initialTo = initialTo,
                    initialBody = initialBody,
                    isDefault = DefaultSmsApp.isDefault(this),
                    onSend = ::send,
                    onOpenCoreSms = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                    onClose = ::finish,
                )
            }
        }
    }

    /** Sends the message and closes, or returns why it can't be sent. */
    private fun send(to: String, body: String): String? {
        val recipients = SmsUri.recipients(to)
        return when {
            recipients.isEmpty() -> getString(R.string.compose_error_no_recipient)
            // Group texts must go as one group MMS, never as separate texts.
            recipients.size > 1 -> getString(R.string.compose_error_group)
            body.isBlank() -> getString(R.string.compose_error_empty)
            else -> runCatching { SmsSender.send(this, recipients.single(), body) }.fold(
                onSuccess = {
                    Toast.makeText(this, R.string.compose_sending, Toast.LENGTH_SHORT).show()
                    finish()
                    null
                },
                onFailure = { getString(R.string.compose_error_send) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeScreen(
    initialTo: String,
    initialBody: String,
    isDefault: Boolean,
    onSend: (to: String, body: String) -> String?,
    onOpenCoreSms: () -> Unit,
    onClose: () -> Unit,
) {
    var to by remember { mutableStateOf(initialTo) }
    var body by remember { mutableStateOf(initialBody) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_message)) },
                actions = { TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isDefault) {
                Text(
                    stringResource(R.string.compose_not_default),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onOpenCoreSms) { Text(stringResource(R.string.setup_default_button)) }
                return@Column
            }
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text(stringResource(R.string.compose_to)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.compose_message)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = { error = onSend(to, body) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.send))
            }
        }
    }
}
