package com.openrecorder.app;

/** Converts AudioRecord frame positions to the monotonic clock used by video. */
final class AudioFrameClock {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private AudioFrameClock() {
    }

    static long frameTimeNanos(
            long timestampFramePosition,
            long timestampNanos,
            long targetFramePosition,
            int sampleRate) {
        return saturatingAdd(
                timestampNanos,
                framesToNanos(targetFramePosition - timestampFramePosition, sampleRate));
    }

    static long framesToNanos(long frameCount, int sampleRate) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be positive");
        }
        long wholeSeconds = frameCount / sampleRate;
        long remainingFrames = frameCount % sampleRate;
        return saturatingAdd(
                saturatingMultiply(wholeSeconds, NANOS_PER_SECOND),
                remainingFrames * NANOS_PER_SECOND / sampleRate);
    }

    static long nanosToFramesCeil(long durationNanos, int sampleRate) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("Sample rate must be positive");
        }
        if (durationNanos <= 0L) {
            return 0L;
        }
        long wholeSeconds = durationNanos / NANOS_PER_SECOND;
        long remainingNanos = durationNanos % NANOS_PER_SECOND;
        long wholeFrames = saturatingMultiply(wholeSeconds, sampleRate);
        long partialFrames = (remainingNanos * sampleRate + NANOS_PER_SECOND - 1L)
                / NANOS_PER_SECOND;
        return saturatingAdd(wholeFrames, partialFrames);
    }

    private static long saturatingAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ignored) {
            return right >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }

    private static long saturatingMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            return (left ^ right) >= 0L ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
    }
}
