package io.github.mk3core.coresms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mk3core.coresms.R

/** Shown until CoreSMS is the default SMS app. */
@Composable
fun SetupScreen(
    roleBlocked: Boolean,
    onMakeDefault: () -> Unit,
    onOpenAppInfo: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            InfoCard(
                title = stringResource(R.string.setup_default_title),
                body = stringResource(R.string.setup_default_body),
            ) {
                Button(onClick = onMakeDefault) { Text(stringResource(R.string.setup_default_button)) }
            }
            if (roleBlocked) {
                InfoCard(
                    title = stringResource(R.string.setup_blocked_title),
                    body = stringResource(R.string.setup_blocked_body),
                ) {
                    OutlinedButton(onClick = onOpenAppInfo) { Text(stringResource(R.string.setup_blocked_button)) }
                }
            }
        }
    }
}

@Composable
fun InfoCard(title: String, body: String, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            actions()
        }
    }
}
