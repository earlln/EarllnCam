package com.example.earllncam

import android.content.Context

enum class CaptureMode { PHOTO, VIDEO }

class AppSettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var shotCount: Int
        get() = prefs.getInt(KEY_SHOT_COUNT, DEFAULT_SHOT_COUNT)
        set(value) = prefs.edit()
            .putInt(KEY_SHOT_COUNT, value.coerceIn(MIN_SHOT_COUNT, MAX_SHOT_COUNT))
            .apply()

    var mode: CaptureMode
        get() = runCatching {
            CaptureMode.valueOf(prefs.getString(KEY_MODE, CaptureMode.PHOTO.name)!!)
        }.getOrDefault(CaptureMode.PHOTO)
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    var keepOnlyLatest: Boolean
        get() = prefs.getBoolean(KEY_KEEP_ONLY_LATEST, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_ONLY_LATEST, value).apply()

    var videoClipSeconds: Int
        get() = prefs.getInt(KEY_VIDEO_SECONDS, DEFAULT_VIDEO_SECONDS)
        set(value) = prefs.edit()
            .putInt(KEY_VIDEO_SECONDS, value.coerceIn(MIN_VIDEO_SECONDS, MAX_VIDEO_SECONDS))
            .apply()

    companion object {
        private const val PREFS_NAME = "earllncam_settings"
        private const val KEY_SHOT_COUNT = "shot_count"
        private const val KEY_MODE = "mode"
        private const val KEY_KEEP_ONLY_LATEST = "keep_only_latest"
        private const val KEY_VIDEO_SECONDS = "video_seconds"

        const val DEFAULT_SHOT_COUNT = 6
        const val MIN_SHOT_COUNT = 1
        const val MAX_SHOT_COUNT = 20

        const val DEFAULT_VIDEO_SECONDS = 3
        const val MIN_VIDEO_SECONDS = 1
        const val MAX_VIDEO_SECONDS = 15
    }
}
