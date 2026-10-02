package com.hamidmdh.quietinbox.sms.util

import android.content.Context
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {
    fun friendly(context: Context, ts: Long): String {
        val now = Calendar.getInstance()
        val that = Calendar.getInstance().apply { timeInMillis = ts }
        val timeFmt = DateFormat.getTimeFormat(context)
        val today = now.get(Calendar.YEAR) == that.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == that.get(Calendar.DAY_OF_YEAR)
        if (today) return timeFmt.format(Date(ts))
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        if (yesterday.get(Calendar.YEAR) == that.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == that.get(Calendar.DAY_OF_YEAR)
        ) {
            return "Yesterday"
        }
        return if (now.get(Calendar.YEAR) == that.get(Calendar.YEAR)) {
            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
        } else {
            SimpleDateFormat("M/d/yy", Locale.getDefault()).format(Date(ts))
        }
    }
}
