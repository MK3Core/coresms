package io.github.mk3core.coresms.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mk3core.coresms.R
import io.github.mk3core.coresms.RecentText

data class SetupState(
    val isDefault: Boolean,
    val roleBlocked: Boolean,
    val missingPermissions: Boolean,
    val savedMmsNotices: Int,
    val recentTexts: List<RecentText>,
)

/**
 * The first screen for this early build: become the default SMS app, then
 * check that texts come in and go out. Replaced by the conversation list in
 * the next milestone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    state: SetupState,
    onMakeDefault: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onGrantPermissions: () -> Unit,
    onNewMessage: () -> Unit,
    onChangeDefault: () -> Unit,
) {
    Scaffold(
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        floatingActionButton = {
            if (state.isDefault) {
                ExtendedFloatingActionButton(onClick = onNewMessage) {
                    Text(stringResource(R.string.new_message))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.isDefault) {
                item {
                    InfoCard(
                        title = stringResource(R.string.setup_default_title),
                        body = stringResource(R.string.setup_default_body),
                    ) {
                        Button(onClick = onMakeDefault) { Text(stringResource(R.string.setup_default_button)) }
                    }
                }
            }
            if (state.roleBlocked) {
                item {
                    InfoCard(
                        title = stringResource(R.string.setup_blocked_title),
                        body = stringResource(R.string.setup_blocked_body),
                    ) {
                        OutlinedButton(onClick = onOpenAppInfo) { Text(stringResource(R.string.setup_blocked_button)) }
                    }
                }
            }
            if (state.isDefault && state.missingPermissions) {
                item {
                    InfoCard(
                        title = stringResource(R.string.setup_permissions_title),
                        body = stringResource(R.string.setup_permissions_body),
                    ) {
                        Button(onClick = onGrantPermissions) { Text(stringResource(R.string.setup_permissions_button)) }
                    }
                }
            }
            item {
                InfoCard(
                    title = stringResource(R.string.early_build_title),
                    body = stringResource(R.string.early_build_body),
                ) {
                    if (state.savedMmsNotices > 0) {
                        Text(
                            pluralStringResource(
                                R.plurals.saved_mms_notices,
                                state.savedMmsNotices,
                                state.savedMmsNotices,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (state.isDefault) {
                item {
                    Text(
                        stringResource(R.string.recent_texts),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (state.recentTexts.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.recent_texts_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.recentTexts, key = { it.id }) { text ->
                    RecentTextRow(text)
                    HorizontalDivider()
                }
            }
            item {
                TextButton(onClick = onChangeDefault) { Text(stringResource(R.string.change_default)) }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, actions: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            actions()
        }
    }
}

@Composable
private fun RecentTextRow(text: RecentText) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = (if (text.incoming) "← " else "→ ") + text.address,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                DateUtils.getRelativeTimeSpanString(text.date).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text.body,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (text.failed) {
            Text(
                stringResource(R.string.recent_text_failed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
