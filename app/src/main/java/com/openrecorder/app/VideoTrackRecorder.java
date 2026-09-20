package com.openrecorder.app;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Encodes the screen through an asynchronous, hardware-first surface pipeline. */
final class VideoTrackRecorder {
    interface Listener {
        void onLimitReached();
        void onFailure(Exception error);
    }

    private static final String TAG = "VideoTrackRecorder";
    private static final int I_FRAME_INTERVAL_SECONDS = 1;
    private static final long MAX_DURATION_US = 60L * 60L * 1_000_000L;
    private static final long ENCODER_STOP_TIMEOUT_MS = 8_000L;
    private static final long CALLBACK_THREAD_JOIN_TIMEOUT_MS = 1_000L;
    private static final long SOURCE_CLOCK_TOLERANCE_NANOS = 30_000_000_000L;

    private final int width;
    private final int height;
    private final int bitrate;
    private final int videoCodec;
    private final int requestedFrameRate;
    private final float sourceRefreshRate;
    private final long maximumFileSize;
    private final Listener listener;
    private final AtomicReference<Exception> failure = new AtomicReference<>();
    private final AtomicBoolean resourcesFinished = new AtomicBoolean();
    private final AtomicBoolean encodingFinished = new AtomicBoolean();
    private final AtomicBoolean limitNotified = new AtomicBoolean();
    private final CountDownLatch encodingFinishedLatch = new CountDownLatch(1);

    private volatile MediaCodec codec;
    private Surface inputSurface;
    private RecordingMuxer.Track outputTrack;
    private RecordingTimeline timeline;
    private HandlerThread callbackThread;
    private Handler callbackHandler;
    private MediaFormat negotiatedOutputFormat;
    private boolean outputFormatRegistered;
    private boolean prepared;
    private boolean started;
    private boolean codecStarted;
    private volatile boolean endOfStreamSignaled;
    private volatile boolean paused;
    private boolean sourceClockResolved;
    private long sourceToMonotonicOffsetNanos;
    private long lastWrittenPresentationTimeUs = -1L;
    private long encodedBytesWritten;
    private int configuredFrameRate;

    private final MediaCodec.Callback codecCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(MediaCodec callbackCodec, int index) {
            // Surface-input encoders never consume ByteBuffer input.
        }

        @Override
        public void onOutputBufferAvailable(
                MediaCodec callbackCodec,
                int index,
                MediaCodec.BufferInfo info) {
            handleOutputBuffer(callbackCodec, index, info);
        }

        @Override
        public void onError(MediaCodec callbackCodec, MediaCodec.CodecException error) {
            if (callbackCodec == codec) {
                completeEncoding(error);
            }
        }

        @Override
        public void onOutputFormatChanged(MediaCodec callbackCodec, MediaFormat format) {
            if (callbackCodec != codec || encodingFinished.get()) {
                return;
            }
            try {
                if (negotiatedOutputFormat != null) {
                    throw new IOException("Video output format changed more than once");
                }
                negotiatedOutputFormat = format;
                logNegotiatedBitrate(format);
            } catch (Exception error) {
                completeEncoding(error);
            }
        }
    };

    VideoTrackRecorder(
            int width,
            int height,
            int bitrate,
            int videoCodec,
            int requestedFrameRate,
            float sourceRefreshRate,
            long maximumFileSize,
            Listener listener) {
        if (width <= 0 || height <= 0 || bitrate <= 0 || maximumFileSize <= 0L) {
            throw new IllegalArgumentException("Invalid video encoder configuration");
        }
        this.width = width;
        this.height = height;
        this.bitrate = bitrate;
        this.videoCodec = RecordingOptions.normalizeVideoCodec(videoCodec);
        this.requestedFrameRate = RecordingOptions.normalizeVideoFrameRate(requestedFrameRate);
        this.sourceRefreshRate = sourceRefreshRate;
        this.maximumFileSize = maximumFileSize;
        this.listener = listener;
    }

    static boolean isCodecSupported(int requestedCodec) {
        String mimeType = mimeTypeForCodec(requestedCodec);
        try {
            return !encoderCandidates(mimeType).isEmpty();
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to query video encoder support", error);
            return false;
        }
    }

    synchronized void prepare() throws IOException {
        if (prepared) {
            return;
        }
        if (started) {
            throw new IllegalStateException("Video recorder is single-use");
        }

        try {
            startCallbackThread();
            configureBestEncoder(mimeTypeForCodec(videoCodec));
            prepared = true;
        } catch (Exception error) {
            finishResources();
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException("Unable to prepare the video encoder", error);
        }
    }

    synchronized Surface getInputSurface() throws IOException {
        prepare();
        return inputSurface;
    }

    synchronized int getConfiguredFrameRate() {
        if (!prepared || configuredFrameRate <= 0) {
            throw new IllegalStateException("Video encoder is not prepared");
        }
        return configuredFrameRate;
    }

    synchronized void setOutputTrack(RecordingMuxer.Track outputTrack) {
        if (started || this.outputTrack != null || outputTrack == null) {
            throw new IllegalStateException("Video output track cannot be changed");
        }
        this.outputTrack = outputTrack;
    }

    synchronized void start(RecordingTimeline timeline) throws IOException {
        if (started) {
            throw new IllegalStateException("Video recorder is single-use");
        }
        if (outputTrack == null || timeline == null) {
            throw new IllegalStateException("Video output is not configured");
        }
        prepare();
        try {
            this.timeline = timeline;
            started = true;
            codec.start();
            codecStarted = true;
        } catch (Exception error) {
            completeEncoding(error);
            finishResources();
            throw new IOException("Unable to start the video encoder", error);
        }
    }

    synchronized void pause() throws IOException {
        if (!started || endOfStreamSignaled || encodingFinished.get()) {
            throw new IllegalStateException("Video recording is not active");
        }
        if (paused) {
            return;
        }
        paused = true;
        setSuspended(true);
    }

    synchronized void resume() throws IOException {
        if (!started || endOfStreamSignaled || encodingFinished.get()) {
            throw new IllegalStateException("Video recording is not active");
        }
        if (!paused) {
            return;
        }
        setSuspended(false);
        paused = false;
    }

    synchronized void requestStop() {
        if (!started || endOfStreamSignaled || encodingFinished.get()) {
            return;
        }
        if (paused) {
            setSuspended(false);
            paused = false;
        }
        endOfStreamSignaled = true;
        try {
            codec.signalEndOfInputStream();
        } catch (RuntimeException error) {
            completeEncoding(error);
        }
    }

    synchronized void awaitStopped() throws IOException {
        if (!started) {
            return;
        }
        boolean completed;
        try {
            completed = encodingFinishedLatch.await(
                    ENCODER_STOP_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while stopping video encoding", error);
        }
        if (!completed) {
            IOException timeout = new IOException("Timed out while draining the video encoder");
            failure.compareAndSet(null, timeout);
        }
        finishResources();

        Exception encoderFailure = failure.get();
        if (encoderFailure != null) {
            throw new IOException("Video encoding failed", encoderFailure);
        }
        if (!completed) {
            throw new IOException("Timed out while draining the video encoder");
        }
    }

    synchronized void stop() throws IOException {
        requestStop();
        awaitStopped();
    }

    synchronized void release() {
        if (started && !resourcesFinished.get()) {
            requestStop();
            try {
                awaitStopped();
            } catch (IOException error) {
                Log.w(TAG, "Unable to stop the video encoder cleanly", error);
            }
        }
        finishResources();
    }

    private void startCallbackThread() {
        callbackThread = new HandlerThread(
                "ScreenVideoEncoderCallback",
                Process.THREAD_PRIORITY_DISPLAY);
        callbackThread.start();
        callbackHandler = new Handler(callbackThread.getLooper());
    }

    private void configureBestEncoder(String mimeType) throws IOException {
        List<MediaCodecInfo> candidates = encoderCandidates(mimeType);
        if (candidates.isEmpty()) {
            throw new IOException("No compatible surface video encoder was found");
        }

        int[] frameRates = VideoFrameRatePolicy.fallbackCandidates(
                requestedFrameRate,
                sourceRefreshRate);
        Exception lastFailure = null;
        for (int hardwareRank = 0; hardwareRank <= 2; hardwareRank++) {
            for (int frameRate : frameRates) {
                for (MediaCodecInfo candidate : candidates) {
                    if (hardwareRank(candidate) != hardwareRank) {
                        continue;
                    }
                    try {
                        configureEncoder(candidate, mimeType, frameRate);
                        logConfiguredEncoder(candidate, frameRate);
                        return;
                    } catch (Exception error) {
                        lastFailure = error;
                        Log.w(TAG, "Encoder " + candidate.getName()
                                + " rejected " + width + "x" + height
                                + " at " + frameRate + " fps", error);
                    }
                }
            }
        }

        throw new IOException(
                "No encoder could be configured for the requested frame-rate fallbacks",
                lastFailure);
    }

    private void configureEncoder(
            MediaCodecInfo codecInfo,
            String mimeType,
            int frameRate) throws IOException {
        MediaCodec candidateCodec = null;
        Surface candidateSurface = null;
        try {
            long frameIntervalUs = VideoTimestampNormalizer.frameIntervalUs(frameRate);
            MediaFormat format = MediaFormat.createVideoFormat(mimeType, width, height);
            format.setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS);
            format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, frameIntervalUs);
            format.setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, frameRate);

            candidateCodec = MediaCodec.createByCodecName(codecInfo.getName());
            candidateCodec.setCallback(codecCallback, callbackHandler);
            candidateCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            candidateSurface = candidateCodec.createInputSurface();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    candidateSurface.setFrameRate(
                            frameRate,
                            Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
                } catch (RuntimeException error) {
                    Log.w(TAG, "Unable to apply the frame-rate hint to the input surface", error);
                }
            }

            codec = candidateCodec;
            inputSurface = candidateSurface;
            configuredFrameRate = frameRate;
        } catch (Exception error) {
            if (candidateSurface != null) {
                try {
                    candidateSurface.release();
                } catch (RuntimeException ignored) {
                }
            }
            if (candidateCodec != null) {
                try {
                    candidateCodec.release();
                } catch (RuntimeException ignored) {
                }
            }
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException("Encoder configuration failed", error);
        }
    }

    private void logConfiguredEncoder(MediaCodecInfo codecInfo, int frameRate) {
        Log.i(TAG, "Encoder " + codecInfo.getName()
                + " configured at " + width + "x" + height
                + ", target " + bitrate + " bps, " + frameRate + " fps"
                + (codecInfo.isHardwareAccelerated() ? ", hardware" : ""));
        int preferredFrameRate = VideoFrameRatePolicy.resolvePreferredFrameRate(
                requestedFrameRate,
                sourceRefreshRate);
        if (frameRate != preferredFrameRate) {
            Log.w(TAG, "Requested " + preferredFrameRate + " fps, using configured fallback "
                    + frameRate + " fps");
        }
    }

    private void handleOutputBuffer(
            MediaCodec callbackCodec,
            int outputIndex,
            MediaCodec.BufferInfo info) {
        if (callbackCodec != codec) {
            return;
        }

        Exception callbackFailure = null;
        boolean endOfStream = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        try {
            ByteBuffer output = callbackCodec.getOutputBuffer(outputIndex);
            if (info.size > 0 && output == null) {
                throw new IOException("Video encoder output buffer is unavailable");
            }
            if (output != null
                    && info.size > 0
                    && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                    && !encodingFinished.get()) {
                long adjustedPresentationTimeUs = adjustPresentationTime(
                        info.presentationTimeUs);
                if (adjustedPresentationTimeUs >= 0L) {
                    ensureOutputTrackConfigured();
                    output.position(info.offset);
                    output.limit(info.offset + info.size);
                    info.presentationTimeUs = adjustedPresentationTimeUs;
                    outputTrack.writeSampleData(output, info);
                    encodedBytesWritten += info.size;
                    checkRecordingLimits(info.presentationTimeUs);
                }
            }
        } catch (Exception error) {
            callbackFailure = error;
        } finally {
            try {
                callbackCodec.releaseOutputBuffer(outputIndex, false);
            } catch (RuntimeException releaseError) {
                if (callbackFailure == null) {
                    callbackFailure = releaseError;
                } else {
                    callbackFailure.addSuppressed(releaseError);
                }
            }
        }

        if (callbackFailure != null) {
            completeEncoding(callbackFailure);
        } else if (endOfStream) {
            completeEncoding(null);
        }
    }

    private void ensureOutputTrackConfigured() throws IOException {
        if (outputFormatRegistered) {
            return;
        }
        if (negotiatedOutputFormat == null) {
            throw new IOException("Video samples arrived before the output format");
        }
        outputTrack.setFormat(negotiatedOutputFormat);
        outputFormatRegistered = true;
    }

    private long adjustPresentationTime(long sourcePresentationTimeUs) {
        long sourceNanos = sourcePresentationTimeUs * 1_000L;
        if (!sourceClockResolved) {
            long startedAtNanos = timeline.getStartedAtNanos();
            long clockDifferenceNanos = sourceNanos - startedAtNanos;
            if (clockDifferenceNanos > SOURCE_CLOCK_TOLERANCE_NANOS
                    || clockDifferenceNanos < -SOURCE_CLOCK_TOLERANCE_NANOS) {
                sourceToMonotonicOffsetNanos = startedAtNanos - sourceNanos;
                Log.w(TAG, "Video timestamps use a non-monotonic origin; applying a clock offset");
            }
            sourceClockResolved = true;
        }
        long monotonicTimestampNanos = sourceNanos + sourceToMonotonicOffsetNanos;
        if (!timeline.shouldInclude(monotonicTimestampNanos)) {
            return -1L;
        }
        long adjusted = timeline.toPresentationTimeUs(monotonicTimestampNanos);
        adjusted = VideoTimestampNormalizer.ensureFrameSpacing(
                adjusted,
                lastWrittenPresentationTimeUs,
                configuredFrameRate);
        lastWrittenPresentationTimeUs = adjusted;
        return adjusted;
    }

    private void checkRecordingLimits(long presentationTimeUs) {
        if ((presentationTimeUs >= MAX_DURATION_US || encodedBytesWritten >= maximumFileSize)
                && limitNotified.compareAndSet(false, true)) {
            try {
                listener.onLimitReached();
            } catch (RuntimeException error) {
                Log.w(TAG, "Video limit listener failed", error);
            }
        }
    }

    private void logNegotiatedBitrate(MediaFormat outputFormat) {
        if (!outputFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
            Log.i(TAG, "Encoder accepted target bitrate without exposing a fixed negotiated value");
            return;
        }
        try {
            int negotiatedBitrate = outputFormat.getInteger(MediaFormat.KEY_BIT_RATE);
            Log.i(TAG, "Encoder bitrate target=" + bitrate
                    + " bps, negotiated=" + negotiatedBitrate
                    + " bps; vendor rate control remains active");
        } catch (ClassCastException error) {
            Log.w(TAG, "Encoder returned a non-integer bitrate value", error);
        }
    }

    private void setSuspended(boolean suspend) {
        MediaCodec activeCodec = codec;
        if (activeCodec == null) {
            return;
        }
        try {
            Bundle parameters = new Bundle();
            parameters.putInt(MediaCodec.PARAMETER_KEY_SUSPEND, suspend ? 1 : 0);
            activeCodec.setParameters(parameters);
        } catch (RuntimeException error) {
            Log.w(TAG, suspend
                    ? "Encoder suspend is unavailable; dropping paused frames by timestamp"
                    : "Encoder resume parameter failed; continuing with pause-free timestamps",
                    error);
        }
    }

    private void completeEncoding(Exception error) {
        if (!encodingFinished.compareAndSet(false, true)) {
            return;
        }
        if (error != null) {
            failure.compareAndSet(null, error);
            Log.e(TAG, "Video encoder failed", error);
        }
        encodingFinishedLatch.countDown();
        if (error != null) {
            try {
                listener.onFailure(error);
            } catch (RuntimeException listenerError) {
                Log.w(TAG, "Video failure listener failed", listenerError);
            }
        }
    }

    private void finishResources() {
        if (!resourcesFinished.compareAndSet(false, true)) {
            return;
        }

        MediaCodec activeCodec = codec;
        codec = null;
        if (activeCodec != null) {
            if (codecStarted) {
                try {
                    activeCodec.stop();
                } catch (Exception error) {
                    Log.w(TAG, "Unable to stop video codec", error);
                }
            }
            try {
                activeCodec.release();
            } catch (Exception error) {
                Log.w(TAG, "Unable to release video codec", error);
            }
        }
        if (inputSurface != null) {
            try {
                inputSurface.release();
            } catch (RuntimeException error) {
                Log.w(TAG, "Unable to release video input surface", error);
            }
            inputSurface = null;
        }
        if (outputTrack != null) {
            outputTrack.finish();
        }

        HandlerThread activeCallbackThread = callbackThread;
        callbackThread = null;
        callbackHandler = null;
        if (activeCallbackThread != null) {
            activeCallbackThread.quit();
            if (Thread.currentThread() != activeCallbackThread) {
                try {
                    activeCallbackThread.join(CALLBACK_THREAD_JOIN_TIMEOUT_MS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    Log.w(TAG, "Interrupted while stopping video callback thread", error);
                }
            }
        }
    }

    private static List<MediaCodecInfo> encoderCandidates(String mimeType) {
        List<MediaCodecInfo> candidates = new ArrayList<>();
        for (MediaCodecInfo codecInfo : new MediaCodecList(
                MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (codecInfo.isEncoder() && supportsSurfaceInput(codecInfo, mimeType)) {
                candidates.add(codecInfo);
            }
        }
        candidates.sort(Comparator
                .comparingInt(VideoTrackRecorder::hardwareRank)
                .thenComparing(MediaCodecInfo::getName));
        return candidates;
    }

    private static int hardwareRank(MediaCodecInfo codecInfo) {
        if (codecInfo.isHardwareAccelerated()) {
            return 0;
        }
        if (codecInfo.isSoftwareOnly()) {
            return 2;
        }
        return 1;
    }

    private static boolean supportsSurfaceInput(MediaCodecInfo codecInfo, String mimeType) {
        try {
            boolean supportsMimeType = false;
            for (String supportedType : codecInfo.getSupportedTypes()) {
                if (mimeType.equalsIgnoreCase(supportedType)) {
                    supportsMimeType = true;
                    break;
                }
            }
            if (!supportsMimeType) {
                return false;
            }
            for (int colorFormat : codecInfo.getCapabilitiesForType(mimeType).colorFormats) {
                if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    private static String mimeTypeForCodec(int requestedCodec) {
        return RecordingOptions.normalizeVideoCodec(requestedCodec)
                == RecordingOptions.VIDEO_CODEC_H265
                ? MediaFormat.MIMETYPE_VIDEO_HEVC
                : MediaFormat.MIMETYPE_VIDEO_AVC;
    }
}
