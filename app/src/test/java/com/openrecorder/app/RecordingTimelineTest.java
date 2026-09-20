package com.openrecorder.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RecordingTimelineTest {
    @Test
    public void preservesAudioAndVideoStartOffsetsOnOneClock() {
        RecordingTimeline timeline = new RecordingTimeline(1_000_000_000L);

        assertEquals(8_000L, timeline.toPresentationTimeUs(1_008_000_000L));
        assertEquals(27_000L, timeline.toPresentationTimeUs(1_027_000_000L));
    }

    @Test
    public void removesTheSamePauseFromEveryTrack() {
        RecordingTimeline timeline = new RecordingTimeline(1_000_000_000L);
        timeline.pause(3_000_000_000L);
        timeline.resume(5_000_000_000L);

        assertFalse(timeline.shouldInclude(4_000_000_000L));
        assertTrue(timeline.shouldInclude(6_000_000_000L));
        assertEquals(3_000_000L, timeline.toPresentationTimeUs(6_000_000_000L));
    }

    @Test
    public void rejectsSamplesPastTheStopBoundary() {
        RecordingTimeline timeline = new RecordingTimeline(1_000_000_000L);
        timeline.stop(6_000_000_000L);

        assertTrue(timeline.shouldInclude(6_000_000_000L));
        assertFalse(timeline.shouldInclude(6_000_000_001L));
        assertEquals(5_000_000L, timeline.toPresentationTimeUs(7_000_000_000L));
    }

    @Test
    public void findsOnlyAudioRangesOutsideCompletedPauses() {
        RecordingTimeline timeline = new RecordingTimeline(1_000_000_000L);
        timeline.pause(1_025_000_000L);
        timeline.resume(1_060_000_000L);

        RecordingTimeline.IncludedRange beforePause = timeline.findNextIncludedRange(
                1_000_000_000L,
                1_080_000_000L);
        assertEquals(1_000_000_000L, beforePause.startNanos);
        assertEquals(1_025_000_000L, beforePause.endNanos);

        RecordingTimeline.IncludedRange afterPause = timeline.findNextIncludedRange(
                beforePause.endNanos,
                1_080_000_000L);
        assertEquals(1_060_000_000L, afterPause.startNanos);
        assertEquals(1_080_000_000L, afterPause.endNanos);
    }

    @Test
    public void excludesTheTailOfABufferCapturedAtAnActivePauseBoundary() {
        RecordingTimeline timeline = new RecordingTimeline(1_000_000_000L);
        timeline.pause(1_025_000_000L);

        RecordingTimeline.IncludedRange included = timeline.findNextIncludedRange(
                1_010_000_000L,
                1_050_000_000L);
        assertEquals(1_010_000_000L, included.startNanos);
        assertEquals(1_025_000_000L, included.endNanos);
        assertEquals(null, timeline.findNextIncludedRange(
                included.endNanos,
                1_050_000_000L));
    }
}
