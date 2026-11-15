package io.github.mk3core.coresms.ui

import android.content.Context
import android.text.format.DateUtils
import java.util.Calendar

object Times {

    /** Conversation list: "3:41 PM", "Yesterday", "Tue", "Nov 3", "Nov 3, 2025". */
    fun short(context: Context, millis: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        return when {
            sameDay(now, then) -> DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)
            isYesterday(now, then) -> DateUtils.getRelativeTimeSpanString(
                millis, now.timeInMillis, DateUtils.DAY_IN_MILLIS,
            ).toString()
            now.timeInMillis - millis < 6 * DateUtils.DAY_IN_MILLIS ->
                DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY)
            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) ->
                DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR)
            else -> DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_YEAR)
        }
    }

    /** Thread dividers: "Today 3:41 PM", "Yesterday 9:02 AM", "Tue 9:02 AM", "Nov 3, 9:02 AM". */
    fun divider(context: Context, millis: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        val time = DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)
        val day = when {
            sameDay(now, then) -> DateUtils.getRelativeTimeSpanString(millis, now.timeInMillis, DateUtils.DAY_IN_MILLIS).toString()
            isYesterday(now, then) -> DateUtils.getRelativeTimeSpanString(millis, now.timeInMillis, DateUtils.DAY_IN_MILLIS).toString()
            now.timeInMillis - millis < 6 * DateUtils.DAY_IN_MILLIS ->
                DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_WEEKDAY)
            else -> DateUtils.formatDateTime(
                context,
                millis,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_WEEKDAY,
            )
        }
        return "$day $time"
    }

    fun time(context: Context, millis: Long): String =
        DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_TIME)

    private fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isYesterday(now: Calendar, then: Calendar): Boolean {
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return sameDay(yesterday, then)
    }
}
