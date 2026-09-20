// SPDX-License-Identifier: GPL-3.0-only

package com.openrecorder.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Requests the permissions needed by a Quick Settings recording without showing the app UI.
 * The activity is translucent, so the app that was visible before opening Quick Settings stays
 * behind the Android permission dialogs and becomes active again as soon as capture is authorized.
 */
class RecorderTileActivity : ComponentActivity() {
    private val projectionManager by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(MediaProjectionManager::class.java)
    }
    private lateinit var recorderPreferences: RecorderPreferences
    private var permissionFlowStarted = false

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            ensureNotificationPermissionAndRequestCapture()
        } else {
            showToast(R.string.audio_permission_denied, Toast.LENGTH_LONG)
            finishWithoutAnimation()
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
            finishWithoutAnimation()
            return@registerForActivityResult
        }

        startRecording(result.resultCode, projectionData)
        finishWithoutAnimation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recorderPreferences = RecorderPreferences(this)
        permissionFlowStarted = savedInstanceState?.getBoolean(KEY_PERMISSION_FLOW_STARTED) == true

        if (RecordingState.get() != RecordingState.IDLE) {
            finishWithoutAnimation()
            return
        }

        if (!permissionFlowStarted) {
            permissionFlowStarted = true
            ensureAudioPermissionAndRequestCapture()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_PERMISSION_FLOW_STARTED, permissionFlowStarted)
        super.onSaveInstanceState(outState)
    }

    private fun ensureAudioPermissionAndRequestCapture() {
        val videoCodec = recorderPreferences.loadVideoCodec()
        if (!ScreenRecorder.isVideoCodecSupported(videoCodec)) {
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
            finishWithoutAnimation()
            return
        }

        val audioSource = recorderPreferences.loadAudioSource()
        if ((audioSource.usesInternalAudio() || audioSource.usesMicrophone()) &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        ensureNotificationPermissionAndRequestCapture()
    }

    private fun ensureNotificationPermissionAndRequestCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
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
            finishWithoutAnimation()
        }
    }

    private fun startRecording(resultCode: Int, projectionData: Intent) {
        if (RecordingState.get() != RecordingState.IDLE) return

        val countdownMillis = recorderPreferences.loadCountdownSeconds().toLong() * 1_000L
        val startIntent = RecordingService.createStartIntent(
            this,
            resultCode,
            projectionData,
            recorderPreferences.loadAudioSource(),
            recorderPreferences.loadSampleRate(),
            recorderPreferences.loadVideoResolution(),
            recorderPreferences.loadVideoFrameRate(),
            recorderPreferences.loadForce16By9Letterboxing(),
            recorderPreferences.loadVideoBitrate(),
            recorderPreferences.loadVideoCodec(),
            recorderPreferences.loadNamingPattern(),
            recorderPreferences.loadOrientation(),
            SystemClock.elapsedRealtime() + countdownMillis,
        )

        try {
            startForegroundService(startIntent)
        } catch (_: RuntimeException) {
            showToast(R.string.recording_failed, Toast.LENGTH_LONG)
        }
    }

    private fun showToast(messageRes: Int, duration: Int) {
        Toast.makeText(this, messageRes, duration).show()
    }

    private fun showToast(message: String, duration: Int) {
        Toast.makeText(this, message, duration).show()
    }

    @Suppress("DEPRECATION")
    private fun finishWithoutAnimation() {
        if (isFinishing) return
        finish()
        overridePendingTransition(0, 0)
    }

    private companion object {
        const val KEY_PERMISSION_FLOW_STARTED = "permission_flow_started"
    }
}
