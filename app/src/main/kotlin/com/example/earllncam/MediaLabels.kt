package com.example.earllncam

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * How a capture describes itself on screen. Shared so the list and the viewer
 * label the same file identically.
 */
object MediaLabels {

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    /** e.g. "동영상 · 0:03 · 8.4 MB · 2026-09-06 09:12" */
    fun subtitle(context: Context, item: MediaVault.Item): String {
        val parts = mutableListOf(
            context.getString(
                if (item.isVideo) R.string.gallery_type_video else R.string.gallery_type_photo
            )
        )
        if (item.isVideo && item.durationMs > 0) parts.add(duration(item.durationMs))
        if (item.sizeBytes > 0) parts.add(size(item.sizeBytes))
        parts.add(DATE_FORMAT.format(Date(item.dateAddedSeconds * 1000L)))
        return parts.joinToString(" · ")
    }

    private fun duration(durationMs: Long): String {
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
    }

    private fun size(bytes: Long): String = when {
        bytes >= 1024 * 1024 ->
            String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
    }
}
