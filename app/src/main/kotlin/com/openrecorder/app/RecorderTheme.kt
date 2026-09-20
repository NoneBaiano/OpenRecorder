// SPDX-License-Identifier: GPL-3.0-only

package com.openrecorder.app

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

@Composable
internal fun RecorderTheme(
    themeMode: Int,
    systemDark: Boolean,
    content: @Composable () -> Unit,
) {
    val normalizedMode = ThemeMode.normalize(themeMode)
    val resolvedDark = when (normalizedMode) {
        ThemeMode.LIGHT, ThemeMode.MONET_LIGHT -> false
        ThemeMode.DARK, ThemeMode.MONET_DARK -> true
        else -> systemDark
    }
    val controller = remember(normalizedMode, resolvedDark) {
        when (normalizedMode) {
            ThemeMode.LIGHT -> ThemeController(ColorSchemeMode.Light)
            ThemeMode.DARK -> ThemeController(ColorSchemeMode.Dark)
            ThemeMode.MONET_SYSTEM, ThemeMode.MONET_LIGHT, ThemeMode.MONET_DARK -> ThemeController(
                if (resolvedDark) ColorSchemeMode.MonetDark else ColorSchemeMode.MonetLight,
                colorSpec = ThemeColorSpec.Spec2025,
                paletteStyle = ThemePaletteStyle.TonalSpot,
            )

            else -> ThemeController(
                if (resolvedDark) ColorSchemeMode.Dark else ColorSchemeMode.Light,
            )
        }
    }

    MiuixTheme(controller = controller) {
        SynchronizeSystemBars(darkTheme = resolvedDark)
        content()
    }
}

@Composable
private fun SynchronizeSystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return

    DisposableEffect(view, darkTheme) {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).run {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        onDispose { }
    }
}
