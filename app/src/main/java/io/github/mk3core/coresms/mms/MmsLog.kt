package io.github.mk3core.coresms.mms

import android.content.Context
import java.io.File

/**
 * A lasting record of what happened to each MMS: when the carrier announced
 * it, every download or send attempt, and the carrier's answer. Android's
 * own log only reaches back a few minutes, so without this a failure can't
 * be explained after the fact. Shown in a message's Details.
 *
 * Holds no message content or addresses, only events and numbers.
 */
object MmsLog {

    data class Event(val time: Long, val text: String)

    private const val MAX_EVENTS_PER_MESSAGE = 60

    private fun dir(context: Context) = File(context.filesDir, "mms-log").apply { mkdirs() }
    private fun file(context: Context, messageId: Long) = File(dir(context), "$messageId.log")

    @Synchronized
    fun record(context: Context, messageId: Long, text: String) {
        if (messageId < 0) return
        runCatching {
            val file = file(context, messageId)
            val line = "${System.currentTimeMillis()}\t${text.replace('\n', ' ')}\n"
            file.appendText(line)
            // Keep each message's history short.
            val lines = file.readLines()
            if (lines.size > MAX_EVENTS_PER_MESSAGE) {
                file.writeText(lines.takeLast(MAX_EVENTS_PER_MESSAGE).joinToString("\n", postfix = "\n"))
            }
        }
    }

    fun events(context: Context, messageId: Long): List<Event> = runCatching {
        val file = file(context, messageId)
        if (!file.exists()) return emptyList()
        file.readLines().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) null else line.substring(0, tab).toLongOrNull()?.let { Event(it, line.substring(tab + 1)) }
        }
    }.getOrDefault(emptyList())

    /** A downloaded message gets a new id; its history moves with it. */
    @Synchronized
    fun move(context: Context, fromId: Long, toId: Long) {
        runCatching {
            val from = file(context, fromId)
            if (!from.exists()) return
            val to = file(context, toId)
            to.appendText(from.readText())
            from.delete()
        }
    }
}
