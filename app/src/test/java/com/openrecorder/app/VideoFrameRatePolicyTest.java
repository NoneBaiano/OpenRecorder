package com.openrecorder.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class VideoFrameRatePolicyTest {
    @Test
    public void everySupportedPreferenceIsPreserved() {
        int[] supportedFrameRates = {
                RecordingOptions.VIDEO_FRAME_RATE_AUTO,
                RecordingOptions.VIDEO_FRAME_RATE_120_FPS,
                RecordingOptions.VIDEO_FRAME_RATE_90_FPS,
                RecordingOptions.VIDEO_FRAME_RATE_60_FPS,
                RecordingOptions.VIDEO_FRAME_RATE_30_FPS,
        };

        for (int frameRate : supportedFrameRates) {
            assertEquals(frameRate, RecordingOptions.normalizeVideoFrameRate(frameRate));
        }
    }

    @Test
    public void invalidPreferenceFallsBackToAuto() {
        assertEquals(
                RecordingOptions.VIDEO_FRAME_RATE_AUTO,
                RecordingOptions.normalizeVideoFrameRate(Integer.MAX_VALUE));
    }

    @Test
    public void automaticModeTracksTheDisplayUsingKnownProfiles() {
        assertEquals(120, VideoFrameRatePolicy.resolvePreferredFrameRate(0, 144f));
        assertEquals(120, VideoFrameRatePolicy.resolvePreferredFrameRate(0, 119.94f));
        assertEquals(90, VideoFrameRatePolicy.resolvePreferredFrameRate(0, 90f));
        assertEquals(60, VideoFrameRatePolicy.resolvePreferredFrameRate(0, 75f));
        assertEquals(30, VideoFrameRatePolicy.resolvePreferredFrameRate(0, 24f));
        assertEquals(30, VideoFrameRatePolicy.resolvePreferredFrameRate(0, Float.NaN));
    }

    @Test
    public void fixedRateFallsBackWithoutExceedingTheRequest() {
        assertArrayEquals(
                new int[] {120, 90, 60, 30},
                VideoFrameRatePolicy.fallbackCandidates(120, 60f));
        assertArrayEquals(
                new int[] {90, 60, 30},
                VideoFrameRatePolicy.fallbackCandidates(90, 120f));
        assertArrayEquals(
                new int[] {30},
                VideoFrameRatePolicy.fallbackCandidates(30, 120f));
    }

    @Test
    public void automaticFallbacksStartAtTheDisplayRate() {
        assertArrayEquals(
                new int[] {60, 30},
                VideoFrameRatePolicy.fallbackCandidates(0, 60f));
    }
}
