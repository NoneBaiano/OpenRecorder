package com.openrecorder.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Process;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class InternalAudioRecorder {
    private static final String TAG = "InternalAudioRecorder";
    private static final int BIT_RATE = 196_000;
    private static final int CHANNEL_COUNT = 1;
    private static final int READ_SAMPLES = 4096;
    private static final float MIC_GAIN = 1.4f;
    private static final long CODEC_TIMEOUT_US = 10_000L;
    private static final int MAX_CODEC_POLLS = 200;
    private static final long WORKER_JOIN_TIMEOUT_MS = 5_000L;

    private final Context context;
    private final MediaProjection projection;
    private final boolean capturePlayback;
    private final boolean includeMicrophone;
    private final int sampleRate;
    private final AtomicReference<Exception> failure = new AtomicReference<>();
    private final AtomicBoolean codecResourcesFinished = new AtomicBoolean();
    private final MediaCodec.BufferInfo codecBufferInfo = new MediaCodec.BufferInfo();
    private final AudioTimestamp captureTimestamp = new AudioTimestamp();
    private final Object pauseLock = new Object();

    private AudioRecord playbackRecord;
    private AudioRecord microphoneRecord;
    private MediaCodec codec;
    private RecordingMuxer.Track outputTrack;
    private RecordingTimeline timeline;
    private MediaFormat negotiatedOutputFormat;
    private boolean outputFormatRegistered;
    private Thread worker;
    private volatile boolean running;
    private volatile boolean paused;
    private boolean prepared;
    private boolean started;
    private boolean codecStarted;
    private boolean endOfStreamQueued;
    private long captureSessionFrames;
    private long lastEstimatedBufferEndNanos = Long.MIN_VALUE;
    private long lastQueuedPresentationTimeUs = -1L;
    private long endOfStreamPresentationTimeUs;

    InternalAudioRecorder(
            Context context,
            MediaProjection projection,
            AudioSource audioSource,
            int sampleRate) {
        this.context = context.getApplicationContext();
        this.projection = projection;
        this.capturePlayback = audioSource.usesInternalAudio();
        this.includeMicrophone = audioSource.usesMicrophone();
        if (!capturePlayback && !includeMicrophone) {
            throw new IllegalArgumentException("An audio capture source is required");
        }
        this.sampleRate = RecordingOptions.normalizeSampleRate(sampleRate);
    }

    synchronized void prepare() throws IOException {
        if (prepared) {
            return;
        }
        try {
            setup();
            codec.start();
            codecStarted = true;
            prepared = true;
        } catch (SecurityException error) {
            cleanUpFailedStart();
            throw error;
        } catch (Exception error) {
            cleanUpFailedStart();
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException("Unable to prepare internal audio capture", error);
        }
    }

    synchronized void setOutputTrack(RecordingMuxer.Track outputTrack) {
        if (started || this.outputTrack != null || outputTrack == null) {
            throw new IllegalStateException("Audio output track cannot be changed");
        }
        this.outputTrack = outputTrack;
    }

    synchronized void start(RecordingTimeline timeline) throws IOException {
        if (started) {
            throw new IllegalStateException("Audio recorder is single-use");
        }
        if (outputTrack == null || timeline == null) {
            throw new IllegalStateException("Audio output is not configured");
        }
        prepare();
        started = true;

        try {
            this.timeline = timeline;
            if (playbackRecord != null) {
                playbackRecord.startRecording();
            }
            if (microphoneRecord != null) {
                microphoneRecord.startRecording();
            }
            ensureRecordersStarted();

            running = true;
            paused = false;
            resetCaptureClockSession();
            worker = new Thread(this::captureLoop, "ScreenAudioCapture");
            worker.start();
        } catch (SecurityException error) {
            cleanUpFailedStart();
            throw error;
        } catch (Exception error) {
            cleanUpFailedStart();
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException("Unable to start internal audio capture", error);
        }
    }

    private void setup() throws IOException {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("RECORD_AUDIO permission is required");
        }
        int minimumBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minimumBuffer <= 0) {
            throw new IOException("Unsupported audio capture configuration");
        }
        int bufferSize = Math.max(minimumBuffer * 4, 64 * 1024);

        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build();
        if (capturePlayback) {
            AudioPlaybackCaptureConfiguration captureConfiguration =
                    new AudioPlaybackCaptureConfiguration.Builder(projection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                            .build();
            playbackRecord = new AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .setAudioPlaybackCaptureConfig(captureConfiguration)
                    .build();
            ensureInitialized(playbackRecord, "Internal audio recorder");
        }

        if (includeMicrophone) {
            microphoneRecord = new AudioRecord.Builder()
                    .setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSize)
                    .build();
            ensureInitialized(microphoneRecord, "Microphone recorder");
        }

        MediaFormat codecFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                sampleRate,
                CHANNEL_COUNT);
        codecFormat.setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        codecFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        codecFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        codec.configure(codecFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    private void ensureRecordersStarted() throws IOException {
        if (playbackRecord != null
                && playbackRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            throw new IOException("Internal audio capture did not start");
        }
        if (microphoneRecord != null
                && microphoneRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            throw new IOException("Microphone capture did not start");
        }
    }

    private static void ensureInitialized(AudioRecord record, String name) throws IOException {
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new IOException(name + " is unavailable");
        }
    }

    private void captureLoop() {
        AudioRecord primaryRecord = capturePlayback ? playbackRecord : microphoneRecord;
        String primaryName = capturePlayback ? "Internal audio" : "Microphone";
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            if (capturePlayback && includeMicrophone) {
                captureMixedAudio(primaryRecord, primaryName);
            } else {
                captureDirectAudio(primaryRecord, primaryName);
            }
            finishEncodingStream();
        } catch (Exception error) {
            if (running) {
                failure.compareAndSet(null, error);
                Log.e(TAG, "Audio capture failed", error);
            } else {
                try {
                    finishEncodingStream();
                } catch (Exception finishError) {
                    failure.compareAndSet(null, finishError);
                    Log.w(TAG, "Unable to finalize the audio stream", finishError);
                }
            }
        } finally {
            running = false;
            finishCodecAndOutput();
        }
    }

    /** Reads a single source straight into the AAC encoder's direct input buffer. */
    private void captureDirectAudio(AudioRecord record, String sourceName)
            throws IOException, InterruptedException {
        while (running) {
            if (!waitUntilActive()) {
                break;
            }
            int inputIndex = dequeueInputBufferWhileCapturing();
            if (inputIndex < 0) {
                break;
            }
            ByteBuffer input = codec.getInputBuffer(inputIndex);
            if (input == null) {
                throw new IOException("AAC encoder input buffer is unavailable");
            }
            input.clear();
            int maximumBytes = Math.min(input.remaining(), READ_SAMPLES * 2) & ~1;
            if (maximumBytes == 0) {
                throw new IOException("AAC encoder input buffer is too small");
            }
            if (!running) {
                queueEmptyInputBuffer(inputIndex);
                break;
            }

            int byteCount = record.read(input, maximumBytes, AudioRecord.READ_BLOCKING);
            if (byteCount <= 0) {
                queueEmptyInputBuffer(inputIndex);
                if (running) {
                    if (paused) {
                        continue;
                    }
                    throw new IOException(sourceName + " read failed: " + byteCount);
                }
                break;
            }

            int frameCount = (byteCount & ~1) / 2;
            long bufferStartFrame = captureSessionFrames;
            captureSessionFrames += frameCount;
            long bufferStartNanos = estimateBufferStartNanos(
                    record,
                    bufferStartFrame,
                    frameCount);
            queueDirectPcmBuffer(inputIndex, input, frameCount, bufferStartNanos);
            drainCodec(false);
        }
    }

    private void captureMixedAudio(AudioRecord primaryRecord, String primaryName)
            throws IOException, InterruptedException {
        short[] primary = new short[READ_SAMPLES];
        short[] microphone = new short[READ_SAMPLES];
        while (running) {
            if (!waitUntilActive()) {
                break;
            }
            int primaryCount = primaryRecord.read(
                    primary,
                    0,
                    primary.length,
                    AudioRecord.READ_BLOCKING);
            if (primaryCount <= 0) {
                if (running) {
                    if (paused) {
                        continue;
                    }
                    throw new IOException(primaryName + " read failed: " + primaryCount);
                }
                break;
            }
            long bufferStartFrame = captureSessionFrames;
            captureSessionFrames += primaryCount;
            long bufferStartNanos = estimateBufferStartNanos(
                    primaryRecord,
                    bufferStartFrame,
                    primaryCount);

            int microphoneCount = microphoneRecord.read(
                    microphone,
                    0,
                    microphone.length,
                    AudioRecord.READ_BLOCKING);
            if (microphoneCount <= 0) {
                if (running) {
                    if (paused) {
                        continue;
                    }
                    throw new IOException("Microphone read failed: " + microphoneCount);
                }
                break;
            }
            int sampleCount = Math.min(primaryCount, microphoneCount);
            mix(primary, microphone, sampleCount);
            queueShortPcm(primary, sampleCount, bufferStartNanos);
            drainCodec(false);
        }
    }

    private boolean waitUntilActive() throws InterruptedException {
        synchronized (pauseLock) {
            while (running && paused) {
                pauseLock.wait();
            }
            return running;
        }
    }

    private void mix(short[] playback, short[] microphone, int count) {
        for (int i = 0; i < count; i++) {
            int mic = Math.round(microphone[i] * MIC_GAIN);
            int mixed = playback[i] + mic;
            playback[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mixed));
        }
    }

    private long estimateBufferStartNanos(
            AudioRecord record,
            long bufferStartFrame,
            int frameCount) {
        long estimateNanos;
        int timestampResult;
        try {
            timestampResult = record.getTimestamp(
                    captureTimestamp,
                    AudioTimestamp.TIMEBASE_MONOTONIC);
        } catch (RuntimeException ignored) {
            timestampResult = AudioRecord.ERROR_INVALID_OPERATION;
        }
        if (timestampResult == AudioRecord.SUCCESS) {
            estimateNanos = AudioFrameClock.frameTimeNanos(
                    captureTimestamp.framePosition,
                    captureTimestamp.nanoTime,
                    bufferStartFrame,
                    sampleRate);
        } else if (lastEstimatedBufferEndNanos != Long.MIN_VALUE) {
            estimateNanos = lastEstimatedBufferEndNanos;
        } else {
            estimateNanos = System.nanoTime()
                    - AudioFrameClock.framesToNanos(frameCount, sampleRate);
        }
        lastEstimatedBufferEndNanos = estimateNanos
                + AudioFrameClock.framesToNanos(frameCount, sampleRate);
        return estimateNanos;
    }

    private int dequeueInputBufferWhileCapturing() throws IOException {
        while (running) {
            int inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US);
            if (inputIndex >= 0) {
                return inputIndex;
            }
            drainCodec(false);
        }
        return -1;
    }

    private int dequeueInputBufferForPcm() throws IOException {
        for (int attempts = 0; attempts < MAX_CODEC_POLLS; attempts++) {
            int inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US);
            if (inputIndex >= 0) {
                return inputIndex;
            }
            drainCodec(false);
        }
        throw new IOException("Timed out while feeding the AAC encoder");
    }

    private void queueDirectPcmBuffer(
            int inputIndex,
            ByteBuffer input,
            int frameCount,
            long bufferStartNanos) throws IOException {
        long bufferEndNanos = bufferStartNanos
                + AudioFrameClock.framesToNanos(frameCount, sampleRate);
        long cursorNanos = bufferStartNanos;
        int writtenFrames = 0;
        long firstIncludedFrameNanos = Long.MIN_VALUE;

        while (cursorNanos < bufferEndNanos) {
            RecordingTimeline.IncludedRange range = timeline.findNextIncludedRange(
                    cursorNanos,
                    bufferEndNanos);
            if (range == null) {
                break;
            }
            int sourceStartFrame = frameOffsetAtOrAfter(
                    range.startNanos,
                    bufferStartNanos,
                    frameCount);
            int sourceEndFrame = frameOffsetAtOrAfter(
                    range.endNanos,
                    bufferStartNanos,
                    frameCount);
            if (sourceEndFrame > sourceStartFrame) {
                if (firstIncludedFrameNanos == Long.MIN_VALUE) {
                    firstIncludedFrameNanos = bufferStartNanos
                            + AudioFrameClock.framesToNanos(sourceStartFrame, sampleRate);
                }
                int includedFrames = sourceEndFrame - sourceStartFrame;
                if (sourceStartFrame != writtenFrames) {
                    movePcmFrames(input, sourceStartFrame, writtenFrames, includedFrames);
                }
                writtenFrames += includedFrames;
            }
            if (range.endNanos <= cursorNanos) {
                break;
            }
            cursorNanos = range.endNanos;
        }

        if (writtenFrames == 0) {
            queueEmptyInputBuffer(inputIndex);
            return;
        }
        queueAudioInputBuffer(
                inputIndex,
                writtenFrames * 2,
                firstIncludedFrameNanos,
                writtenFrames);
    }

    private void queueShortPcm(
            short[] samples,
            int frameCount,
            long bufferStartNanos) throws IOException {
        long bufferEndNanos = bufferStartNanos
                + AudioFrameClock.framesToNanos(frameCount, sampleRate);
        long cursorNanos = bufferStartNanos;
        while (cursorNanos < bufferEndNanos) {
            RecordingTimeline.IncludedRange range = timeline.findNextIncludedRange(
                    cursorNanos,
                    bufferEndNanos);
            if (range == null) {
                return;
            }
            int sourceFrame = frameOffsetAtOrAfter(
                    range.startNanos,
                    bufferStartNanos,
                    frameCount);
            int sourceEndFrame = frameOffsetAtOrAfter(
                    range.endNanos,
                    bufferStartNanos,
                    frameCount);
            while (sourceFrame < sourceEndFrame) {
                int inputIndex = dequeueInputBufferForPcm();
                ByteBuffer input = codec.getInputBuffer(inputIndex);
                if (input == null) {
                    throw new IOException("AAC encoder input buffer is unavailable");
                }
                input.clear();
                int chunkFrames = Math.min(input.remaining() / 2, sourceEndFrame - sourceFrame);
                if (chunkFrames == 0) {
                    throw new IOException("AAC encoder input buffer is too small");
                }
                input.order(ByteOrder.LITTLE_ENDIAN)
                        .asShortBuffer()
                        .put(samples, sourceFrame, chunkFrames);
                long chunkStartNanos = bufferStartNanos
                        + AudioFrameClock.framesToNanos(sourceFrame, sampleRate);
                queueAudioInputBuffer(
                        inputIndex,
                        chunkFrames * 2,
                        chunkStartNanos,
                        chunkFrames);
                sourceFrame += chunkFrames;
            }
            if (range.endNanos <= cursorNanos) {
                return;
            }
            cursorNanos = range.endNanos;
        }
    }

    private int frameOffsetAtOrAfter(
            long timestampNanos,
            long bufferStartNanos,
            int frameCount) {
        long offsetFrames = AudioFrameClock.nanosToFramesCeil(
                Math.max(0L, timestampNanos - bufferStartNanos),
                sampleRate);
        return (int) Math.min(frameCount, offsetFrames);
    }

    private static void movePcmFrames(
            ByteBuffer input,
            int sourceFrame,
            int destinationFrame,
            int frameCount) {
        int sourceByte = sourceFrame * 2;
        int destinationByte = destinationFrame * 2;
        int byteCount = frameCount * 2;
        for (int index = 0; index < byteCount; index++) {
            input.put(destinationByte + index, input.get(sourceByte + index));
        }
    }

    private void queueAudioInputBuffer(
            int inputIndex,
            int byteCount,
            long firstFrameNanos,
            int frameCount) {
        long presentationTimeUs = timeline.toPresentationTimeUs(firstFrameNanos);
        if (presentationTimeUs <= lastQueuedPresentationTimeUs) {
            presentationTimeUs = lastQueuedPresentationTimeUs + 1L;
        }
        codec.queueInputBuffer(inputIndex, 0, byteCount, presentationTimeUs, 0);
        lastQueuedPresentationTimeUs = presentationTimeUs;
        long durationUs = AudioFrameClock.framesToNanos(frameCount, sampleRate) / 1_000L;
        endOfStreamPresentationTimeUs = Math.max(
                endOfStreamPresentationTimeUs,
                presentationTimeUs + durationUs);
    }

    private void queueEmptyInputBuffer(int inputIndex) {
        codec.queueInputBuffer(
                inputIndex,
                0,
                0,
                Math.max(0L, endOfStreamPresentationTimeUs),
                0);
    }

    private void finishEncodingStream() throws IOException {
        queueEndOfStream();
        drainCodec(true);
    }

    private void queueEndOfStream() throws IOException {
        if (endOfStreamQueued) {
            return;
        }
        for (int attempts = 0; attempts < MAX_CODEC_POLLS; attempts++) {
            int inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US);
            if (inputIndex >= 0) {
                codec.queueInputBuffer(
                        inputIndex,
                        0,
                        0,
                        endOfStreamPresentationTimeUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                endOfStreamQueued = true;
                return;
            }
            drainCodec(false);
        }
        throw new IOException("Timed out while signaling the AAC encoder");
    }

    private void drainCodec(boolean waitForEnd) throws IOException {
        int emptyPolls = 0;
        while (true) {
            int outputIndex = codec.dequeueOutputBuffer(
                    codecBufferInfo,
                    waitForEnd ? CODEC_TIMEOUT_US : 0L);
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!waitForEnd) {
                    return;
                }
                if (++emptyPolls >= MAX_CODEC_POLLS) {
                    throw new IOException("Timed out while draining the AAC encoder");
                }
                continue;
            }
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (negotiatedOutputFormat != null) {
                    throw new IOException("AAC output format changed more than once");
                }
                negotiatedOutputFormat = codec.getOutputFormat();
                emptyPolls = 0;
                continue;
            }
            if (outputIndex < 0) {
                continue;
            }

            boolean end;
            try {
                ByteBuffer output = codec.getOutputBuffer(outputIndex);
                if (codecBufferInfo.size > 0 && output == null) {
                    throw new IOException("AAC encoder output buffer is unavailable");
                }
                if (output != null
                        && codecBufferInfo.size > 0
                        && (codecBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    ensureOutputTrackConfigured();
                    output.position(codecBufferInfo.offset);
                    output.limit(codecBufferInfo.offset + codecBufferInfo.size);
                    outputTrack.writeSampleData(output, codecBufferInfo);
                }
                end = (codecBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            } finally {
                codec.releaseOutputBuffer(outputIndex, false);
            }
            if (end) {
                return;
            }
            emptyPolls = 0;
        }
    }

    private void ensureOutputTrackConfigured() throws IOException {
        if (outputFormatRegistered) {
            return;
        }
        if (negotiatedOutputFormat == null) {
            throw new IOException("AAC samples arrived before the output format");
        }
        outputTrack.setFormat(negotiatedOutputFormat);
        outputFormatRegistered = true;
    }

    synchronized void pause() {
        if (!started || !running) {
            throw new IllegalStateException("Audio recording is not active");
        }
        synchronized (pauseLock) {
            if (paused) {
                return;
            }
            paused = true;
        }
        safeStop(playbackRecord);
        safeStop(microphoneRecord);
    }

    synchronized void resume() throws IOException {
        if (!started || !running) {
            throw new IllegalStateException("Audio recording is not active");
        }
        synchronized (pauseLock) {
            if (!paused) {
                return;
            }
            try {
                if (playbackRecord != null) {
                    playbackRecord.startRecording();
                }
                if (microphoneRecord != null) {
                    microphoneRecord.startRecording();
                }
                ensureRecordersStarted();
                resetCaptureClockSession();
            } catch (Exception error) {
                throw new IOException("Unable to resume internal audio capture", error);
            }
            paused = false;
            pauseLock.notifyAll();
        }
    }

    synchronized void requestStop() {
        if (!started) {
            return;
        }
        running = false;
        wakePausedWorker();
        safeStop(playbackRecord);
        safeStop(microphoneRecord);
    }

    synchronized void awaitStopped() throws IOException {
        if (!started) {
            return;
        }
        waitForWorker();

        Exception audioFailure = failure.get();
        if (audioFailure != null) {
            throw new IOException("Internal audio capture failed", audioFailure);
        }
    }

    synchronized void stop() throws IOException {
        requestStop();
        awaitStopped();
    }

    synchronized void release() {
        boolean waitForCaptureWorker = worker != null && worker.isAlive();
        running = false;
        wakePausedWorker();
        safeStop(playbackRecord);
        safeStop(microphoneRecord);
        if (waitForCaptureWorker) {
            try {
                waitForWorker();
            } catch (IOException error) {
                Log.w(TAG, "Unable to stop the audio worker cleanly", error);
            }
        }
        safeRelease(playbackRecord);
        safeRelease(microphoneRecord);
        playbackRecord = null;
        microphoneRecord = null;
        if (worker == null || !worker.isAlive()) {
            finishCodecAndOutput();
        }
    }

    private void waitForWorker() throws IOException {
        Thread activeWorker = worker;
        if (activeWorker == null) {
            return;
        }
        try {
            activeWorker.join(WORKER_JOIN_TIMEOUT_MS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while stopping audio", error);
        }
        if (activeWorker.isAlive()) {
            activeWorker.interrupt();
            throw new IOException("Timed out while stopping audio capture");
        }
        worker = null;
    }

    private void cleanUpFailedStart() {
        running = false;
        wakePausedWorker();
        safeStop(playbackRecord);
        safeStop(microphoneRecord);
        safeRelease(playbackRecord);
        safeRelease(microphoneRecord);
        playbackRecord = null;
        microphoneRecord = null;
        finishCodecAndOutput();
    }

    private void wakePausedWorker() {
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
    }

    private void resetCaptureClockSession() {
        captureSessionFrames = 0L;
        lastEstimatedBufferEndNanos = Long.MIN_VALUE;
    }

    private void finishCodecAndOutput() {
        if (!codecResourcesFinished.compareAndSet(false, true)) {
            return;
        }
        if (codec != null) {
            if (codecStarted) {
                try {
                    codec.stop();
                } catch (Exception error) {
                    Log.w(TAG, "Unable to stop AAC encoder", error);
                }
            }
            try {
                codec.release();
            } catch (Exception error) {
                Log.w(TAG, "Unable to release AAC encoder", error);
            }
            codec = null;
        }
        if (outputTrack != null) {
            outputTrack.finish();
        }
    }

    private static void safeStop(AudioRecord record) {
        if (record == null) {
            return;
        }
        try {
            if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop();
            }
        } catch (IllegalStateException ignored) {
            // The audio server may already have stopped this recorder.
        }
    }

    private static void safeRelease(AudioRecord record) {
        if (record == null) {
            return;
        }
        try {
            record.release();
        } catch (Exception ignored) {
            // Best effort during cleanup.
        }
    }
}
