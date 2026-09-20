// SPDX-License-Identifier: GPL-3.0-only
// Pager transition logic adapted from AsteriskNG 1.4.2.
// Copyright 2026, AsteriskNG contributors

package com.openrecorder.app

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

private const val RECORD_PAGE = 0
private const val SETTINGS_PAGE = 1
private const val RECORDINGS_PAGE = 2
private const val PAGE_COUNT = 3

@Composable
internal fun RecorderApp(
    onRecordingsVisible: () -> Unit,
    recordContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
    recordingsContent: @Composable () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = RECORD_PAGE,
        pageCount = { PAGE_COUNT },
    )
    val recorderPagerState = rememberRecorderPagerState(pagerState)
    val recordLabel = stringResource(R.string.nav_record)
    val settingsLabel = stringResource(R.string.nav_settings)
    val recordingsLabel = stringResource(R.string.nav_recordings)
    val navigationItems = remember(recordLabel, settingsLabel, recordingsLabel) {
        listOf(
            NavigationItem(recordLabel, RecorderIcons.Play),
            NavigationItem(settingsLabel, RecorderIcons.Settings),
            NavigationItem(recordingsLabel, RecorderIcons.Recordings),
        )
    }

    LaunchedEffect(recorderPagerState.pagerState.currentPage) {
        recorderPagerState.syncPage()
    }

    LaunchedEffect(recorderPagerState.selectedPage) {
        if (recorderPagerState.selectedPage == RECORDINGS_PAGE) {
            onRecordingsVisible()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Box(
                modifier = Modifier
                    .background(MiuixTheme.colorScheme.surface)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                NavigationBar(mode = NavigationBarDisplayMode.IconAndText) {
                    navigationItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = recorderPagerState.selectedPage == index,
                            onClick = { recorderPagerState.animateToPage(index) },
                            icon = item.icon,
                            label = item.label,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        HorizontalPager(
            state = recorderPagerState.pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
            beyondViewportPageCount = 0,
            userScrollEnabled = false,
            verticalAlignment = Alignment.Top,
            key = { page -> page },
        ) { page ->
            when (page) {
                RECORD_PAGE -> recordContent()
                SETTINGS_PAGE -> settingsContent()
                else -> recordingsContent()
            }
        }
    }
}

@Stable
private class RecorderPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    private var isNavigating by mutableStateOf(false)
    private var navigationJob: Job? = null

    fun animateToPage(targetPage: Int) {
        if (targetPage !in 0 until PAGE_COUNT || targetPage == selectedPage) return

        navigationJob?.cancel()
        selectedPage = targetPage
        isNavigating = true

        navigationJob = coroutineScope.launch {
            val currentJob = coroutineContext.job
            try {
                pagerState.scroll(MutatePriority.UserInput) {
                    val pageDistance = abs(targetPage - pagerState.currentPage).coerceAtLeast(2)
                    val durationMillis = 100 * pageDistance + 100
                    val pageSize = pagerState.layoutInfo.let { it.pageSize + it.pageSpacing }
                    val remainingPages = targetPage -
                        pagerState.currentPage -
                        pagerState.currentPageOffsetFraction
                    val scrollDistance = remainingPages * pageSize
                    var previousValue = 0f

                    animate(
                        initialValue = 0f,
                        targetValue = scrollDistance,
                        animationSpec = tween(
                            durationMillis = durationMillis,
                            easing = EaseInOut,
                        ),
                    ) { currentValue, _ ->
                        previousValue += scrollBy(currentValue - previousValue)
                    }
                }

                if (pagerState.currentPage != targetPage) {
                    pagerState.scrollToPage(targetPage)
                }
            } finally {
                if (navigationJob == currentJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetPage) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
private fun rememberRecorderPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): RecorderPagerState = remember(pagerState, coroutineScope) {
    RecorderPagerState(pagerState, coroutineScope)
}
