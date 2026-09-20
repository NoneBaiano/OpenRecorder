package com.openrecorder.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AudioFrameClockTest {
    @Test
    public void mapsCapturedFramesToTheMonotonicTimestamp() {
        long timestampNanos = 3_600_012_000_000L;
        long timestampFrame = 172_800_000L;

        assertEquals(
                timestampNanos - 85_333_333L,
                AudioFrameClock.frameTimeNanos(
                        timestampFrame,
                        timestampNanos,
                        timestampFrame - 4_096L,
                        48_000));
    }

    @Test
    public void handlesNegativeAndPositiveFrameDeltas() {
        assertEquals(1_000_000_000L, AudioFrameClock.framesToNanos(48_000L, 48_000));
        assertEquals(-1_000_000_000L, AudioFrameClock.framesToNanos(-48_000L, 48_000));
        assertEquals(10_000_000L, AudioFrameClock.framesToNanos(441L, 44_100));
    }

    @Test
    public void roundsPauseBoundariesUpToTheNextCompleteSample() {
        assertEquals(1L, AudioFrameClock.nanosToFramesCeil(1L, 48_000));
        assertEquals(480L, AudioFrameClock.nanosToFramesCeil(10_000_000L, 48_000));
        assertEquals(481L, AudioFrameClock.nanosToFramesCeil(10_000_001L, 48_000));
    }
}
