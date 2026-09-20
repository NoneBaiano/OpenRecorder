package com.openrecorder.app;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Serializes both encoder threads into the same MP4 while capture is active. */
final class RecordingMuxer {
    private static final long START_TIMEOUT_MS = 5_000L;

    private final MediaMuxer muxer;
    private final List<Track> tracks = new ArrayList<>();

    private boolean started;
    private boolean released;
    private IOException failure;

    RecordingMuxer(FileDescriptor output) throws IOException {
        muxer = new MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    synchronized Track createTrack(String name) {
        if (started || released) {
            throw new IllegalStateException("Tracks must be declared before the muxer starts");
        }
        Track track = new Track(this, name);
        tracks.add(track);
        return track;
    }

    synchronized void stop() throws IOException {
        if (released) {
            if (failure != null) {
                throw failure;
            }
            return;
        }

        IOException stopFailure = failure;
        try {
            if (started) {
                muxer.stop();
            } else if (stopFailure == null) {
                stopFailure = new IOException("No encoded tracks were available to finalize");
            }
        } catch (RuntimeException error) {
            if (stopFailure == null) {
                stopFailure = new IOException("Unable to finalize the recording", error);
            } else {
                stopFailure.addSuppressed(error);
            }
        } finally {
            try {
                muxer.release();
            } catch (RuntimeException error) {
                if (stopFailure != null) {
                    stopFailure.addSuppressed(error);
                } else {
                    stopFailure = new IOException("Unable to release the recording muxer", error);
                }
            }
            released = true;
            notifyAll();
        }
        failure = stopFailure;
        if (stopFailure != null) {
            throw stopFailure;
        }
    }

    synchronized void release() {
        if (released) {
            return;
        }
        try {
            if (started) {
                muxer.stop();
            }
        } catch (RuntimeException ignored) {
            // The pending MediaStore item is deleted after a cancelled recording.
        }
        try {
            muxer.release();
        } catch (RuntimeException ignored) {
            // Best effort during cancellation.
        }
        released = true;
        notifyAll();
    }

    private synchronized void setTrackFormat(Track track, MediaFormat format) throws IOException {
        verifyTrack(track);
        if (track.formatAdded) {
            throw new IOException(track.name + " output format changed more than once");
        }
        if (track.finished) {
            throw new IOException(track.name + " track finished before its format was added");
        }
        throwIfFailed();
        try {
            track.index = muxer.addTrack(format);
            track.formatAdded = true;
            startIfReady();
        } catch (RuntimeException error) {
            throw recordFailure("Unable to add the " + track.name + " track", error);
        }
    }

    private synchronized void writeSampleData(
            Track track,
            ByteBuffer data,
            MediaCodec.BufferInfo info) throws IOException {
        verifyTrack(track);
        if (!track.formatAdded) {
            throw new IOException(track.name + " samples arrived before the output format");
        }

        long deadlineNanos = System.nanoTime() + START_TIMEOUT_MS * 1_000_000L;
        while (!started && !released && failure == null) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) {
                throw recordFailure(
                        "Timed out waiting for all recording tracks",
                        new IOException("Missing encoder output format"));
            }
            try {
                long waitMillis = Math.max(1L, remainingNanos / 1_000_000L);
                wait(waitMillis);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for the recording muxer", error);
            }
        }
        throwIfFailed();
        if (released || track.finished) {
            throw new IOException("The " + track.name + " track is already closed");
        }

        try {
            muxer.writeSampleData(track.index, data, info);
            track.sampleCount++;
            track.bytesWritten += info.size;
        } catch (RuntimeException error) {
            throw recordFailure("Unable to write the " + track.name + " track", error);
        }
    }

    private synchronized void finishTrack(Track track) {
        verifyTrack(track);
        if (track.finished) {
            return;
        }
        track.finished = true;
        try {
            startIfReady();
        } catch (IOException error) {
            failure = error;
        }
        notifyAll();
    }

    private void startIfReady() throws IOException {
        if (started || released || failure != null || tracks.isEmpty()) {
            return;
        }
        boolean hasConfiguredTrack = false;
        for (Track track : tracks) {
            hasConfiguredTrack |= track.formatAdded;
            if (!track.formatAdded && !track.finished) {
                return;
            }
        }
        if (!hasConfiguredTrack) {
            return;
        }
        try {
            muxer.start();
            started = true;
            notifyAll();
        } catch (RuntimeException error) {
            throw recordFailure("Unable to start the recording muxer", error);
        }
    }

    private void verifyTrack(Track track) {
        if (track == null || track.owner != this || !tracks.contains(track)) {
            throw new IllegalArgumentException("Track does not belong to this muxer");
        }
    }

    private void throwIfFailed() throws IOException {
        if (failure != null) {
            throw failure;
        }
    }

    private IOException recordFailure(String message, Throwable cause) {
        if (failure == null) {
            failure = new IOException(message, cause);
        } else if (failure != cause) {
            failure.addSuppressed(cause);
        }
        notifyAll();
        return failure;
    }

    static final class Track {
        private final RecordingMuxer owner;
        private final String name;

        private int index = -1;
        private boolean formatAdded;
        private boolean finished;
        private long sampleCount;
        private long bytesWritten;

        private Track(RecordingMuxer owner, String name) {
            this.owner = owner;
            this.name = name;
        }

        void setFormat(MediaFormat format) throws IOException {
            owner.setTrackFormat(this, format);
        }

        void writeSampleData(ByteBuffer data, MediaCodec.BufferInfo info) throws IOException {
            owner.writeSampleData(this, data, info);
        }

        void finish() {
            owner.finishTrack(this);
        }

        long getSampleCount() {
            synchronized (owner) {
                return sampleCount;
            }
        }

        long getBytesWritten() {
            synchronized (owner) {
                return bytesWritten;
            }
        }
    }
}
