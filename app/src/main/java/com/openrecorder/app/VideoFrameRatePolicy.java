package com.openrecorder.app;

/** Resolves user frame-rate choices into safe encoder fallback candidates. */
final class VideoFrameRatePolicy {
    private static final float DISPLAY_RATE_TOLERANCE_FPS = 1.0f;
    private static final int[] SUPPORTED_RATES_DESCENDING = {
            RecordingOptions.VIDEO_FRAME_RATE_120_FPS,
            RecordingOptions.VIDEO_FRAME_RATE_90_FPS,
            RecordingOptions.VIDEO_FRAME_RATE_60_FPS,
            RecordingOptions.VIDEO_FRAME_RATE_30_FPS,
    };

    private VideoFrameRatePolicy() {
    }

    static int resolvePreferredFrameRate(int requestedFrameRate, float sourceRefreshRate) {
        int normalized = RecordingOptions.normalizeVideoFrameRate(requestedFrameRate);
        if (normalized != RecordingOptions.VIDEO_FRAME_RATE_AUTO) {
            return normalized;
        }
        if (!Float.isFinite(sourceRefreshRate) || sourceRefreshRate <= 0f) {
            return RecordingOptions.VIDEO_FRAME_RATE_30_FPS;
        }
        for (int frameRate : SUPPORTED_RATES_DESCENDING) {
            if (sourceRefreshRate + DISPLAY_RATE_TOLERANCE_FPS >= frameRate) {
                return frameRate;
            }
        }
        return RecordingOptions.VIDEO_FRAME_RATE_30_FPS;
    }

    static int[] fallbackCandidates(int requestedFrameRate, float sourceRefreshRate) {
        int preferredFrameRate = resolvePreferredFrameRate(requestedFrameRate, sourceRefreshRate);
        int candidateCount = 0;
        for (int frameRate : SUPPORTED_RATES_DESCENDING) {
            if (frameRate <= preferredFrameRate) {
                candidateCount++;
            }
        }

        int[] candidates = new int[candidateCount];
        int index = 0;
        for (int frameRate : SUPPORTED_RATES_DESCENDING) {
            if (frameRate <= preferredFrameRate) {
                candidates[index++] = frameRate;
            }
        }
        return candidates;
    }
}
