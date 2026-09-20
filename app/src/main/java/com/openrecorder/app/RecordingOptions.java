package com.openrecorder.app;

final class RecordingOptions {
    static final int SAMPLE_RATE_44_1_KHZ = 44_100;
    static final int SAMPLE_RATE_48_KHZ = 48_000;
    static final int DEFAULT_SAMPLE_RATE = SAMPLE_RATE_44_1_KHZ;

    static final int VIDEO_BITRATE_AUTO = 0;
    static final int VIDEO_BITRATE_4_MBPS = 4_000_000;
    static final int VIDEO_BITRATE_8_MBPS = 8_000_000;
    static final int VIDEO_BITRATE_16_MBPS = 16_000_000;
    static final int VIDEO_BITRATE_24_MBPS = 24_000_000;
    static final int DEFAULT_VIDEO_BITRATE = VIDEO_BITRATE_AUTO;

    static final int VIDEO_RESOLUTION_NATIVE = 0;
    static final int VIDEO_RESOLUTION_1080P = 1;
    static final int VIDEO_RESOLUTION_720P = 2;
    static final int VIDEO_RESOLUTION_480P = 3;
    static final int DEFAULT_VIDEO_RESOLUTION = VIDEO_RESOLUTION_NATIVE;

    static final int VIDEO_FRAME_RATE_AUTO = 0;
    static final int VIDEO_FRAME_RATE_120_FPS = 120;
    static final int VIDEO_FRAME_RATE_90_FPS = 90;
    static final int VIDEO_FRAME_RATE_60_FPS = 60;
    static final int VIDEO_FRAME_RATE_30_FPS = 30;
    static final int DEFAULT_VIDEO_FRAME_RATE = VIDEO_FRAME_RATE_AUTO;

    static final int VIDEO_CODEC_H264 = 0;
    static final int VIDEO_CODEC_H265 = 1;
    static final int DEFAULT_VIDEO_CODEC = VIDEO_CODEC_H264;

    static final int COUNTDOWN_OFF = 0;
    static final int COUNTDOWN_3_SECONDS = 3;
    static final int COUNTDOWN_5_SECONDS = 5;
    static final int COUNTDOWN_10_SECONDS = 10;
    static final int DEFAULT_COUNTDOWN_SECONDS = COUNTDOWN_OFF;

    static final int ORIENTATION_AUTOMATIC = 0;
    static final int ORIENTATION_PORTRAIT = 1;
    static final int ORIENTATION_LANDSCAPE = 2;
    static final int DEFAULT_ORIENTATION = ORIENTATION_AUTOMATIC;

    static final String NAMING_DAY_MONTH_YEAR = "dd-MM-yyyy_HH-mm-ss";
    static final String NAMING_MONTH_DAY_YEAR = "MM-dd-yyyy_HH-mm-ss";
    static final String NAMING_YEAR_MONTH_DAY = "yyyy-MM-dd_HH-mm-ss";
    static final String NAMING_YEAR_DAY_MONTH = "yyyy-dd-MM_HH-mm-ss";
    static final String DEFAULT_NAMING_PATTERN = NAMING_DAY_MONTH_YEAR;

    private RecordingOptions() {
    }

    static int normalizeSampleRate(int value) {
        return value == SAMPLE_RATE_48_KHZ ? SAMPLE_RATE_48_KHZ : SAMPLE_RATE_44_1_KHZ;
    }

    static int normalizeVideoBitrate(int value) {
        if (value == VIDEO_BITRATE_4_MBPS
                || value == VIDEO_BITRATE_8_MBPS
                || value == VIDEO_BITRATE_16_MBPS
                || value == VIDEO_BITRATE_24_MBPS) {
            return value;
        }
        return VIDEO_BITRATE_AUTO;
    }

    static int normalizeVideoResolution(int value) {
        if (value == VIDEO_RESOLUTION_NATIVE
                || value == VIDEO_RESOLUTION_1080P
                || value == VIDEO_RESOLUTION_720P
                || value == VIDEO_RESOLUTION_480P) {
            return value;
        }
        return DEFAULT_VIDEO_RESOLUTION;
    }

    static int normalizeVideoFrameRate(int value) {
        if (value == VIDEO_FRAME_RATE_120_FPS
                || value == VIDEO_FRAME_RATE_90_FPS
                || value == VIDEO_FRAME_RATE_60_FPS
                || value == VIDEO_FRAME_RATE_30_FPS) {
            return value;
        }
        return VIDEO_FRAME_RATE_AUTO;
    }

    static int normalizeVideoCodec(int value) {
        return value == VIDEO_CODEC_H265 ? VIDEO_CODEC_H265 : VIDEO_CODEC_H264;
    }

    static int normalizeCountdownSeconds(int value) {
        if (value == COUNTDOWN_3_SECONDS
                || value == COUNTDOWN_5_SECONDS
                || value == COUNTDOWN_10_SECONDS) {
            return value;
        }
        return COUNTDOWN_OFF;
    }

    static int normalizeOrientation(int value) {
        return value == ORIENTATION_PORTRAIT || value == ORIENTATION_LANDSCAPE
                ? value
                : ORIENTATION_AUTOMATIC;
    }

    static String normalizeNamingPattern(String value) {
        if (NAMING_MONTH_DAY_YEAR.equals(value)
                || NAMING_YEAR_MONTH_DAY.equals(value)
                || NAMING_YEAR_DAY_MONTH.equals(value)) {
            return value;
        }
        return NAMING_DAY_MONTH_YEAR;
    }
}
