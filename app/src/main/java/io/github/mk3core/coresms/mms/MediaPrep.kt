package io.github.mk3core.coresms.mms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** An attachment ready to go into an MMS. */
class PreparedMedia(val bytes: ByteArray, val mimeType: String, val fileName: String)

/** Thrown when an attachment can't be made small enough for the carrier's MMS limit. */
class MediaTooLargeException(val fileName: String, val limitBytes: Int) : Exception("$fileName is too large")

/**
 * Fits picked photos and videos into the carrier's MMS size limit. Photos
 * (including iPhone HEIC) are resized and re-encoded as JPEG; other files are
 * sent as they are if they fit.
 */
object MediaPrep {

    private const val OVERHEAD_BYTES = 16 * 1024 // headers, SMIL layout and text
    private const val MIN_DIMENSION = 320

    fun prepare(context: Context, uris: List<Uri>, limits: CarrierLimits): List<PreparedMedia> {
        if (uris.isEmpty()) return emptyList()
        val budget = (limits.maxMessageBytes - OVERHEAD_BYTES).coerceAtLeast(32 * 1024)
        val perItem = budget / uris.size
        return uris.mapIndexed { index, uri -> prepareOne(context, uri, index, perItem, limits) }
    }

    private fun prepareOne(context: Context, uri: Uri, index: Int, limit: Int, limits: CarrierLimits): PreparedMedia {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)?.lowercase() ?: "application/octet-stream"
        val name = displayName(context, uri) ?: "attachment_$index"

        if (mimeType.startsWith("image/") && mimeType != "image/gif") {
            return PreparedMedia(compressImage(context, uri, name, limit, limits), "image/jpeg", "image_$index.jpg")
        }

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Can't read $name")
        if (bytes.size > limit) throw MediaTooLargeException(name, limits.maxMessageBytes)
        val extension = name.substringAfterLast('.', "").takeIf { it.length in 1..5 }
            ?: mimeType.substringAfter('/').substringBefore(';')
        val prefix = when {
            mimeType.startsWith("video/") -> "video"
            mimeType.startsWith("audio/") -> "audio"
            mimeType.startsWith("image/") -> "image"
            else -> "file"
        }
        return PreparedMedia(bytes, mimeType, "${prefix}_$index.$extension")
    }

    private fun compressImage(context: Context, uri: Uri, name: String, limit: Int, limits: CarrierLimits): ByteArray {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        var maxDimension = max(limits.maxImageWidth, limits.maxImageHeight)
        while (maxDimension >= MIN_DIMENSION) {
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val (w, h) = info.size.width to info.size.height
                val scale = minOf(1f, maxDimension.toFloat() / max(w, h))
                decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            try {
                for (quality in intArrayOf(90, 80, 70, 60, 50, 40)) {
                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    if (out.size() <= limit) return out.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
            maxDimension = (maxDimension * 0.75f).roundToInt()
        }
        throw MediaTooLargeException(name, limits.maxMessageBytes)
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()
}
