package com.openrecorder.app;

/** Resolves the frame layout and exact target bitrate sent to the video encoder. */
final class VideoEncodingProfile {
    private static final double LANDSCAPE_16_BY_9 = 16.0 / 9.0;
    private static final double PORTRAIT_9_BY_16 = 9.0 / 16.0;
    private static final int LONG_EDGE_1080P = 1_920;
    private static final int LONG_EDGE_720P = 1_280;
    private static final int LONG_EDGE_480P = 854;
    private static final int MAX_AUTOMATIC_VIDEO_BIT_RATE = 24_000_000;
    private static final int BASE_FRAME_RATE = RecordingOptions.VIDEO_FRAME_RATE_30_FPS;
    // Automatic bitrate is 0.10 bits per visible pixel per frame.
    // Black bars are excluded, and fixed bitrate choices are passed through unchanged.
    private static final int AUTOMATIC_BITRATE_DIVISOR = 10;

    private VideoEncodingProfile() {
    }

    static Layout resolve(
            int requestedWidth,
            int requestedHeight,
            int requestedOrientation,
            int requestedResolution,
            boolean force16By9Letterboxing,
            int requestedBitrate) {
        return resolve(
                requestedWidth,
                requestedHeight,
                requestedOrientation,
                requestedResolution,
                force16By9Letterboxing,
                requestedBitrate,
                BASE_FRAME_RATE);
    }

    static Layout resolve(
            int requestedWidth,
            int requestedHeight,
            int requestedOrientation,
            int requestedResolution,
            boolean force16By9Letterboxing,
            int requestedBitrate,
            int targetFrameRate) {
        if (requestedWidth <= 0 || requestedHeight <= 0) {
            throw new IllegalArgumentException("Capture dimensions must be positive");
        }

        int orientation = RecordingOptions.normalizeOrientation(requestedOrientation);
        int orientedWidth = requestedWidth;
        int orientedHeight = requestedHeight;
        boolean shouldSwap =
                (orientation == RecordingOptions.ORIENTATION_PORTRAIT
                        && orientedWidth > orientedHeight)
                        || (orientation == RecordingOptions.ORIENTATION_LANDSCAPE
                        && orientedHeight > orientedWidth);
        if (shouldSwap) {
            int originalWidth = orientedWidth;
            orientedWidth = orientedHeight;
            orientedHeight = originalWidth;
        }

        double canvasWidth = orientedWidth;
        double canvasHeight = orientedHeight;
        if (force16By9Letterboxing) {
            boolean portraitFrame = orientedHeight > orientedWidth
                    || (orientedHeight == orientedWidth
                    && orientation != RecordingOptions.ORIENTATION_LANDSCAPE);
            double targetAspect = portraitFrame ? PORTRAIT_9_BY_16 : LANDSCAPE_16_BY_9;
            double contentAspect = (double) orientedWidth / orientedHeight;
            if (contentAspect < targetAspect) {
                canvasWidth = orientedHeight * targetAspect;
            } else if (contentAspect > targetAspect) {
                canvasHeight = orientedWidth / targetAspect;
            }
        }

        int maximumLongEdge = maximumLongEdge(requestedResolution);
        double canvasLongEdge = Math.max(canvasWidth, canvasHeight);
        double scale = maximumLongEdge > 0 && canvasLongEdge > maximumLongEdge
                ? maximumLongEdge / canvasLongEdge
                : 1.0;

        int contentWidth = toEvenDimension(orientedWidth * scale);
        int contentHeight = toEvenDimension(orientedHeight * scale);
        int outputWidth = Math.max(contentWidth, toEvenDimension(canvasWidth * scale));
        int outputHeight = Math.max(contentHeight, toEvenDimension(canvasHeight * scale));
        int videoBitrate = resolveBitrate(
                contentWidth,
                contentHeight,
                requestedBitrate,
                targetFrameRate);
        return new Layout(
                outputWidth,
                outputHeight,
                contentWidth,
                contentHeight,
                videoBitrate,
                force16By9Letterboxing);
    }

    private static int resolveBitrate(
            int contentWidth,
            int contentHeight,
            int requestedBitrate,
            int targetFrameRate) {
        int normalizedBitrate = RecordingOptions.normalizeVideoBitrate(requestedBitrate);
        if (normalizedBitrate != RecordingOptions.VIDEO_BITRATE_AUTO) {
            return normalizedBitrate;
        }

        int normalizedFrameRate = RecordingOptions.normalizeVideoFrameRate(targetFrameRate);
        int bitrateFrameRate = normalizedFrameRate == RecordingOptions.VIDEO_FRAME_RATE_AUTO
                ? BASE_FRAME_RATE
                : normalizedFrameRate;
        long automaticBitrate = (long) contentWidth
                * contentHeight
                * bitrateFrameRate
                / AUTOMATIC_BITRATE_DIVISOR;
        return (int) Math.min(MAX_AUTOMATIC_VIDEO_BIT_RATE, automaticBitrate);
    }

    private static int maximumLongEdge(int requestedResolution) {
        switch (RecordingOptions.normalizeVideoResolution(requestedResolution)) {
            case RecordingOptions.VIDEO_RESOLUTION_NATIVE:
                return 0;
            case RecordingOptions.VIDEO_RESOLUTION_720P:
                return LONG_EDGE_720P;
            case RecordingOptions.VIDEO_RESOLUTION_480P:
                return LONG_EDGE_480P;
            case RecordingOptions.VIDEO_RESOLUTION_1080P:
            default:
                return LONG_EDGE_1080P;
        }
    }

    private static int toEvenDimension(double value) {
        int rounded = (int) Math.round(value);
        return Math.max(2, rounded & ~1);
    }

    static final class Layout {
        final int outputWidth;
        final int outputHeight;
        final int contentWidth;
        final int contentHeight;
        final int videoBitrate;
        final boolean letterboxed;

        Layout(
                int outputWidth,
                int outputHeight,
                int contentWidth,
                int contentHeight,
                int videoBitrate,
                boolean letterboxed) {
            this.outputWidth = outputWidth;
            this.outputHeight = outputHeight;
            this.contentWidth = contentWidth;
            this.contentHeight = contentHeight;
            this.videoBitrate = videoBitrate;
            this.letterboxed = letterboxed;
        }
    }
}
