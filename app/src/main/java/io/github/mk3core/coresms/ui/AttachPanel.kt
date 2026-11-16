package io.github.mk3core.coresms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.mk3core.coresms.R
import io.github.mk3core.coresms.data.Prefs
import io.github.mk3core.coresms.gif.Faces
import io.github.mk3core.coresms.gif.Gif
import io.github.mk3core.coresms.gif.Giphy
import io.github.mk3core.coresms.ui.theme.LocalChatColors
import kotlinx.coroutines.delay

enum class AttachTab { Photos, Gifs, Faces }

/** What the "+" panel can do; the thread screen fills these in. */
class AttachPanelActions(
    val onPickPhotos: () -> Unit,
    /** Downloads the GIF and attaches it; returns an error to show, or null when done. */
    val onPickGif: suspend (Gif) -> String?,
    val onInsertFace: (String) -> Unit,
    val onDismiss: () -> Unit,
)

/** The composer's "+" panel: Photos | GIFs | Faces. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachPanel(actions: AttachPanelActions) {
    var tab by rememberSaveable { mutableStateOf(AttachTab.Gifs) }
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 420.dp).padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TabLabel(stringResource(R.string.attach_photos), tab == AttachTab.Photos) { tab = AttachTab.Photos }
                TabLabel(stringResource(R.string.attach_gifs), tab == AttachTab.Gifs) { tab = AttachTab.Gifs }
                TabLabel(stringResource(R.string.attach_faces), tab == AttachTab.Faces) { tab = AttachTab.Faces }
            }
            when (tab) {
                AttachTab.Photos -> PhotosTab(actions)
                AttachTab.Gifs -> GifsTab(actions)
                AttachTab.Faces -> FacesTab(actions)
            }
        }
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.onSurface)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PhotosTab(actions: AttachPanelActions) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(4.dp))
                .clickable { actions.onDismiss(); actions.onPickPhotos() }
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Text(stringResource(R.string.attach_open_gallery), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.attach_photos_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalChatColors.current.subtle,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GifsTab(actions: AttachPanelActions) {
    val chat = LocalChatColors.current
    if (!Giphy.configured) {
        Text(
            stringResource(R.string.gif_not_configured),
            style = MaterialTheme.typography.bodyMedium,
            color = chat.subtle,
            modifier = Modifier.padding(16.dp),
        )
        return
    }
    var query by rememberSaveable { mutableStateOf("") }
    val gifs = remember { mutableStateListOf<Gif>() }
    var nextOffset by remember { mutableStateOf<Int?>(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<Gif?>(null) }
    val loadFailed = stringResource(R.string.gif_load_failed)
    val grid = rememberLazyGridState()

    suspend fun load(reset: Boolean) {
        val offset = if (reset) 0 else nextOffset ?: return
        loading = true
        error = null
        try {
            val page = if (query.isBlank()) Giphy.trending(offset) else Giphy.search(query, offset)
            if (reset) gifs.clear()
            gifs.addAll(page.gifs.filterNot { new -> gifs.any { it.id == new.id } })
            nextOffset = page.nextOffset
        } catch (e: Exception) {
            error = loadFailed
        } finally {
            loading = false
        }
    }

    // Trending first; a search runs once typing pauses.
    LaunchedEffect(query) {
        if (query.isNotBlank()) delay(400)
        load(reset = true)
        grid.scrollToItem(0)
    }
    val nearEnd by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gifs.size - 6
        }
    }
    LaunchedEffect(nearEnd, gifs.size) {
        if (nearEnd && !loading && nextOffset != null && gifs.isNotEmpty()) load(reset = false)
    }
    picking?.let { gif ->
        LaunchedEffect(gif) {
            val problem = actions.onPickGif(gif)
            picking = null
            if (problem == null) actions.onDismiss() else error = problem
        }
    }

    BasicTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
        decorationBox = { field ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (query.isEmpty()) {
                    Text(stringResource(R.string.gif_search_hint), style = MaterialTheme.typography.bodyLarge, color = chat.subtle)
                }
                field()
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    error?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = chat.failed, modifier = Modifier.padding(bottom = 8.dp))
    }
    Box(Modifier.fillMaxWidth().height(460.dp)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = grid,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(gifs, key = { it.id }) { gif ->
                val ratio = if (gif.preview.width > 0 && gif.preview.height > 0) {
                    gif.preview.width.toFloat() / gif.preview.height
                } else {
                    1f
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio.coerceIn(0.6f, 2f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(chat.incomingBubble)
                        .clickable(enabled = picking == null) { picking = gif },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = gif.preview.url,
                        contentDescription = gif.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (picking == gif) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f)))
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
            item(span = { GridItemSpan(2) }) {
                // Giphy's terms ask apps using its API to credit it.
                Text(
                    stringResource(R.string.gif_powered_by),
                    style = MaterialTheme.typography.labelSmall,
                    color = chat.subtle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
            }
        }
        if (loading && gifs.isEmpty()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun FacesTab(actions: AttachPanelActions) {
    val context = LocalContext.current
    val recent = remember { Prefs.recentFaces(context) }
    val rest = remember(recent) { Faces.ALL.filterNot { it in recent } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxWidth().height(500.dp),
    ) {
        if (recent.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) { SectionLabel(stringResource(R.string.faces_recent)) }
            items(recent, key = { "recent-$it" }) { face -> FaceCell(face, actions) }
            item(span = { GridItemSpan(2) }) { SectionLabel(stringResource(R.string.faces_all)) }
        }
        items(rest, key = { it }) { face -> FaceCell(face, actions) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalChatColors.current.subtle,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun FaceCell(face: String, actions: AttachPanelActions) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .clickable {
                Prefs.useFace(context, face)
                actions.onInsertFace(face)
                actions.onDismiss()
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(face, fontSize = 15.sp, fontFamily = FontFamily.Default, maxLines = 1, softWrap = false)
    }
}
