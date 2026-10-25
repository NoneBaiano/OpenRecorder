// SPDX-License-Identifier: GPL-3.0-only
package com.openrecorder.app

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList

/** Framework locales on Android 13+, localized contexts on Android 10–12. */
object AppLanguage {
    private val tags = listOf("", "en", "tr")
    private const val PREFERENCES = "app_language"
    private const val KEY = "language_tag"

    private fun languageTag(context: Context): String =
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)
                .applicationLocales.toLanguageTags()
        } else {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(KEY, "").orEmpty()
        }

    fun selectedIndex(context: Context): Int =
        tags.indexOf(languageTag(context)).coerceAtLeast(0)

    fun select(activity: Activity, index: Int) {
        val tag = tags.getOrNull(index) ?: return
        if (tag == languageTag(activity)) return
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java)
                .applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(KEY, tag).apply()
            activity.recreate()
        }
        RecorderTileService.requestStateRefresh(activity)
    }

    @JvmStatic
    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val tag = languageTag(context)
        if (tag.isEmpty()) return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(tag))
        return context.createConfigurationContext(configuration)
    }
}
