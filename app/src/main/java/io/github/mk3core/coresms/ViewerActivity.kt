package io.github.mk3core.coresms

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.IntentCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import io.github.mk3core.coresms.data.Attachment
import io.github.mk3core.coresms.ui.theme.CoreSmsTheme

/** Full-screen photo (pinch to zoom) or video/audio player, with Save and Share. */
class ViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uri = IntentCompat.getParcelableExtra(intent, EXTRA_URI, Uri::class.java)
        val mimeType = intent.getStringExtra(EXTRA_MIME)
        if (uri == null || mimeType == null) {
            finish()
            return
        }
        val attachment = Attachment(uri, mimeType, intent.getStringExtra(EXTRA_NAME))
        setContent {
            CoreSmsTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (attachment.isImage) ZoomableImage(uri) else MediaPlayer(uri)
                    Row(
                        Modifier.fillMaxWidth().safeDrawingPadding().padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = ::finish) { Icon(Icons.Filled.Close, stringResource(R.string.close), tint = Color.White) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { save(attachment) }) { Text(stringResource(R.string.save), color = Color.White) }
                        IconButton(onClick = { AttachmentFiles.share(this@ViewerActivity, attachment) }) {
                            Icon(Icons.Filled.Share, stringResource(R.string.share), tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    private fun save(attachment: Attachment) {
        val saved = AttachmentFiles.saveToDevice(this, attachment)
        Toast.makeText(this, if (saved) R.string.saved else R.string.save_failed, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val EXTRA_URI = "uri"
        private const val EXTRA_MIME = "mime"
        private const val EXTRA_NAME = "name"

        fun intent(context: Context, attachment: Attachment): Intent = Intent(context, ViewerActivity::class.java)
            .putExtra(EXTRA_URI, attachment.uri)
            .putExtra(EXTRA_MIME, attachment.mimeType)
            .putExtra(EXTRA_NAME, attachment.name)
    }
}

@Composable
private fun ZoomableImage(uri: Uri) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

@Composable
private fun MediaPlayer(uri: Uri) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = Modifier.fillMaxSize(),
    )
}
