// SPDX-License-Identifier: GPL-3.0-only

package com.openrecorder.app

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.text.DateFormat
import java.util.Date

private const val SORT_BY_DATE = 0
private const val SORT_BY_SIZE = 1
private const val SORT_BY_DURATION = 2

@Composable
internal fun RecordingsScreen(
    recordings: List<RecordingItem>,
    loading: Boolean,
    loadFailed: Boolean,
    deleting: Boolean,
    onOpenRecording: (RecordingItem) -> Unit,
    onDeleteRecordings: (List<RecordingItem>) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var sortOption by rememberSaveable { mutableIntStateOf(SORT_BY_DATE) }
    val busy = loading || deleting
    val selectionMode = selectedIds.isNotEmpty()
    val selectedRecordings = remember(recordings, selectedIds) {
        recordings.filter { it.id in selectedIds }
    }
    val allRecordingIds = remember(recordings) {
        recordings.map(RecordingItem::id).toSet()
    }
    val allRecordingsSelected = allRecordingIds.isNotEmpty() &&
        selectedIds.containsAll(allRecordingIds)
    val sortedRecordings = remember(recordings, sortOption) {
        when (sortOption) {
            SORT_BY_SIZE -> recordings.sortedWith(
                compareByDescending<RecordingItem> { it.sizeBytes }
                    .thenByDescending { it.dateAddedSeconds }
                    .thenByDescending { it.id },
            )

            SORT_BY_DURATION -> recordings.sortedWith(
                compareByDescending<RecordingItem> { it.durationMillis }
                    .thenByDescending { it.dateAddedSeconds }
                    .thenByDescending { it.id },
            )

            else -> recordings.sortedWith(
                compareByDescending<RecordingItem> { it.dateAddedSeconds }
                    .thenByDescending { it.id },
            )
        }
    }
    val sortByLabel = stringResource(R.string.recordings_sort_by)
    val sortEntry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = stringResource(R.string.recordings_sort_date),
                selected = sortOption == SORT_BY_DATE,
                onClick = { sortOption = SORT_BY_DATE },
            ),
            DropdownItem(
                text = stringResource(R.string.recordings_sort_size),
                selected = sortOption == SORT_BY_SIZE,
                onClick = { sortOption = SORT_BY_SIZE },
            ),
            DropdownItem(
                text = stringResource(R.string.recordings_sort_duration),
                selected = sortOption == SORT_BY_DURATION,
                onClick = { sortOption = SORT_BY_DURATION },
            ),
        ),
    )

    LaunchedEffect(recordings) {
        val availableIds = recordings.mapTo(mutableSetOf(), RecordingItem::id)
        selectedIds = selectedIds.intersect(availableIds)
    }

    if (selectionMode && !deleting) {
        BackHandler {
            selectedIds = emptySet()
        }
    }

    fun toggleSelection(recording: RecordingItem) {
        selectedIds = if (recording.id in selectedIds) {
            selectedIds - recording.id
        } else {
            selectedIds + recording.id
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = if (selectionMode) {
                    stringResource(R.string.recordings_selected_count, selectedIds.size)
                } else {
                    stringResource(R.string.recordings_title)
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (selectionMode) {
                        IconButton(
                            enabled = !busy,
                            onClick = {
                                if (allRecordingsSelected) {
                                    selectedIds = emptySet()
                                } else {
                                    selectedIds = allRecordingIds
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (allRecordingsSelected) {
                                    RecorderIcons.AllSelected
                                } else {
                                    RecorderIcons.SelectAll
                                },
                                contentDescription = if (allRecordingsSelected) {
                                    stringResource(R.string.clear_recording_selection)
                                } else {
                                    stringResource(R.string.select_all_recordings)
                                },
                                tint = if (allRecordingsSelected) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onSurface
                                },
                            )
                        }
                        IconButton(
                            enabled = !busy && selectedRecordings.isNotEmpty(),
                            onClick = { onDeleteRecordings(selectedRecordings) },
                        ) {
                            if (deleting) {
                                InfiniteProgressIndicator(
                                    color = MiuixTheme.colorScheme.primary,
                                    size = 20.dp,
                                )
                            } else {
                                Icon(
                                    imageVector = RecorderIcons.Delete,
                                    contentDescription = stringResource(
                                        R.string.delete_selected_recordings,
                                    ),
                                    tint = MiuixTheme.colorScheme.error,
                                )
                            }
                        }
                        IconButton(
                            enabled = !deleting,
                            onClick = { selectedIds = emptySet() },
                        ) {
                            Icon(
                                imageVector = RecorderIcons.Close,
                                contentDescription = stringResource(
                                    R.string.clear_recording_selection,
                                ),
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    } else {
                        OverlayIconDropdownMenu(
                            entry = sortEntry,
                            enabled = !busy,
                        ) {
                            Icon(
                                imageVector = RecorderIcons.Sort,
                                contentDescription = sortByLabel,
                                tint = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
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
                bottom = 24.dp,
            ),
        ) {
            when {
                loading && recordings.isEmpty() -> item(contentType = "loading") {
                    RecordingsLoadingState(modifier = Modifier.fillParentMaxSize())
                }

                loadFailed && recordings.isEmpty() -> item(contentType = "error") {
                    RecordingsMessageState(
                        title = stringResource(R.string.recordings_load_error),
                        summary = stringResource(R.string.recordings_load_error_summary),
                        modifier = Modifier.fillParentMaxSize(),
                    )
                }

                recordings.isEmpty() -> item(contentType = "empty") {
                    RecordingsMessageState(
                        title = stringResource(R.string.recordings_empty),
                        summary = stringResource(R.string.recordings_empty_summary),
                        modifier = Modifier.fillParentMaxSize(),
                    )
                }

                else -> {
                    item(contentType = "title") {
                        SmallTitle(
                            text = stringResource(R.string.recordings_count, recordings.size),
                        )
                    }
                    items(
                        items = sortedRecordings,
                        key = RecordingItem::id,
                        contentType = { "recording" },
                    ) { recording ->
                        RecordingCard(
                            modifier = Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = folmeSpring(
                                    damping = 0.9f,
                                    response = 0.38f,
                                ),
                            ),
                            recording = recording,
                            selected = recording.id in selectedIds,
                            selectionMode = selectionMode,
                            enabled = !busy,
                            onClick = {
                                if (selectionMode) {
                                    toggleSelection(recording)
                                } else {
                                    onOpenRecording(recording)
                                }
                            },
                            onLongClick = { toggleSelection(recording) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingCard(
    modifier: Modifier = Modifier,
    recording: RecordingItem,
    selected: Boolean,
    selectionMode: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val selectLabel = stringResource(R.string.select_recording)
    val openLabel = stringResource(R.string.open_recording)
    val selectedDescription = stringResource(R.string.recording_selected)
    val details = remember(context, recording) {
        val size = Formatter.formatShortFileSize(context, recording.sizeBytes)
        val duration = DateUtils.formatElapsedTime(recording.durationMillis / 1_000L)
        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(recording.dateAddedSeconds * 1_000L))
        "$size · $duration · $date"
    }
    val selectionBackgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 180, easing = EaseInOut),
        label = "recordingSelectionBackgroundColor",
    )
    val iconBackgroundColor by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
        },
        animationSpec = tween(durationMillis = 180, easing = EaseInOut),
        label = "recordingSelectionIconBackgroundColor",
    )
    val iconContentColor by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.onPrimary
        } else {
            MiuixTheme.colorScheme.primary
        },
        animationSpec = tween(durationMillis = 180, easing = EaseInOut),
        label = "recordingSelectionIconContentColor",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 10.dp),
        insideMargin = PaddingValues(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { this.selected = selected }
                .background(selectionBackgroundColor)
                .combinedClickable(
                    enabled = enabled,
                    role = if (selectionMode) Role.Checkbox else Role.Button,
                    onClickLabel = if (selectionMode) selectLabel else openLabel,
                    onLongClickLabel = selectLabel,
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(iconBackgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (selected) RecorderIcons.Check else RecorderIcons.Recordings,
                    contentDescription = if (selected) selectedDescription else null,
                    modifier = Modifier.size(24.dp),
                    tint = iconContentColor,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.name.ifEmpty {
                        context.getString(R.string.recording_untitled)
                    },
                    style = MiuixTheme.textStyles.main,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = details,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RecordingsLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        InfiniteProgressIndicator(
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            size = 28.dp,
            strokeWidth = 2.dp,
            orbitingDotSize = 2.dp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.recordings_loading),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun RecordingsMessageState(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = RecorderIcons.Recordings,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = MiuixTheme.textStyles.main,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = summary,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
