package io.github.mk3core.coresms.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import android.util.Log
import io.github.mk3core.coresms.DefaultSmsApp
import io.github.mk3core.coresms.mmslib.pdu.PduHeaders
import io.github.mk3core.coresms.reactions.Reactions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln

/** One message found by [SearchIndex.search]: where it is and the text to show. */
data class MessageHit(
    val threadId: Long,
    val messageKey: String,
    val date: Long,
    val body: String,
)

/**
 * A word index over every message, so search can find text anywhere, not
 * just in each conversation's latest message.
 *
 * Privacy:
 * - It never holds the messages themselves. Each message is stored as the
 *   words it contains in alphabetical order, so the index can say which words
 *   appear in which message but not what the message said. Results show the
 *   real text by reading it from Android's message database.
 * - It lives in the app's private no_backup folder, which Android's storage
 *   encryption covers, and is excluded from backups and phone-to-phone
 *   transfers (see data_extraction_rules.xml).
 * - Deleted messages leave the index on the next sync. secure_delete zeroes
 *   the freed space and the full-text index is merged afterwards, so their
 *   words don't linger in old parts of the file.
 * - Turning search off in Settings deletes the file. Nothing here is logged
 *   or sent anywhere.
 *
 * Ranking is BM25, computed from FTS4's matchinfo() because Android's SQLite
 * doesn't reliably include FTS5, with a boost for recent messages.
 */
object SearchIndex {

    private const val TAG = "SearchIndex"
    private const val FILE_NAME = "search.db"
    private const val MAX_RESULTS = 200
    private const val BATCH = 400

    // BM25 constants, the usual defaults.
    private const val K1 = 1.2
    private const val B = 0.75

    /** A message this many days old counts about half as much as one from today. */
    private const val RECENCY_DAYS = 120.0

    private val lock = Mutex()
    private var helper: Helper? = null

    fun enabled(context: Context): Boolean = Prefs.searchMessages(context)

    /**
     * Brings the index in line with Android's message database: adds new
     * messages and removes deleted ones. Cheap when nothing changed.
     */
    suspend fun sync(context: Context) {
        if (!enabled(context) || !DefaultSmsApp.isDefault(context)) return
        lock.withLock {
            if (!enabled(context)) return
            runCatching { syncLocked(context.applicationContext) }
                .onFailure { Log.w(TAG, "Sync failed: ${it.javaClass.simpleName}") }
        }
    }

    /** Turns message search off: closes and deletes the index file. */
    suspend fun deleteIndex(context: Context) {
        lock.withLock {
            helper?.close()
            helper = null
            SQLiteDatabase.deleteDatabase(file(context))
        }
    }

    /**
     * Messages that contain every word of [query] (the last one can be
     * partly typed), best matches first. Empty when search is off.
     */
    suspend fun search(context: Context, query: String): List<MessageHit> {
        if (!enabled(context)) return emptyList()
        val words = Words.split(query)
        if (words.isEmpty()) return emptyList()
        // Every word matches as a prefix, so results appear while typing.
        val match = words.joinToString(" ") { "\"$it*\"" }
        data class Scored(val docId: Long, val thread: Long, val date: Long, val score: Double)
        val scored = lock.withLock {
            if (!enabled(context)) return emptyList()
            val db = runCatching { db(context.applicationContext) }.getOrNull() ?: return emptyList()
            val now = System.currentTimeMillis()
            val rows = ArrayList<Scored>()
            runCatching {
                db.rawQuery(
                    "SELECT d.docid, d.thread, d.date, matchinfo(fts, 'pcnalx') " +
                        "FROM fts JOIN docs d ON d.docid = fts.docid WHERE fts MATCH ?",
                    arrayOf(match),
                ).use { c ->
                    while (c.moveToNext()) {
                        val date = c.getLong(2)
                        val ageDays = ((now - date).coerceAtLeast(0)) / 86_400_000.0
                        val recency = 1.0 + exp(-ageDays * ln(2.0) / RECENCY_DAYS)
                        rows += Scored(c.getLong(0), c.getLong(1), date, bm25(c.getBlob(3)) * recency)
                    }
                }
            }.onFailure { Log.w(TAG, "Search failed: ${it.javaClass.simpleName}") }
            rows
        }
        val top = scored.sortedWith(compareByDescending<Scored> { it.score }.thenByDescending { it.date }).take(MAX_RESULTS)
        val bodies = bodies(context, top.map { it.docId })
        return top.mapNotNull { hit ->
            val body = bodies[hit.docId] ?: return@mapNotNull null // deleted since the last sync
            MessageHit(hit.thread, keyOf(hit.docId), hit.date, body)
        }
    }

    /**
     * BM25 for one row, from matchinfo 'pcnalx': phrase count, column count,
     * row count, average tokens, this row's tokens, then per phrase and column
     * (hits here, hits everywhere, rows with a hit).
     */
    internal fun bm25(blob: ByteArray): Double {
        val ints = ByteBuffer.wrap(blob).order(ByteOrder.nativeOrder()).asIntBuffer()
        fun at(i: Int) = ints.get(i).toLong() and 0xffffffffL
        val phrases = at(0).toInt()
        val columns = at(1).toInt()
        val rows = at(2).toDouble()
        val base = 3
        val avg = (0 until columns).map { at(base + it).toDouble() }
        val len = (0 until columns).map { at(base + columns + it).toDouble() }
        val x = base + 2 * columns
        var score = 0.0
        for (p in 0 until phrases) {
            for (col in 0 until columns) {
                val i = x + 3 * (p * columns + col)
                val tf = at(i).toDouble()
                if (tf == 0.0) continue
                val df = at(i + 2).toDouble()
                val idf = ln((rows - df + 0.5) / (df + 0.5) + 1.0)
                val norm = if (avg[col] > 0) len[col] / avg[col] else 1.0
                score += idf * tf * (K1 + 1) / (tf + K1 * (1 - B + B * norm))
            }
        }
        return score
    }

    // Message ids: SMS and MMS ids overlap, so SMS are even and MMS odd.
    private fun smsDoc(id: Long) = id * 2
    private fun mmsDoc(id: Long) = id * 2 + 1
    private fun keyOf(docId: Long) = (if (docId % 2 == 1L) "m" else "s") + docId / 2

    private fun file(context: Context) = File(context.applicationContext.noBackupFilesDir, FILE_NAME)

    private fun db(context: Context): SQLiteDatabase =
        (helper ?: Helper(context, file(context)).also { helper = it }).writableDatabase

    private class Helper(context: Context, file: File) : SQLiteOpenHelper(context, file.absolutePath, null, 1) {
        init {
            // No WAL file holding old copies of pages.
            setWriteAheadLoggingEnabled(false)
        }

        override fun onConfigure(db: SQLiteDatabase) {
            // Freed space is overwritten with zeros, so deleted messages' words don't linger.
            db.execSQL("PRAGMA secure_delete = ON")
        }

        override fun onCreate(db: SQLiteDatabase) {
            // words: the message's words, alphabetical, so word order isn't kept.
            db.execSQL("CREATE TABLE docs (docid INTEGER PRIMARY KEY, thread INTEGER NOT NULL, date INTEGER NOT NULL, words TEXT NOT NULL)")
            // Words are already lowercased and split by Words, so the simple tokenizer is enough.
            db.execSQL("CREATE VIRTUAL TABLE fts USING fts4(content=\"docs\", words)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS fts")
            db.execSQL("DROP TABLE IF EXISTS docs")
            onCreate(db)
        }
    }

    private class Doc(val docId: Long, val thread: Long, val date: Long, val words: String)

    private fun syncLocked(context: Context) {
        val db = db(context)
        val indexed = HashSet<Long>()
        db.rawQuery("SELECT docid FROM docs", null).use { while (it.moveToNext()) indexed += it.getLong(0) }

        val smsIds = ids(context, Sms.CONTENT_URI, "${Sms.TYPE} != ${Sms.MESSAGE_TYPE_DRAFT}")
        // Not-yet-downloaded MMS have no text; they come back as new messages once downloaded.
        val mmsIds = ids(
            context,
            Mms.CONTENT_URI,
            "${Mms.MESSAGE_BOX} != ${Mms.MESSAGE_BOX_DRAFTS} AND ${Mms.MESSAGE_TYPE} != ${PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND}",
        )
        if (smsIds == null || mmsIds == null) return // couldn't read; try again next time

        val current = HashSet<Long>(smsIds.size + mmsIds.size)
        smsIds.forEach { current += smsDoc(it) }
        mmsIds.forEach { current += mmsDoc(it) }

        val gone = indexed.filter { it !in current }
        if (gone.isNotEmpty()) {
            db.transaction {
                gone.forEach { docId ->
                    // External-content FTS reads the old words from docs to remove them, so fts goes first.
                    db.execSQL("DELETE FROM fts WHERE docid = $docId")
                    db.execSQL("DELETE FROM docs WHERE docid = $docId")
                }
            }
            // Rewrites the index into one piece, dropping the deleted messages' old entries.
            db.execSQL("INSERT INTO fts(fts) VALUES('optimize')")
        }

        // Stops early if search is turned off partway through the first build.
        for (chunk in smsIds.filter { smsDoc(it) !in indexed }.chunked(BATCH)) {
            if (!enabled(context)) return
            insert(db, loadSms(context, chunk))
        }
        for (chunk in mmsIds.filter { mmsDoc(it) !in indexed }.chunked(BATCH)) {
            if (!enabled(context)) return
            insert(db, loadMms(context, chunk))
        }
    }

    private fun insert(db: SQLiteDatabase, docs: List<Doc>) {
        if (docs.isEmpty()) return
        db.transaction {
            docs.forEach { doc ->
                db.insertWithOnConflict(
                    "docs",
                    null,
                    ContentValues().apply {
                        put("docid", doc.docId)
                        put("thread", doc.thread)
                        put("date", doc.date)
                        put("words", doc.words)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
                db.execSQL("INSERT INTO fts(docid, words) VALUES (?, ?)", arrayOf<Any>(doc.docId, doc.words))
            }
        }
    }

    private inline fun SQLiteDatabase.transaction(block: () -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private fun ids(context: Context, uri: Uri, selection: String): List<Long>? = runCatching {
        val result = ArrayList<Long>()
        context.contentResolver.query(uri, arrayOf("_id"), selection, null, null)?.use { c ->
            while (c.moveToNext()) result += c.getLong(0)
        } ?: return null
        result
    }.getOrNull()

    /** What's indexed for a message body: nothing for reactions, which show up folded into other messages. */
    private fun wordsOf(body: String): String =
        if (Reactions.parse(body) != null) "" else Words.split(body).sorted().joinToString(" ")

    private fun loadSms(context: Context, ids: List<Long>): List<Doc> {
        val result = ArrayList<Doc>(ids.size)
        runCatching {
            context.contentResolver.query(
                Sms.CONTENT_URI,
                arrayOf(Sms._ID, Sms.THREAD_ID, Sms.DATE, Sms.BODY),
                "${Sms._ID} IN (${ids.joinToString(",")})",
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    result += Doc(smsDoc(c.getLong(0)), c.getLong(1), c.getLong(2), wordsOf(c.getString(3).orEmpty()))
                }
            }
        }
        return result
    }

    private fun loadMms(context: Context, ids: List<Long>): List<Doc> {
        val texts = mmsTexts(context, ids)
        val result = ArrayList<Doc>(ids.size)
        runCatching {
            context.contentResolver.query(
                Mms.CONTENT_URI,
                arrayOf(Mms._ID, Mms.THREAD_ID, Mms.DATE),
                "${Mms._ID} IN (${ids.joinToString(",")})",
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    // MMS dates are in seconds.
                    result += Doc(mmsDoc(id), c.getLong(1), c.getLong(2) * 1000, wordsOf(texts[id].orEmpty()))
                }
            }
        }
        return result
    }

    /** The text parts of each MMS, joined like the conversation shows them. */
    private fun mmsTexts(context: Context, ids: List<Long>): Map<Long, String> {
        val parts = HashMap<Long, MutableList<String>>()
        ids.chunked(BATCH).forEach { chunk ->
            runCatching {
                context.contentResolver.query(
                    Uri.parse("content://mms/part"),
                    arrayOf(Mms.Part.MSG_ID, Mms.Part.TEXT),
                    "${Mms.Part.MSG_ID} IN (${chunk.joinToString(",")}) AND ${Mms.Part.CONTENT_TYPE} = 'text/plain'",
                    null,
                    "${Mms.Part.SEQ}, ${Mms.Part._ID}",
                )?.use { c ->
                    while (c.moveToNext()) parts.getOrPut(c.getLong(0)) { mutableListOf() } += c.getString(1).orEmpty()
                }
            }
        }
        return parts.mapValues { (_, texts) -> texts.joinToString("\n").trim() }
    }

    /** The current text of each found message, read from Android's message database. */
    private fun bodies(context: Context, docIds: List<Long>): Map<Long, String> {
        val result = HashMap<Long, String>()
        val sms = docIds.filter { it % 2 == 0L }.map { it / 2 }
        val mms = docIds.filter { it % 2 == 1L }.map { it / 2 }
        sms.chunked(BATCH).forEach { chunk ->
            runCatching {
                context.contentResolver.query(
                    Sms.CONTENT_URI,
                    arrayOf(Sms._ID, Sms.BODY),
                    "${Sms._ID} IN (${chunk.joinToString(",")})",
                    null,
                    null,
                )?.use { c -> while (c.moveToNext()) result[smsDoc(c.getLong(0))] = c.getString(1).orEmpty() }
            }
        }
        if (mms.isNotEmpty()) mmsTexts(context, mms).forEach { (id, text) -> result[mmsDoc(id)] = text }
        return result
    }
}
