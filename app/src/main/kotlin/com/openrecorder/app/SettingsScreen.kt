// SPDX-License-Identifier: GPL-3.0-only

package com.openrecorder.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

private const val VERSION_TAPS_REQUIRED = 10
private const val SETTINGS_PAGE_MAIN = 0
private const val SETTINGS_PAGE_ABOUT_PROJECT = 1
private const val SETTINGS_PAGE_OPEN_SOURCE_LICENSES = 2
private const val SETTINGS_NAVIGATION_DURATION_MILLIS = 300
private const val SETTINGS_NAVIGATION_SETTLE_DURATION_MILLIS = 180
private const val SETTINGS_DESTINATION_PARALLAX_FRACTION = 0.2f
private const val SETTINGS_DESTINATION_DIM_AMOUNT = 0.5f
private val SettingsTransitionCornerRadius = 28.dp

@Composable
internal fun SettingsScreen(
    selectedThemeIndex: Int,
    enablePredictiveBack: Boolean,
    selectedAudioIndex: Int,
    selectedSampleRateIndex: Int,
    selectedVideoResolutionIndex: Int,
    selectedVideoFrameRateIndex: Int,
    force16By9Letterboxing: Boolean,
    selectedVideoBitrateIndex: Int,
    selectedVideoCodecIndex: Int,
    selectedCountdownIndex: Int,
    selectedNamingPatternIndex: Int,
    selectedOrientationIndex: Int,
    optionsEnabled: Boolean,
    onThemeSelected: (Int) -> Unit,
    onEnablePredictiveBackChanged: (Boolean) -> Unit,
    onAudioSelected: (Int) -> Unit,
    onSampleRateSelected: (Int) -> Unit,
    onVideoResolutionSelected: (Int) -> Unit,
    onVideoFrameRateSelected: (Int) -> Unit,
    onForce16By9LetterboxingChanged: (Boolean) -> Unit,
    onVideoBitrateSelected: (Int) -> Unit,
    onVideoCodecSelected: (Int) -> Unit,
    onCountdownSelected: (Int) -> Unit,
    onNamingPatternSelected: (Int) -> Unit,
    onOrientationSelected: (Int) -> Unit,
    onViewOnGitHub: () -> Unit,
    onViewLicense: () -> Unit,
    onOpenExternalUrl: (String) -> Unit,
) {
    var settingsPage by rememberSaveable { mutableIntStateOf(SETTINGS_PAGE_MAIN) }
    var versionTapCount by rememberSaveable { mutableIntStateOf(0) }
    var popupDismissRequestKey by remember { mutableIntStateOf(0) }
    var settingsPopupExpanded by remember { mutableStateOf(false) }
    val pageTransitionProgress = remember {
        Animatable(if (settingsPage == SETTINGS_PAGE_MAIN) 0f else 1f)
    }
    val coroutineScope = rememberCoroutineScope()
    val transitionInteractionSource = remember { MutableInteractionSource() }
    val transitionShape = remember { RoundedCornerShape(SettingsTransitionCornerRadius) }
    val showingSubpage = settingsPage != SETTINGS_PAGE_MAIN

    fun openSubpage(page: Int) {
        if (settingsPage != SETTINGS_PAGE_MAIN) return

        settingsPage = page
        coroutineScope.launch {
            pageTransitionProgress.snapTo(0f)
            pageTransitionProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = SETTINGS_NAVIGATION_DURATION_MILLIS,
                    easing = EaseInOut,
                ),
            )
        }
    }
    fun closeSubpage() {
        if (settingsPage == SETTINGS_PAGE_MAIN) return

        coroutineScope.launch {
            pageTransitionProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = SETTINGS_NAVIGATION_DURATION_MILLIS,
                    easing = EaseInOut,
                ),
            )
            settingsPage = SETTINGS_PAGE_MAIN
        }
    }

    if (showingSubpage) {
        if (enablePredictiveBack) {
            PredictiveBackHandler { progress ->
                try {
                    progress.collect { backEvent ->
                        pageTransitionProgress.snapTo(
                            1f - backEvent.progress.coerceIn(0f, 1f),
                        )
                    }
                    pageTransitionProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = SETTINGS_NAVIGATION_SETTLE_DURATION_MILLIS,
                            easing = EaseInOut,
                        ),
                    )
                    settingsPage = SETTINGS_PAGE_MAIN
                } catch (cancellation: CancellationException) {
                    coroutineScope.launch {
                        pageTransitionProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = SETTINGS_NAVIGATION_SETTLE_DURATION_MILLIS,
                                easing = EaseInOut,
                            ),
                        )
                    }
                    throw cancellation
                }
            }
        } else {
            BackHandler {
                closeSubpage()
            }
        }
    }

    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val themeOptions = remember(context) {
        listOf(
            context.getString(R.string.theme_system),
            context.getString(R.string.theme_light),
            context.getString(R.string.theme_dark),
            context.getString(R.string.theme_monet_system),
            context.getString(R.string.theme_monet_light),
            context.getString(R.string.theme_monet_dark),
        )
    }
    val audioOptions = remember(context) {
        listOf(
            context.getString(R.string.audio_none),
            context.getString(R.string.audio_microphone),
            context.getString(R.string.audio_internal),
            context.getString(R.string.audio_both),
        )
    }
    val sampleRateOptions = remember(context) {
        listOf(
            context.getString(R.string.sample_rate_44),
            context.getString(R.string.sample_rate_48),
        )
    }
    val videoResolutionOptions = remember(context) {
        listOf(
            context.getString(R.string.video_resolution_native),
            context.getString(R.string.video_resolution_1080p),
            context.getString(R.string.video_resolution_720p),
            context.getString(R.string.video_resolution_480p),
        )
    }
    val videoFrameRateOptions = remember(context) {
        listOf(
            context.getString(R.string.video_frame_rate_auto),
            context.getString(R.string.video_frame_rate_120),
            context.getString(R.string.video_frame_rate_90),
            context.getString(R.string.video_frame_rate_60),
            context.getString(R.string.video_frame_rate_30),
        )
    }
    val videoBitrateOptions = remember(context) {
        listOf(
            context.getString(R.string.video_bitrate_auto),
            context.getString(R.string.video_bitrate_4),
            context.getString(R.string.video_bitrate_8),
            context.getString(R.string.video_bitrate_16),
            context.getString(R.string.video_bitrate_24),
        )
    }
    val videoCodecOptions = remember(context) {
        listOf(
            context.getString(R.string.video_codec_h264),
            context.getString(R.string.video_codec_h265),
        )
    }
    val countdownOptions = remember(context) {
        listOf(
            context.getString(R.string.countdown_0_seconds),
            context.getString(R.string.countdown_3_seconds),
            context.getString(R.string.countdown_5_seconds),
            context.getString(R.string.countdown_10_seconds),
        )
    }
    val namingPatternOptions = remember(context) {
        listOf(
            context.getString(R.string.naming_pattern_day_month_year),
            context.getString(R.string.naming_pattern_month_day_year),
            context.getString(R.string.naming_pattern_year_month_day),
            context.getString(R.string.naming_pattern_year_day_month),
        )
    }
    val orientationOptions = remember(context) {
        listOf(
            context.getString(R.string.orientation_automatic),
            context.getString(R.string.orientation_portrait),
            context.getString(R.string.orientation_landscape),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.graphicsLayer {
                translationX = -size.width *
                    SETTINGS_DESTINATION_PARALLAX_FRACTION *
                    pageTransitionProgress.value
            },
            topBar = {
                TopAppBar(
                    title = stringResource(R.string.nav_settings),
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = 28.dp,
                ),
            ) {
                item {
                    SmallTitle(text = stringResource(R.string.section_appearance))
                    PreferenceCard {
                        SelectablePreference(
                            title = stringResource(R.string.theme_label),
                            items = themeOptions,
                            selectedIndex = selectedThemeIndex,
                            enabled = true,
                            predictiveBackEnabled = enablePredictiveBack,
                            dismissRequestKey = popupDismissRequestKey,
                            onExpandedChange = { settingsPopupExpanded = it },
                            onSelected = onThemeSelected,
                        )
                        SwitchPreference(
                            title = stringResource(R.string.enable_predictive_back),
                            summary = stringResource(R.string.enable_predictive_back_summary),
                            checked = enablePredictiveBack,
                            onCheckedChange = onEnablePredictiveBackChanged,
                        )
                    }
                }

                item {
                    SmallTitle(text = stringResource(R.string.section_options))
                    PreferenceCard {
                        SelectablePreference(
                            title = stringResource(R.string.video_resolution_label),
                            items = videoResolutionOptions,
                            selectedIndex = selectedVideoResolutionIndex,
                            enabled = optionsEnabled,
                            predictiveBackEnabled = enablePredictiveBack,
                            dismissRequestKey = popupDismissRequestKey,
                            onExpandedChange = { settingsPopupExpanded = it },
                            onSelected = onVideoResolutionSelected,
                        )
                        SelectablePreference(
                            title = stringResource(R.string.video_frame_rate_label),
                            items = videoFrameRateOptions,
                            selectedIndex = selectedVideoFrameRateIndex,
                            enabled = optionsEnabled,
                            predictiveBackEnabled = enablePredictiveBack,
                            dismissRequestKey = popupDismissRequestKey,
                            onExpandedChange = { settingsPopupExpanded = it },
                            onSelected = onVideoFrameRateSelected,
                        )
                        SelectablePreference(
                            title = stringResource(R.string.audio_label),
                            items = audioOptions,
                            selectedIndex = selectedAudioIndex,
                            enabled = optionsEnabled,
                            predictiveBackEnabled = enablePredictiveBack,
                            dismissRequestKey = popupDismissRequestKey,
                            onExpandedChange = { settingsPopupExpanded = it },
                            onSelected = onAudioSelected,
                        )
                        SelectablePreference(
                            title = stringResource(R.string.sample_rate_label),
                            items = sampleRateOptions,
                            selectedIndex = selectedSampleRateIndex,
                            enabled = optionsEnabled,
                            predictiveBackEnabled = enablePredictiveBack,
                            dismissRequestKey = popupDismissRequestKey,
                            onExpandedChange = { settingsPopupExpanded = it },
                            onSelected = onSampleRateSelected,
                        )
                        SwitchPreference(
                            title = stringResource(R.string.force_16_by_9_letterboxing),
                            summary = stringResource(
                                R.string.force_16_by_9_letterboxing_summary,
                            ),
                            checked = force16By9Letterboxing,
                            enabled = optionsEnabled,
                            onCheckedChange = onForce16By9LetterboxingChanged,
                        )
                        SelectablePreference(
                            title = stringResource(R.string.video_bitrate_label),
                            items = videoBitrateOptions,
                            selectedIndex = selectedVideoBitrateIndex,
                            enabled = optionsEnabled,
                            predictiveBackEnabled = enablePredictiveBack,
                            dismissRequestKey = popupDismissRequestKey,
                            onExpandedChange = { settingsPopupExpanded = it },
                            onSelected = onVideoBitrateSelected,
                        )
                        SelectablePreference(
                            title = stringResource(R.string.video_codec_label),
                            items = videoCodecOptions,
                            selectedIndex = selectedVideoCodecIndex,
                            enabled = optionsEnabled,
                            predictiveBackEnabled = enablePredictiveBack,
                            dismissRequestKey = popupDismissRequestKey,
                            onExpandedChange = { settingsPopupExpanded = it },
                            onSelected = onVideoCodecSelected,
                        )
                        SelectablePreference(
                            title = stringResource(R.string.recording_countdown_label),
                            items = countdownOptions,
                            selectedIndex = selectedCountdownIndex,
                            enabled = optionsEnabled,
                            predictiveBackEnabled = enablePredictiveBack,
                            dismissRequestKey = popupDismissRequestKey,
                            onExpandedChange = { settingsPopupExpanded = it },
                            onSelected = onCountdownSelected,
                        )
                        SelectablePreference(
                            title = stringResource(R.string.naming_pattern_label),
                            items = namingPatternOptions,
                            selectedIndex = selectedNamingPatternIndex,
                            enabled = optionsEnabled,
                            predictiveBackEnabled = enablePredictiveBack,
                            dismissRequestKey = popupDismissRequestKey,
                            onExpandedChange = { settingsPopupExpanded = it },
                            onSelected = onNamingPatternSelected,
                        )
                        SelectablePreference(
                            title = stringResource(R.string.recording_orientation_label),
                            items = orientationOptions,
                            selectedIndex = selectedOrientationIndex,
                            enabled = optionsEnabled,
                            predictiveBackEnabled = enablePredictiveBack,
                            dismissRequestKey = popupDismissRequestKey,
                            onExpandedChange = { settingsPopupExpanded = it },
                            onSelected = onOrientationSelected,
                        )
                    }
                }

                item {
                    SmallTitle(text = stringResource(R.string.section_about))
                    PreferenceCard {
                        ArrowPreference(
                            title = stringResource(R.string.about_this_project),
                            onClick = { openSubpage(SETTINGS_PAGE_ABOUT_PROJECT) },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.open_source_licenses),
                            onClick = { openSubpage(SETTINGS_PAGE_OPEN_SOURCE_LICENSES) },
                        )
                    }
                }
            }
        }

        if (showingSubpage) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = SETTINGS_DESTINATION_DIM_AMOUNT * pageTransitionProgress.value
                    }
                    .background(Color.Black)
                    .clickable(
                        interactionSource = transitionInteractionSource,
                        indication = null,
                        onClick = {},
                    ),
            )

            val subpageModifier = Modifier.graphicsLayer {
                translationX = size.width *
                    (1f - pageTransitionProgress.value)
                shape = transitionShape
                clip = pageTransitionProgress.value < 1f
            }

            when (settingsPage) {
                SETTINGS_PAGE_ABOUT_PROJECT -> AboutProjectScreen(
                    modifier = subpageModifier,
                    onBack = ::closeSubpage,
                    onViewOnGitHub = onViewOnGitHub,
                    onViewLicense = onViewLicense,
                    onVersionClick = {
                        val updatedTapCount = versionTapCount + 1
                        if (updatedTapCount >= VERSION_TAPS_REQUIRED) {
                            Toast.makeText(
                                context,
                                R.string.version_tap_thanks,
                                Toast.LENGTH_SHORT,
                            ).show()
                            versionTapCount = 0
                        } else {
                            versionTapCount = updatedTapCount
                        }
                    },
                )

                SETTINGS_PAGE_OPEN_SOURCE_LICENSES -> OpenSourceLicensesScreen(
                    modifier = subpageModifier,
                    onBack = ::closeSubpage,
                    onOpenExternalUrl = onOpenExternalUrl,
                )

                else -> Unit
            }
        }
    }

    NonPredictivePopupBackHandler(
        enabled = !enablePredictiveBack && settingsPopupExpanded,
    ) {
        settingsPopupExpanded = false
        popupDismissRequestKey++
    }
}

@Composable
private fun PreferenceCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
    ) {
        content()
    }
}

@Composable
private fun SelectablePreference(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    enabled: Boolean,
    predictiveBackEnabled: Boolean,
    dismissRequestKey: Int,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (Int) -> Unit,
) {
    if (items.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(items.indices)
    if (enabled) {
        if (predictiveBackEnabled) {
            OverlayDropdownPreference(
                title = title,
                items = items,
                selectedIndex = safeIndex,
                onExpandedChange = onExpandedChange,
                onSelectedIndexChange = onSelected,
            )
        } else {
            NonPredictiveBackDropdownPreference(
                title = title,
                items = items,
                selectedIndex = safeIndex,
                dismissRequestKey = dismissRequestKey,
                onExpandedChange = onExpandedChange,
                onSelected = onSelected,
            )
        }
    } else {
        BasicComponent(
            title = title,
            summary = items[safeIndex],
        )
    }
}

// Adapted from Miuix OverlayDropdownPreference.
// Copyright 2025, compose-miuix-ui contributors. Licensed under Apache-2.0.
// Modified to expose a dismiss request while retaining Miuix's normal exit animation.
@Composable
private fun NonPredictiveBackDropdownPreference(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    dismissRequestKey: Int,
    onExpandedChange: (Boolean) -> Unit,
    onSelected: (Int) -> Unit,
) {
    val currentOnSelected = rememberUpdatedState(onSelected)
    val entry = remember(items, selectedIndex) {
        DropdownEntry(
            items.mapIndexed { index, item ->
                DropdownItem(
                    text = item,
                    selected = index == selectedIndex,
                    onClick = { currentOnSelected.value(index) },
                )
            },
        )
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isDropdownExpanded = remember { mutableStateOf(false) }
    val isHoldDown = remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val currentHapticFeedback = rememberUpdatedState(hapticFeedback)
    val currentOnExpandedChange = rememberUpdatedState(onExpandedChange)
    val setExpanded: (Boolean) -> Unit = remember {
        { expanded ->
            if (isDropdownExpanded.value != expanded) {
                isDropdownExpanded.value = expanded
                currentOnExpandedChange.value(expanded)
            }
        }
    }

    LaunchedEffect(dismissRequestKey) {
        if (isDropdownExpanded.value) {
            setExpanded(false)
        }
    }

    val actionColor = MiuixTheme.colorScheme.onSurfaceVariantActions
    val handleClick: () -> Unit = remember {
        {
            setExpanded(!isDropdownExpanded.value)
            if (isDropdownExpanded.value) {
                isHoldDown.value = true
                currentHapticFeedback.value.performHapticFeedback(
                    HapticFeedbackType.ContextClick,
                )
            }
        }
    }

    BasicComponent(
        interactionSource = interactionSource,
        title = title,
        endActions = {
            val selectedText = entry.items.firstOrNull { it.selected }?.text
            if (!selectedText.isNullOrEmpty()) {
                Text(
                    text = selectedText,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .align(Alignment.CenterVertically)
                        .weight(1f, fill = false),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = actionColor,
                    textAlign = TextAlign.End,
                )
            }
            DropdownArrowEndAction(actionColor = actionColor)
            OverlayDropdownPopup(
                entry = entry,
                show = isDropdownExpanded.value,
                onDismiss = { setExpanded(false) },
                onDismissFinished = { isHoldDown.value = false },
                maxHeight = null,
                dropdownColors = DropdownDefaults.dropdownColors(),
                renderInRootScaffold = true,
                collapseOnSelection = true,
            )
        },
        onClick = handleClick,
        role = Role.DropdownList,
        holdDownState = isHoldDown.value,
        enabled = true,
    )
}

@Composable
private fun NonPredictivePopupBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val activity = LocalContext.current.findActivity() ?: return
    val currentOnBack by rememberUpdatedState(onBack)
    DisposableEffect(activity) {
        val callback = OnBackInvokedCallback { currentOnBack() }
        activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
            callback,
        )
        onDispose {
            activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
