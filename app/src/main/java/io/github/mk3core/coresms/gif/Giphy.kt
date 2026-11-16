package io.github.mk3core.coresms.gif

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.mk3core.coresms.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One size of a GIF that Giphy offers. */
data class Rendition(val url: String, val bytes: Int, val width: Int, val height: Int)

data class Gif(
    val id: String,
    val title: String,
    /** A small, low-frame-rate copy for the picker grid. */
    val preview: Rendition,
    /** Every size Giphy offers, to pick one that fits the carrier's MMS limit. */
    val renditions: List<Rendition>,
)

data class GifPage(val gifs: List<Gif>, val nextOffset: Int?)

/**
 * GIF search through Giphy's API at its loosest content rating ("r").
 * Nothing is sent to Giphy until the GIF tab is opened; searches go straight
 * from the phone to Giphy with the API key built into the app.
 */
object Giphy {

    private const val BASE = "https://api.giphy.com/v1/gifs"
    private const val RATING = "r"
    private const val PAGE = 30

    val configured: Boolean get() = BuildConfig.GIPHY_API_KEY.isNotBlank()

    suspend fun trending(offset: Int = 0): GifPage = get("$BASE/trending?${common(offset)}")

    suspend fun search(query: String, offset: Int = 0): GifPage =
        get("$BASE/search?${common(offset)}&q=${URLEncoder.encode(query.trim(), "UTF-8")}&lang=en")

    private fun common(offset: Int) =
        "api_key=${BuildConfig.GIPHY_API_KEY}&limit=$PAGE&offset=$offset&rating=$RATING"

    private suspend fun get(url: String): GifPage = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            if (connection.responseCode != 200) throw GiphyException(connection.responseCode)
            parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(body: String): GifPage {
        val json = JSONObject(body)
        val data = json.optJSONArray("data")
        val gifs = buildList {
            for (i in 0 until (data?.length() ?: 0)) {
                val item = data!!.getJSONObject(i)
                val images = item.optJSONObject("images") ?: continue
                val renditions = SEND_SIZES.mapNotNull { rendition(images.optJSONObject(it)) }
                val preview = PREVIEW_SIZES.firstNotNullOfOrNull { rendition(images.optJSONObject(it)) } ?: continue
                add(Gif(item.optString("id"), item.optString("title"), preview, renditions))
            }
        }
        val pagination = json.optJSONObject("pagination")
        val next = pagination?.let { p ->
            val end = p.optInt("offset") + p.optInt("count")
            end.takeIf { end < p.optInt("total_count") && gifs.isNotEmpty() }
        }
        return GifPage(gifs, next)
    }

    private fun rendition(json: JSONObject?): Rendition? {
        val url = json?.optString("url")?.takeIf { it.startsWith("https://") } ?: return null
        return Rendition(
            url = url,
            bytes = json.optString("size").toIntOrNull() ?: 0,
            width = json.optString("width").toIntOrNull() ?: 0,
            height = json.optString("height").toIntOrNull() ?: 0,
        )
    }

    /**
     * The biggest size that fits [budgetBytes], so the GIF looks as good as
     * the carrier allows. Null if even the smallest is too big.
     */
    fun bestFit(gif: Gif, budgetBytes: Int): Rendition? =
        gif.renditions.filter { it.bytes in 1..budgetBytes }.maxByOrNull { it.bytes }

    /** Downloads [rendition] into the app's cache and returns a shareable Uri for the composer. */
    suspend fun download(context: Context, gif: Gif, rendition: Rendition): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "gifs").apply { mkdirs() }
        // Old picks are only needed until the message is sent.
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > DAY_MS }?.forEach { it.delete() }
        val file = File(dir, "${gif.id.ifBlank { "gif" }}-${rendition.bytes}.gif")
        if (!file.exists() || file.length() == 0L) {
            val connection = (URL(rendition.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 30_000
            }
            try {
                if (connection.responseCode != 200) throw GiphyException(connection.responseCode)
                connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
            } finally {
                connection.disconnect()
            }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.mmsfiles", file)
    }

    // Giphy's GIF renditions, largest to smallest. (Its mp4/webp versions don't work in MMS.)
    private val SEND_SIZES = listOf(
        "original", "downsized_large", "downsized_medium", "downsized", "fixed_height", "fixed_width",
        "fixed_height_downsampled", "fixed_width_downsampled", "fixed_height_small", "fixed_width_small",
    )
    private val PREVIEW_SIZES = listOf("fixed_width_downsampled", "fixed_width_small", "fixed_width", "downsized")
    private const val DAY_MS = 24 * 60 * 60 * 1000L
}

class GiphyException(val status: Int) : Exception("Giphy answered HTTP $status")
