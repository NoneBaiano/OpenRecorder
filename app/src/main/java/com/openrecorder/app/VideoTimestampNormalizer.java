package com.openrecorder.app;

/** Enforces a full-frame gap when a vendor encoder repeats presentation timestamps. */
final class VideoTimestampNormalizer {
    private VideoTimestampNormalizer() {
    }

    static long frameIntervalUs(int frameRate) {
        if (frameRate <= 0) {
            throw new IllegalArgumentException("Frame rate must be positive");
        }
        return Math.max(1L, 1_000_000L / frameRate);
    }

    static long ensureFrameSpacing(long candidateUs, long previousUs, int frameRate) {
        if (previousUs < 0L || candidateUs > previousUs) {
            return candidateUs;
        }
        long intervalUs = frameIntervalUs(frameRate);
        if (previousUs > Long.MAX_VALUE - intervalUs) {
            return Long.MAX_VALUE;
        }
        return previousUs + intervalUs;
    }
}
