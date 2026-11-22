// SPDX-License-Identifier: GPL-3.0-only
// Pager transition logic adapted from AsteriskNG 1.4.2.
// Copyright 2026, AsteriskNG contributors

package com.openrecorder.app

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs

private const val RECORD_PAGE = 0
private const val SETTINGS_PAGE = 1
private const val OTHERS_PAGE = 2
private const val RECORDINGS_PAGE = 3
private const val PAGE_COUNT = 4
private val FloatingBarHeight = 64.dp
private val FloatingBarBottomSpacing = 12.dp

// Reserve space at the end of each list, while keeping its viewport behind the floating bar.
internal val LocalFloatingNavigationBottomPadding = staticCompositionLocalOf { 0.dp }

@Composable
internal fun RecorderApp(
    floatingBottomBar: Boolean,
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
    val othersLabel = stringResource(R.string.nav_others)
    val recordingsLabel = stringResource(R.string.nav_recordings)
    val navigationItems = remember(recordLabel, settingsLabel, othersLabel, recordingsLabel) {
        listOf(
            NavigationItem(recordLabel, RecorderIcons.Play),
            NavigationItem(settingsLabel, RecorderIcons.Settings),
            NavigationItem(othersLabel, RecorderIcons.Others),
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
            if (!floatingBottomBar) {
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
            }
        },
    ) { innerPadding ->
        val floatingContentPadding = if (floatingBottomBar) {
            FloatingBarHeight + FloatingBarBottomSpacing +
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        } else {
            0.dp
        }
        CompositionLocalProvider(
            LocalFloatingNavigationBottomPadding provides floatingContentPadding,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = recorderPagerState.pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            bottom = if (floatingBottomBar) 0.dp else innerPadding.calculateBottomPadding(),
                        ),
                    beyondViewportPageCount = 0,
                    userScrollEnabled = false,
                    verticalAlignment = Alignment.Top,
                    key = { page -> page },
                ) { page ->
                    when (page) {
                        RECORD_PAGE -> recordContent()
                        SETTINGS_PAGE -> settingsContent()
                        OTHERS_PAGE -> OthersScreen()
                        else -> recordingsContent()
                    }
                }
                if (floatingBottomBar) {
                    RecorderFloatingNavigationBar(
                        items = navigationItems,
                        selectedIndex = recorderPagerState.selectedPage,
                        onSelected = recorderPagerState::animateToPage,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(
                                WindowInsets.navigationBars.only(
                                    WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                                ),
                            )
                            .padding(horizontal = 36.dp, vertical = FloatingBarBottomSpacing),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecorderFloatingNavigationBar(
    items: List<NavigationItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    val colors = MiuixTheme.colorScheme
    BoxWithConstraints(
        modifier = modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .height(FloatingBarHeight)
            .shadow(elevation = 3.dp, shape = shape)
            .clip(shape)
            .background(colors.surfaceContainer)
            .pointerInput(Unit) { detectTapGestures { /* Consume taps inside the capsule. */ } },
    ) {
        val itemWidth = (maxWidth - 8.dp) / items.size
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
            label = "floatingNavigationIndicator",
        )
        Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(colors.primary.copy(alpha = 0.16f)),
            )
            Row(modifier = Modifier.fillMaxSize().selectableGroup()) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val pressScale by animateFloatAsState(
                        targetValue = if (pressed) 0.94f else 1f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
                        label = "floatingNavigationPress",
                    )
                    val contentColor = if (selected) {
                        lerp(colors.onSurfaceContainer, colors.primary, 0.18f)
                    } else {
                        colors.onSurfaceContainer
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .graphicsLayer {
                                scaleX = pressScale
                                scaleY = pressScale
                            }
                            .selectable(
                                selected = selected,
                                onClick = { onSelected(index) },
                                role = Role.Tab,
                                interactionSource = interactionSource,
                                indication = null,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = contentColor,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = contentColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
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
