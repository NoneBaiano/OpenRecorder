// SPDX-License-Identifier: GPL-3.0-only

package com.openrecorder.app

import android.content.Context

internal object ThemeMode {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2
    const val MONET_SYSTEM = 3
    const val MONET_LIGHT = 4
    const val MONET_DARK = 5

    fun normalize(value: Int): Int = if (value in SYSTEM..MONET_DARK) value else SYSTEM
}

internal class RecorderPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadThemeMode(): Int = ThemeMode.normalize(
        preferences.getInt(KEY_THEME_MODE, ThemeMode.SYSTEM),
    )

    fun saveThemeMode(value: Int) {
        preferences.edit().putInt(KEY_THEME_MODE, ThemeMode.normalize(value)).apply()
    }

    fun loadEnablePredictiveBack(): Boolean {
        if (preferences.contains(KEY_ENABLE_PREDICTIVE_BACK)) {
            return preferences.getBoolean(KEY_ENABLE_PREDICTIVE_BACK, false)
        }

        val enabled = if (preferences.contains(KEY_DISABLE_PREDICTIVE_BACK_LEGACY)) {
            !preferences.getBoolean(KEY_DISABLE_PREDICTIVE_BACK_LEGACY, true)
        } else {
            false
        }
        preferences.edit()
            .putBoolean(KEY_ENABLE_PREDICTIVE_BACK, enabled)
            .putBoolean(KEY_DISABLE_PREDICTIVE_BACK_LEGACY, !enabled)
            .apply()
        return enabled
    }

    fun saveEnablePredictiveBack(value: Boolean) {
        preferences.edit()
            .putBoolean(KEY_ENABLE_PREDICTIVE_BACK, value)
            .putBoolean(KEY_DISABLE_PREDICTIVE_BACK_LEGACY, !value)
            .apply()
    }

    fun loadAudioSource(): AudioSource = AudioSource.fromOrdinal(
        preferences.getInt(KEY_AUDIO_SOURCE, AudioSource.NONE.ordinal),
    )

    fun saveAudioSource(value: AudioSource) {
        preferences.edit().putInt(KEY_AUDIO_SOURCE, value.ordinal).apply()
    }

    fun loadSampleRate(): Int = RecordingOptions.normalizeSampleRate(
        preferences.getInt(KEY_SAMPLE_RATE, RecordingOptions.DEFAULT_SAMPLE_RATE),
    )

    fun saveSampleRate(value: Int) {
        preferences.edit()
            .putInt(KEY_SAMPLE_RATE, RecordingOptions.normalizeSampleRate(value))
            .apply()
    }

    fun loadVideoBitrate(): Int = RecordingOptions.normalizeVideoBitrate(
        preferences.getInt(KEY_VIDEO_BITRATE, RecordingOptions.DEFAULT_VIDEO_BITRATE),
    )

    fun saveVideoBitrate(value: Int) {
        preferences.edit()
            .putInt(KEY_VIDEO_BITRATE, RecordingOptions.normalizeVideoBitrate(value))
            .apply()
    }

    fun loadVideoResolution(): Int = RecordingOptions.normalizeVideoResolution(
        preferences.getInt(KEY_VIDEO_RESOLUTION, RecordingOptions.DEFAULT_VIDEO_RESOLUTION),
    )

    fun saveVideoResolution(value: Int) {
        preferences.edit()
            .putInt(KEY_VIDEO_RESOLUTION, RecordingOptions.normalizeVideoResolution(value))
            .apply()
    }

    fun loadVideoFrameRate(): Int = RecordingOptions.normalizeVideoFrameRate(
        preferences.getInt(KEY_VIDEO_FRAME_RATE, RecordingOptions.DEFAULT_VIDEO_FRAME_RATE),
    )

    fun saveVideoFrameRate(value: Int) {
        preferences.edit()
            .putInt(KEY_VIDEO_FRAME_RATE, RecordingOptions.normalizeVideoFrameRate(value))
            .apply()
    }

    fun loadForce16By9Letterboxing(): Boolean = preferences.getBoolean(
        KEY_FORCE_16_BY_9_LETTERBOXING,
        false,
    )

    fun saveForce16By9Letterboxing(value: Boolean) {
        preferences.edit().putBoolean(KEY_FORCE_16_BY_9_LETTERBOXING, value).apply()
    }

    fun loadVideoCodec(): Int = RecordingOptions.normalizeVideoCodec(
        preferences.getInt(KEY_VIDEO_CODEC, RecordingOptions.DEFAULT_VIDEO_CODEC),
    )

    fun saveVideoCodec(value: Int) {
        preferences.edit()
            .putInt(KEY_VIDEO_CODEC, RecordingOptions.normalizeVideoCodec(value))
            .apply()
    }

    fun loadCountdownSeconds(): Int = RecordingOptions.normalizeCountdownSeconds(
        preferences.getInt(KEY_COUNTDOWN_SECONDS, RecordingOptions.DEFAULT_COUNTDOWN_SECONDS),
    )

    fun saveCountdownSeconds(value: Int) {
        preferences.edit()
            .putInt(KEY_COUNTDOWN_SECONDS, RecordingOptions.normalizeCountdownSeconds(value))
            .apply()
    }

    fun loadOrientation(): Int = RecordingOptions.normalizeOrientation(
        preferences.getInt(KEY_ORIENTATION, RecordingOptions.DEFAULT_ORIENTATION),
    )

    fun saveOrientation(value: Int) {
        preferences.edit()
            .putInt(KEY_ORIENTATION, RecordingOptions.normalizeOrientation(value))
            .apply()
    }

    fun loadNamingPattern(): String = RecordingOptions.normalizeNamingPattern(
        preferences.getString(KEY_NAMING_PATTERN, RecordingOptions.DEFAULT_NAMING_PATTERN),
    )

    fun saveNamingPattern(value: String) {
        preferences.edit()
            .putString(KEY_NAMING_PATTERN, RecordingOptions.normalizeNamingPattern(value))
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "recorder_preferences"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ENABLE_PREDICTIVE_BACK = "enable_predictive_back"
        const val KEY_DISABLE_PREDICTIVE_BACK_LEGACY = "disable_predictive_back"
        const val KEY_AUDIO_SOURCE = "audio_source"
        const val KEY_SAMPLE_RATE = "sample_rate"
        const val KEY_VIDEO_BITRATE = "video_bitrate"
        const val KEY_VIDEO_RESOLUTION = "video_resolution"
        const val KEY_VIDEO_FRAME_RATE = "video_frame_rate"
        const val KEY_FORCE_16_BY_9_LETTERBOXING = "force_16_by_9_letterboxing"
        const val KEY_VIDEO_CODEC = "video_codec"
        const val KEY_COUNTDOWN_SECONDS = "countdown_seconds"
        const val KEY_ORIENTATION = "recording_orientation"
        const val KEY_NAMING_PATTERN = "naming_pattern"
    }
}
