// SPDX-License-Identifier: GPL-3.0-only

package com.openrecorder.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
internal fun RecorderScreen(
    recordingState: Int,
    countdownSeconds: Int?,
    actionEnabled: Boolean,
    onActionClick: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.record_title),
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
                SmallTitle(text = stringResource(R.string.section_status))
                RecordingStatusCard(
                    recordingState = recordingState,
                    countdownSeconds = countdownSeconds,
                )
            }

            item {
                RecordActionCard(
                    label = actionLabel(
                        recordingState = recordingState,
                        countdownSeconds = countdownSeconds,
                    ),
                    recording = recordingState == RecordingState.RECORDING ||
                        recordingState == RecordingState.PAUSED,
                    enabled = actionEnabled,
                    onClick = onActionClick,
                )
            }
        }
    }
}

@Composable
private fun RecordingStatusCard(
    recordingState: Int,
    countdownSeconds: Int?,
) {
    val targetStatusColor = when {
        countdownSeconds != null -> MiuixTheme.colorScheme.primary
        recordingState == RecordingState.PREPARING -> MiuixTheme.colorScheme.primary
        recordingState == RecordingState.RECORDING -> MiuixTheme.colorScheme.error
        recordingState == RecordingState.PAUSED -> MiuixTheme.colorScheme.primary
        recordingState == RecordingState.SAVING -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val statusColor by animateColorAsState(
        targetValue = targetStatusColor,
        animationSpec = tween(durationMillis = 220, easing = EaseInOut),
        label = "recordingStatusColor",
    )
    val presentation = RecordingPresentation(recordingState, countdownSeconds)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        colors = CardDefaults.defaultColors(
            color = statusColor.copy(alpha = 0.10f),
            contentColor = MiuixTheme.colorScheme.onSurface,
        ),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Spacer(Modifier.width(14.dp))
            AnimatedContent(
                targetState = presentation,
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
                transitionSpec = {
                    fadeIn(tween(durationMillis = 180, delayMillis = 40)) togetherWith
                        fadeOut(tween(durationMillis = 120))
                },
                label = "recordingStatusText",
            ) { state ->
                Column {
                    Text(
                        text = statusTitle(state.recordingState, state.countdownSeconds),
                        style = MiuixTheme.textStyles.main,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = statusSummary(state.recordingState, state.countdownSeconds),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordActionCard(
    label: String,
    recording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val targetBackgroundColor = when {
        !enabled -> MiuixTheme.colorScheme.secondaryContainer
        recording -> MiuixTheme.colorScheme.error
        else -> MiuixTheme.colorScheme.primary
    }
    val targetContentColor = when {
        !enabled -> MiuixTheme.colorScheme.disabledOnSecondaryVariant
        recording -> Color.White
        else -> MiuixTheme.colorScheme.onPrimary
    }
    val backgroundColor by animateColorAsState(
        targetValue = targetBackgroundColor,
        animationSpec = tween(durationMillis = 220, easing = EaseInOut),
        label = "recordActionBackgroundColor",
    )
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = tween(durationMillis = 220, easing = EaseInOut),
        label = "recordActionContentColor",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.defaultColors(
            color = backgroundColor,
            contentColor = contentColor,
        ),
        insideMargin = PaddingValues(0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = label,
                transitionSpec = {
                    fadeIn(tween(durationMillis = 180, delayMillis = 40)) togetherWith
                        fadeOut(tween(durationMillis = 120))
                },
                label = "recordActionLabel",
            ) { currentLabel ->
                Text(
                    text = currentLabel,
                    style = MiuixTheme.textStyles.main,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
            }
        }
    }
}

@Immutable
private data class RecordingPresentation(
    val recordingState: Int,
    val countdownSeconds: Int?,
)

@Composable
private fun statusTitle(recordingState: Int, countdownSeconds: Int?): String = when {
    countdownSeconds != null -> stringResource(R.string.status_countdown, countdownSeconds)
    recordingState == RecordingState.PREPARING -> stringResource(R.string.status_preparing)
    recordingState == RecordingState.RECORDING -> stringResource(R.string.status_recording)
    recordingState == RecordingState.PAUSED -> stringResource(R.string.status_paused)
    recordingState == RecordingState.SAVING -> stringResource(R.string.status_saving)
    else -> stringResource(R.string.status_idle)
}

@Composable
private fun statusSummary(recordingState: Int, countdownSeconds: Int?): String = when {
    countdownSeconds != null -> stringResource(R.string.status_countdown_summary)
    recordingState == RecordingState.PREPARING -> stringResource(R.string.status_preparing_summary)
    recordingState == RecordingState.RECORDING -> stringResource(R.string.status_recording_summary)
    recordingState == RecordingState.PAUSED -> stringResource(R.string.status_paused_summary)
    recordingState == RecordingState.SAVING -> stringResource(R.string.status_saving_summary)
    else -> stringResource(R.string.status_idle_summary)
}

@Composable
private fun actionLabel(recordingState: Int, countdownSeconds: Int?): String = when {
    countdownSeconds != null || recordingState == RecordingState.PREPARING ->
        stringResource(R.string.cancel_recording)
    recordingState == RecordingState.RECORDING || recordingState == RecordingState.PAUSED ->
        stringResource(R.string.stop_recording)
    recordingState == RecordingState.SAVING -> stringResource(R.string.status_saving)
    else -> stringResource(R.string.start_recording)
}
