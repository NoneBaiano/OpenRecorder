// SPDX-License-Identifier: GPL-3.0-only

package com.openrecorder.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val projectionManager by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(MediaProjectionManager::class.java)
    }
    private lateinit var recorderPreferences: RecorderPreferences
    private val recordingRepository by lazy(LazyThreadSafetyMode.NONE) {
        RecordingRepository(applicationContext)
    }
    private var recordingsExecutor: ExecutorService? = null
    private var countDownTimer: CountDownTimer? = null
    private var stateListenerRegistered = false
    private var recordingsLoadGeneration = 0
    private var recordingsLoaded = false
    private var recordingsStale = true
    private var reloadRecordingsAfterCurrentLoad = false
    private var notificationPermissionRequested = false
    private var pendingDeleteCount = 0
    private lateinit var appUiState: MutableState<AppUiState>
    private lateinit var recordScreenUiState: MutableState<RecordScreenUiState>
    private lateinit var recordingSettingsUiState: MutableState<RecordingSettingsUiState>
    private lateinit var recordingsUiState: MutableState<RecordingsUiState>

    private val stateListener = RecordingState.Listener { newState ->
        val previousState = recordScreenUiState.value.recordingState
        updateRecordScreenUiState { it.copy(recordingState = newState) }

        if (newState == RecordingState.RECORDING || newState == RecordingState.IDLE) {
            cancelCountdownUi()
        }

        if (previousState == RecordingState.SAVING && newState == RecordingState.IDLE) {
            recordingsStale = true
            if (recordingsLoaded && stateListenerRegistered) {
                loadRecordings(force = true)
            }
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            ensureNotificationPermissionAndRequestCapture()
        } else {
            showToast(R.string.audio_permission_denied, Toast.LENGTH_LONG)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        requestCapturePermission()
    }

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val projectionData = result.data
        if (result.resultCode != RESULT_OK || projectionData == null) {
            showToast(R.string.permission_denied, Toast.LENGTH_SHORT)
            return@registerForActivityResult
        }
        scheduleRecording(result.resultCode, projectionData)
    }

    private val deleteRecordingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val deletedCount = pendingDeleteCount
        pendingDeleteCount = 0
        if (!::recordingsUiState.isInitialized) return@registerForActivityResult

        updateRecordingsUiState { it.copy(deleting = false) }
        if (result.resultCode == RESULT_OK) {
            recordingsStale = true
            if (deletedCount > 0) {
                showDeletedToast(deletedCount)
            }
            loadRecordings(force = true)
        } else {
            showToast(R.string.recordings_delete_canceled, Toast.LENGTH_SHORT)
            loadRecordings(force = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recorderPreferences = RecorderPreferences(this)
        appUiState = mutableStateOf(
            AppUiState(
                selectedThemeMode = recorderPreferences.loadThemeMode(),
                enablePredictiveBack = recorderPreferences.loadEnablePredictiveBack(),
            ),
        )
        recordScreenUiState = mutableStateOf(
            RecordScreenUiState(recordingState = RecordingState.get()),
        )
        recordingSettingsUiState = mutableStateOf(
            RecordingSettingsUiState(
                selectedAudioSource = recorderPreferences.loadAudioSource(),
                selectedSampleRate = recorderPreferences.loadSampleRate(),
                selectedVideoResolution = recorderPreferences.loadVideoResolution(),
                selectedVideoFrameRate = recorderPreferences.loadVideoFrameRate(),
                force16By9Letterboxing = recorderPreferences.loadForce16By9Letterboxing(),
                selectedVideoBitrate = recorderPreferences.loadVideoBitrate(),
                selectedVideoCodec = recorderPreferences.loadVideoCodec(),
                selectedCountdownSeconds = recorderPreferences.loadCountdownSeconds(),
                selectedOrientation = recorderPreferences.loadOrientation(),
                selectedNamingPattern = recorderPreferences.loadNamingPattern(),
            ),
        )
        recordingsUiState = mutableStateOf(RecordingsUiState())

        enableEdgeToEdge()
        setContent {
            val appState = appUiState.value
            val recordingSessionActive by remember {
                derivedStateOf {
                    val recordState = recordScreenUiState.value
                    recordState.recordingState != RecordingState.IDLE ||
                        recordState.countdownSeconds != null
                }
            }
            val systemDark = isSystemInDarkTheme()
            BackHandler(enabled = !appState.enablePredictiveBack) {
                if (recordingSessionActive) {
                    moveTaskToBack(true)
                } else {
                    finish()
                }
            }
            RecorderTheme(
                themeMode = appState.selectedThemeMode,
                systemDark = systemDark,
            ) {
                RecorderApp(
                    onRecordingsVisible = { loadRecordings() },
                    recordContent = {
                        val recordState = recordScreenUiState.value
                        RecorderScreen(
                            recordingState = recordState.recordingState,
                            countdownSeconds = recordState.countdownSeconds,
                            actionEnabled = recordState.recordingState != RecordingState.SAVING,
                            onActionClick = ::onRecordButtonClicked,
                        )
                    },
                    settingsContent = {
                        val settingsState = recordingSettingsUiState.value
                        val recordState = recordScreenUiState.value
                        val optionsEnabled = recordState.recordingState == RecordingState.IDLE &&
                            recordState.countdownSeconds == null
                        SettingsScreen(
                            selectedThemeIndex = appState.selectedThemeMode,
                            enablePredictiveBack = appState.enablePredictiveBack,
                            selectedAudioIndex = AUDIO_SOURCES
                                .indexOf(settingsState.selectedAudioSource)
                                .coerceAtLeast(0),
                            selectedSampleRateIndex = if (
                                settingsState.selectedSampleRate == RecordingOptions.SAMPLE_RATE_48_KHZ
                            ) 1 else 0,
                            selectedVideoResolutionIndex = VIDEO_RESOLUTIONS
                                .indexOf(settingsState.selectedVideoResolution)
                                .coerceAtLeast(0),
                            selectedVideoFrameRateIndex = VIDEO_FRAME_RATES
                                .indexOf(settingsState.selectedVideoFrameRate)
                                .coerceAtLeast(0),
                            force16By9Letterboxing = settingsState.force16By9Letterboxing,
                            selectedVideoBitrateIndex = VIDEO_BITRATES
                                .indexOf(settingsState.selectedVideoBitrate)
                                .coerceAtLeast(0),
                            selectedVideoCodecIndex = settingsState.selectedVideoCodec,
                            selectedCountdownIndex = COUNTDOWN_OPTIONS
                                .indexOf(settingsState.selectedCountdownSeconds)
                                .coerceAtLeast(0),
                            selectedNamingPatternIndex = NAMING_PATTERNS
                                .indexOf(settingsState.selectedNamingPattern)
                                .coerceAtLeast(0),
                            selectedOrientationIndex = settingsState.selectedOrientation,
                            optionsEnabled = optionsEnabled,
                            onThemeSelected = { index ->
                                val themeMode = ThemeMode.normalize(index)
                                updateAppUiState { it.copy(selectedThemeMode = themeMode) }
                                recorderPreferences.saveThemeMode(themeMode)
                            },
                            onEnablePredictiveBackChanged = { enabled ->
                                updateAppUiState { it.copy(enablePredictiveBack = enabled) }
                                recorderPreferences.saveEnablePredictiveBack(enabled)
                            },
                            onAudioSelected = { index ->
                                if (optionsEnabled) {
                                    val audioSource = AUDIO_SOURCES.getOrElse(index) {
                                        AudioSource.NONE
                                    }
                                    updateRecordingSettingsUiState {
                                        it.copy(selectedAudioSource = audioSource)
                                    }
                                    recorderPreferences.saveAudioSource(audioSource)
                                }
                            },
                            onSampleRateSelected = { index ->
                                if (optionsEnabled) {
                                    val sampleRate = if (index == 1) {
                                        RecordingOptions.SAMPLE_RATE_48_KHZ
                                    } else {
                                        RecordingOptions.SAMPLE_RATE_44_1_KHZ
                                    }
                                    updateRecordingSettingsUiState {
                                        it.copy(selectedSampleRate = sampleRate)
                                    }
                                    recorderPreferences.saveSampleRate(sampleRate)
                                }
                            },
                            onVideoResolutionSelected = { index ->
                                if (optionsEnabled) {
                                    val videoResolution = VIDEO_RESOLUTIONS.getOrElse(index) {
                                        RecordingOptions.DEFAULT_VIDEO_RESOLUTION
                                    }
                                    updateRecordingSettingsUiState {
                                        it.copy(selectedVideoResolution = videoResolution)
                                    }
                                    recorderPreferences.saveVideoResolution(videoResolution)
                                }
                            },
                            onVideoFrameRateSelected = { index ->
                                if (optionsEnabled) {
                                    val videoFrameRate = VIDEO_FRAME_RATES.getOrElse(index) {
                                        RecordingOptions.DEFAULT_VIDEO_FRAME_RATE
                                    }
                                    updateRecordingSettingsUiState {
                                        it.copy(selectedVideoFrameRate = videoFrameRate)
                                    }
                                    recorderPreferences.saveVideoFrameRate(videoFrameRate)
                                }
                            },
                            onForce16By9LetterboxingChanged = { forceLetterboxing ->
                                if (optionsEnabled) {
                                    updateRecordingSettingsUiState {
                                        it.copy(force16By9Letterboxing = forceLetterboxing)
                                    }
                                    recorderPreferences.saveForce16By9Letterboxing(
                                        forceLetterboxing,
                                    )
                                }
                            },
                            onVideoBitrateSelected = { index ->
                                if (optionsEnabled) {
                                    val videoBitrate = VIDEO_BITRATES.getOrElse(index) {
                                        RecordingOptions.DEFAULT_VIDEO_BITRATE
                                    }
                                    updateRecordingSettingsUiState {
                                        it.copy(selectedVideoBitrate = videoBitrate)
                                    }
                                    recorderPreferences.saveVideoBitrate(videoBitrate)
                                }
                            },
                            onVideoCodecSelected = { index ->
                                if (optionsEnabled) {
                                    val videoCodec = RecordingOptions.normalizeVideoCodec(index)
                                    if (ScreenRecorder.isVideoCodecSupported(videoCodec)) {
                                        updateRecordingSettingsUiState {
                                            it.copy(selectedVideoCodec = videoCodec)
                                        }
                                        recorderPreferences.saveVideoCodec(videoCodec)
                                    } else {
                                        showUnsupportedVideoCodec(videoCodec)
                                    }
                                }
                            },
                            onCountdownSelected = { index ->
                                if (optionsEnabled) {
                                    val countdownSeconds = COUNTDOWN_OPTIONS.getOrElse(index) {
                                        RecordingOptions.DEFAULT_COUNTDOWN_SECONDS
                                    }
                                    updateRecordingSettingsUiState {
                                        it.copy(selectedCountdownSeconds = countdownSeconds)
                                    }
                                    recorderPreferences.saveCountdownSeconds(countdownSeconds)
                                }
                            },
                            onNamingPatternSelected = { index ->
                                if (optionsEnabled) {
                                    val namingPattern = NAMING_PATTERNS.getOrElse(index) {
                                        RecordingOptions.DEFAULT_NAMING_PATTERN
                                    }
                                    updateRecordingSettingsUiState {
                                        it.copy(selectedNamingPattern = namingPattern)
                                    }
                                    recorderPreferences.saveNamingPattern(namingPattern)
                                }
                            },
                            onOrientationSelected = { index ->
                                if (optionsEnabled) {
                                    val orientation = RecordingOptions.normalizeOrientation(index)
                                    updateRecordingSettingsUiState {
                                        it.copy(selectedOrientation = orientation)
                                    }
                                    recorderPreferences.saveOrientation(orientation)
                                }
                            },
                            onViewOnGitHub = ::viewOnGitHub,
                            onViewLicense = ::viewLicense,
                            onOpenExternalUrl = ::viewOpenSourceLicense,
                        )
                    },
                    recordingsContent = {
                        val state = recordingsUiState.value
                        RecordingsScreen(
                            recordings = state.recordings,
                            loading = state.loading,
                            loadFailed = state.loadFailed,
                            deleting = state.deleting,
                            onOpenRecording = ::openRecording,
                            onDeleteRecordings = ::deleteRecordings,
                        )
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (!stateListenerRegistered) {
            RecordingState.addListener(stateListener)
            stateListenerRegistered = true
        }
        updateRecordScreenUiState { it.copy(recordingState = RecordingState.get()) }
        if (recordingsLoaded && recordingsStale && !recordingsUiState.value.deleting) {
            loadRecordings(force = true)
        }
    }

    override fun onStop() {
        if (stateListenerRegistered) {
            RecordingState.removeListener(stateListener)
            stateListenerRegistered = false
        }
        if (recordingsLoaded) {
            recordingsStale = true
        }
        super.onStop()
    }

    override fun onDestroy() {
        cancelCountdownUi()
        recordingsExecutor?.shutdownNow()
        recordingsExecutor = null
        super.onDestroy()
    }

    private fun loadRecordings(force: Boolean = false) {
        if (recordingsUiState.value.deleting) {
            recordingsStale = true
            return
        }
        if (!force && recordingsLoaded && !recordingsStale) return
        if (recordingsUiState.value.loading) {
            reloadRecordingsAfterCurrentLoad = reloadRecordingsAfterCurrentLoad || force
            return
        }

        val executor = recordingsExecutor ?: Executors.newSingleThreadExecutor().also {
            recordingsExecutor = it
        }
        if (executor.isShutdown) return

        val generation = ++recordingsLoadGeneration
        updateRecordingsUiState {
            it.copy(
                loading = true,
                loadFailed = false,
            )
        }
        executor.execute {
            val result = runCatching(recordingRepository::loadRecordings)
            runOnUiThread {
                if (generation != recordingsLoadGeneration || isDestroyed) {
                    return@runOnUiThread
                }

                result.onSuccess { loadedRecordings ->
                    updateRecordingsUiState {
                        it.copy(
                            recordings = loadedRecordings,
                            loading = false,
                            loadFailed = false,
                        )
                    }
                    recordingsLoaded = true
                    recordingsStale = !stateListenerRegistered
                }.onFailure {
                    updateRecordingsUiState {
                        it.copy(
                            loading = false,
                            loadFailed = true,
                        )
                    }
                    recordingsStale = true
                }

                if (reloadRecordingsAfterCurrentLoad) {
                    reloadRecordingsAfterCurrentLoad = false
                    loadRecordings(force = true)
                }
            }
        }
    }

    private fun openRecording(recording: RecordingItem) {
        val viewIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(recording.uri, VIDEO_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(viewIntent)
        } catch (_: ActivityNotFoundException) {
            showToast(R.string.recording_open_failed, Toast.LENGTH_LONG)
        } catch (_: SecurityException) {
            showToast(R.string.recording_unavailable, Toast.LENGTH_LONG)
        }
    }

    private fun viewOnGitHub() {
        openExternalUrl(R.string.github_url, R.string.github_open_failed)
    }

    private fun viewLicense() {
        openExternalUrl(R.string.license_url, R.string.license_open_failed)
    }

    private fun viewOpenSourceLicense(url: String) {
        openExternalUrl(url, R.string.license_open_failed)
    }

    private fun openExternalUrl(urlRes: Int, errorMessageRes: Int) {
        openExternalUrl(getString(urlRes), errorMessageRes)
    }

    private fun openExternalUrl(url: String, errorMessageRes: Int) {
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(viewIntent)
        } catch (_: ActivityNotFoundException) {
            showToast(errorMessageRes, Toast.LENGTH_LONG)
        } catch (_: SecurityException) {
            showToast(errorMessageRes, Toast.LENGTH_LONG)
        }
    }

    private fun deleteRecordings(selectedRecordings: List<RecordingItem>) {
        if (recordingsUiState.value.deleting) return
        val recordingsToDelete = selectedRecordings.distinctBy(RecordingItem::id)
        if (recordingsToDelete.isEmpty()) return

        pendingDeleteCount = recordingsToDelete.size
        updateRecordingsUiState { it.copy(deleting = true) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestSystemDelete(recordingsToDelete)
        } else {
            deleteOwnedRecordings(recordingsToDelete)
        }
    }

    private fun requestSystemDelete(recordingsToDelete: List<RecordingItem>) {
        val deleteRequest = try {
            MediaStore.createDeleteRequest(
                contentResolver,
                recordingsToDelete.map(RecordingItem::uri),
            )
        } catch (_: RuntimeException) {
            finishDeleteWithError()
            return
        }

        try {
            deleteRecordingsLauncher.launch(
                IntentSenderRequest.Builder(deleteRequest.intentSender).build(),
            )
        } catch (_: RuntimeException) {
            finishDeleteWithError()
        }
    }

    private fun deleteOwnedRecordings(recordingsToDelete: List<RecordingItem>) {
        val executor = recordingsExecutor ?: Executors.newSingleThreadExecutor().also {
            recordingsExecutor = it
        }
        if (executor.isShutdown) {
            finishDeleteWithError()
            return
        }

        executor.execute {
            val result = recordingRepository.deleteRecordings(recordingsToDelete)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread

                pendingDeleteCount = 0
                updateRecordingsUiState { it.copy(deleting = false) }
                recordingsStale = true
                when {
                    result.failedCount == 0 -> showDeletedToast(result.deletedCount)
                    result.deletedCount > 0 -> showToast(
                        getString(
                            R.string.recordings_delete_partial,
                            result.deletedCount,
                            result.failedCount,
                        ),
                        Toast.LENGTH_LONG,
                    )

                    else -> showToast(R.string.recordings_delete_failed, Toast.LENGTH_LONG)
                }
                loadRecordings(force = true)
            }
        }
    }

    private fun finishDeleteWithError() {
        pendingDeleteCount = 0
        updateRecordingsUiState { it.copy(deleting = false) }
        showToast(R.string.recordings_delete_failed, Toast.LENGTH_LONG)
    }

    private fun showDeletedToast(deletedCount: Int) {
        if (deletedCount <= 0) return
        showToast(
            resources.getQuantityString(
                R.plurals.recordings_deleted,
                deletedCount,
                deletedCount,
            ),
            Toast.LENGTH_SHORT,
        )
    }

    private fun onRecordButtonClicked() {
        val state = recordScreenUiState.value
        when {
            state.countdownSeconds != null || state.recordingState == RecordingState.PREPARING -> {
                cancelCountdownUi()
                stopRecordingService()
            }

            state.recordingState == RecordingState.RECORDING ||
                state.recordingState == RecordingState.PAUSED -> {
                stopRecordingService()
            }

            state.recordingState == RecordingState.IDLE -> {
                ensureAudioPermissionAndRequestCapture()
            }
        }
    }

    private fun ensureAudioPermissionAndRequestCapture() {
        val state = recordingSettingsUiState.value
        if (!ScreenRecorder.isVideoCodecSupported(state.selectedVideoCodec)) {
            showUnsupportedVideoCodec(state.selectedVideoCodec)
            return
        }

        val audioSource = state.selectedAudioSource
        if ((audioSource.usesInternalAudio() || audioSource.usesMicrophone()) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        ensureNotificationPermissionAndRequestCapture()
    }

    private fun showUnsupportedVideoCodec(videoCodec: Int) {
        val codecName = getString(
            if (videoCodec == RecordingOptions.VIDEO_CODEC_H265) {
                R.string.video_codec_h265
            } else {
                R.string.video_codec_h264
            },
        )
        showToast(
            getString(R.string.video_codec_not_supported, codecName),
            Toast.LENGTH_LONG,
        )
    }

    private fun ensureNotificationPermissionAndRequestCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            !notificationPermissionRequested
        ) {
            notificationPermissionRequested = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        requestCapturePermission()
    }

    private fun requestCapturePermission() {
        try {
            captureLauncher.launch(projectionManager.createScreenCaptureIntent())
        } catch (_: RuntimeException) {
            showToast(R.string.capture_permission_unavailable, Toast.LENGTH_LONG)
        }
    }

    private fun stopRecordingService() {
        try {
            startService(RecordingService.createStopIntent(this))
        } catch (_: RuntimeException) {
            showToast(R.string.stop_recording_failed, Toast.LENGTH_LONG)
        }
    }

    private fun scheduleRecording(resultCode: Int, projectionData: Intent) {
        val state = recordingSettingsUiState.value
        val countdownMillis = state.selectedCountdownSeconds.toLong() * 1_000L
        val recordingStartTime = SystemClock.elapsedRealtime() + countdownMillis
        val startIntent = RecordingService.createStartIntent(
            this,
            resultCode,
            projectionData,
            state.selectedAudioSource,
            state.selectedSampleRate,
            state.selectedVideoResolution,
            state.selectedVideoFrameRate,
            state.force16By9Letterboxing,
            state.selectedVideoBitrate,
            state.selectedVideoCodec,
            state.selectedNamingPattern,
            state.selectedOrientation,
            recordingStartTime,
        )

        try {
            startForegroundService(startIntent)
            if (countdownMillis > 0L) {
                beginCountdownUi(recordingStartTime)
            } else {
                cancelCountdownUi()
            }
        } catch (_: RuntimeException) {
            showToast(R.string.recording_failed, Toast.LENGTH_LONG)
        }
    }

    private fun beginCountdownUi(recordingStartTime: Long) {
        cancelCountdownUi()
        val remainingMillis = (recordingStartTime - SystemClock.elapsedRealtime()).coerceAtLeast(1L)
        val initialSeconds = maxOf(1, ((remainingMillis + 999L) / 1_000L).toInt())
        updateRecordScreenUiState { it.copy(countdownSeconds = initialSeconds) }
        countDownTimer = object : CountDownTimer(remainingMillis, COUNTDOWN_TICK_MILLIS) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = maxOf(1, ((millisUntilFinished + 999L) / 1_000L).toInt())
                updateRecordScreenUiState { it.copy(countdownSeconds = seconds) }
            }

            override fun onFinish() {
                countDownTimer = null
                updateRecordScreenUiState { it.copy(countdownSeconds = null) }
            }
        }.also { it.start() }
    }

    private fun cancelCountdownUi() {
        countDownTimer?.cancel()
        countDownTimer = null
        if (::recordScreenUiState.isInitialized &&
            recordScreenUiState.value.countdownSeconds != null
        ) {
            updateRecordScreenUiState { it.copy(countdownSeconds = null) }
        }
    }

    private fun updateAppUiState(transform: (AppUiState) -> AppUiState) {
        appUiState.value = transform(appUiState.value)
    }

    private fun updateRecordScreenUiState(
        transform: (RecordScreenUiState) -> RecordScreenUiState,
    ) {
        recordScreenUiState.value = transform(recordScreenUiState.value)
    }

    private fun updateRecordingSettingsUiState(
        transform: (RecordingSettingsUiState) -> RecordingSettingsUiState,
    ) {
        recordingSettingsUiState.value = transform(recordingSettingsUiState.value)
    }

    private fun updateRecordingsUiState(transform: (RecordingsUiState) -> RecordingsUiState) {
        recordingsUiState.value = transform(recordingsUiState.value)
    }

    private fun showToast(messageRes: Int, duration: Int) {
        Toast.makeText(this, messageRes, duration).show()
    }

    private fun showToast(message: String, duration: Int) {
        Toast.makeText(this, message, duration).show()
    }

    private companion object {
        const val COUNTDOWN_TICK_MILLIS = 250L
        const val VIDEO_MIME_TYPE = "video/mp4"

        val AUDIO_SOURCES = listOf(
            AudioSource.NONE,
            AudioSource.MICROPHONE,
            AudioSource.INTERNAL,
            AudioSource.INTERNAL_AND_MICROPHONE,
        )

        val VIDEO_BITRATES = listOf(
            RecordingOptions.VIDEO_BITRATE_AUTO,
            RecordingOptions.VIDEO_BITRATE_4_MBPS,
            RecordingOptions.VIDEO_BITRATE_8_MBPS,
            RecordingOptions.VIDEO_BITRATE_16_MBPS,
            RecordingOptions.VIDEO_BITRATE_24_MBPS,
        )

        val VIDEO_RESOLUTIONS = listOf(
            RecordingOptions.VIDEO_RESOLUTION_NATIVE,
            RecordingOptions.VIDEO_RESOLUTION_1080P,
            RecordingOptions.VIDEO_RESOLUTION_720P,
            RecordingOptions.VIDEO_RESOLUTION_480P,
        )

        val VIDEO_FRAME_RATES = listOf(
            RecordingOptions.VIDEO_FRAME_RATE_AUTO,
            RecordingOptions.VIDEO_FRAME_RATE_120_FPS,
            RecordingOptions.VIDEO_FRAME_RATE_90_FPS,
            RecordingOptions.VIDEO_FRAME_RATE_60_FPS,
            RecordingOptions.VIDEO_FRAME_RATE_30_FPS,
        )

        val COUNTDOWN_OPTIONS = listOf(
            RecordingOptions.COUNTDOWN_OFF,
            RecordingOptions.COUNTDOWN_3_SECONDS,
            RecordingOptions.COUNTDOWN_5_SECONDS,
            RecordingOptions.COUNTDOWN_10_SECONDS,
        )

        val NAMING_PATTERNS = listOf(
            RecordingOptions.NAMING_DAY_MONTH_YEAR,
            RecordingOptions.NAMING_MONTH_DAY_YEAR,
            RecordingOptions.NAMING_YEAR_MONTH_DAY,
            RecordingOptions.NAMING_YEAR_DAY_MONTH,
        )
    }
}

@Immutable
private data class AppUiState(
    val selectedThemeMode: Int,
    val enablePredictiveBack: Boolean,
)

@Immutable
private data class RecordScreenUiState(
    val recordingState: Int,
    val countdownSeconds: Int? = null,
)

@Immutable
private data class RecordingSettingsUiState(
    val selectedAudioSource: AudioSource,
    val selectedSampleRate: Int,
    val selectedVideoResolution: Int,
    val selectedVideoFrameRate: Int,
    val force16By9Letterboxing: Boolean,
    val selectedVideoBitrate: Int,
    val selectedVideoCodec: Int,
    val selectedCountdownSeconds: Int,
    val selectedOrientation: Int,
    val selectedNamingPattern: String,
)

@Immutable
private data class RecordingsUiState(
    val recordings: List<RecordingItem> = emptyList(),
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    val deleting: Boolean = false,
)
