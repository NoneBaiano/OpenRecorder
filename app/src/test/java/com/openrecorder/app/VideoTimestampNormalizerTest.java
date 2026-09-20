package com.openrecorder.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class VideoTimestampNormalizerTest {
    @Test
    public void repeatedTimestampAdvancesByACompleteFrame() {
        assertEquals(16_666L, VideoTimestampNormalizer.frameIntervalUs(60));
        assertEquals(
                1_016_666L,
                VideoTimestampNormalizer.ensureFrameSpacing(1_000_000L, 1_000_000L, 60));
    }

    @Test
    public void naturallyIncreasingTimestampIsPreserved() {
        assertEquals(
                1_010_000L,
                VideoTimestampNormalizer.ensureFrameSpacing(1_010_000L, 1_000_000L, 60));
    }
}
