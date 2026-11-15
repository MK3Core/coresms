package io.github.mk3core.coresms

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import io.github.mk3core.coresms.data.Attachment
import java.io.File

/** Saving and sharing attachments. Message parts can't be handed to other apps directly, so they're copied first. */
object AttachmentFiles {

    /** Copies an attachment into the phone's Pictures, Movies, Music or Downloads folder under "CoreSMS". */
    fun saveToDevice(context: Context, attachment: Attachment): Boolean = runCatching {
        val (collection, folder) = when {
            attachment.isImage -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
            attachment.isVideo -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
            attachment.isAudio -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_DOWNLOADS
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName(attachment))
            put(MediaStore.MediaColumns.MIME_TYPE, attachment.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/CoreSMS")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val target = resolver.insert(collection, values) ?: return false
        resolver.openOutputStream(target)?.use { out ->
            resolver.openInputStream(attachment.uri)?.use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(target, values, null, null)
        true
    }.getOrDefault(false)

    /** Opens the share sheet for an attachment. */
    fun share(context: Context, attachment: Attachment): Boolean = runCatching {
        val uri = shareableCopy(context, attachment)
        val send = Intent(Intent.ACTION_SEND)
            .setType(attachment.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    /** Opens an attachment CoreSMS can't show itself (contact cards, documents) in another app. */
    fun openExternally(context: Context, attachment: Attachment): Boolean = runCatching {
        val uri = shareableCopy(context, attachment)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, attachment.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(view)
        true
    }.getOrDefault(false)

    private fun shareableCopy(context: Context, attachment: Attachment): Uri {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
        val file = File(dir, fileName(attachment))
        context.contentResolver.openInputStream(attachment.uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.mmsfiles", file)
    }

    private fun fileName(attachment: Attachment): String {
        val base = attachment.name?.substringAfterLast('/')?.takeIf { it.isNotBlank() && it.contains('.') }
        if (base != null) return base
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(attachment.mimeType) ?: "bin"
        return "CoreSMS_${System.currentTimeMillis()}.$extension"
    }
}
