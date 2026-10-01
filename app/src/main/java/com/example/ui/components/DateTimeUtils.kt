package com.example.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateTimeUtils {

    private val utcFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val localFormatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    fun formatUtc(timestamp: Long): String {
        return utcFormatter.format(Date(timestamp))
    }

    fun formatLocal(timestamp: Long): String {
        return localFormatter.format(Date(timestamp))
    }

    fun formatRelative(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        if (diff < 0) {
            val future = -diff
            val mins = future / (60 * 1000)
            return if (mins < 60) "in ${mins}m" else "in ${mins / 60}h"
        }

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 45 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> formatLocal(timestamp)
        }
    }
}
