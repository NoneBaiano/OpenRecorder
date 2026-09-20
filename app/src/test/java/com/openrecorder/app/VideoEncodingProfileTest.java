package com.openrecorder.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class VideoEncodingProfileTest {
    @Test
    public void nativeResolutionKeepsSourceDimensions() {
        VideoEncodingProfile.Layout layout = resolve(
                RecordingOptions.VIDEO_RESOLUTION_NATIVE,
                false,
                RecordingOptions.VIDEO_BITRATE_AUTO);

        assertLayout(layout, 1080, 2340, 1080, 2340);
    }

    @Test
    public void resolutionPresetsPreserveAspectRatioAndCapLongEdge() {
        VideoEncodingProfile.Layout fullHd = resolve(
                RecordingOptions.VIDEO_RESOLUTION_1080P,
                false,
                RecordingOptions.VIDEO_BITRATE_AUTO);
        VideoEncodingProfile.Layout hd = resolve(
                RecordingOptions.VIDEO_RESOLUTION_720P,
                false,
                RecordingOptions.VIDEO_BITRATE_AUTO);
        VideoEncodingProfile.Layout sd = resolve(
                RecordingOptions.VIDEO_RESOLUTION_480P,
                false,
                RecordingOptions.VIDEO_BITRATE_AUTO);

        assertLayout(fullHd, 886, 1920, 886, 1920);
        assertLayout(hd, 590, 1280, 590, 1280);
        assertLayout(sd, 394, 854, 394, 854);
    }

    @Test
    public void letterboxingProducesA16By9CanvasWithoutCropping() {
        VideoEncodingProfile.Layout layout = resolve(
                RecordingOptions.VIDEO_RESOLUTION_1080P,
                true,
                RecordingOptions.VIDEO_BITRATE_AUTO);

        assertLayout(layout, 1080, 1920, 886, 1920);
    }

    @Test
    public void nativeLetterboxingKeepsNativeContentPixels() {
        VideoEncodingProfile.Layout layout = resolve(
                RecordingOptions.VIDEO_RESOLUTION_NATIVE,
                true,
                RecordingOptions.VIDEO_BITRATE_AUTO);

        assertLayout(layout, 1316, 2340, 1080, 2340);
        assertEquals(7_581_600, layout.videoBitrate);
    }

    @Test
    public void letterboxPixelsDoNotIncreaseAutomaticBitrate() {
        VideoEncodingProfile.Layout regular = resolve(
                RecordingOptions.VIDEO_RESOLUTION_1080P,
                false,
                RecordingOptions.VIDEO_BITRATE_AUTO);
        VideoEncodingProfile.Layout letterboxed = resolve(
                RecordingOptions.VIDEO_RESOLUTION_1080P,
                true,
                RecordingOptions.VIDEO_BITRATE_AUTO);

        assertEquals(regular.videoBitrate, letterboxed.videoBitrate);
        assertEquals(5_103_360, letterboxed.videoBitrate);
    }

    @Test
    public void automaticBitrateFallsMonotonicallyWithResolution() {
        int nativeBitrate = resolve(RecordingOptions.VIDEO_RESOLUTION_NATIVE, false, 0)
                .videoBitrate;
        int fullHdBitrate = resolve(RecordingOptions.VIDEO_RESOLUTION_1080P, false, 0)
                .videoBitrate;
        int hdBitrate = resolve(RecordingOptions.VIDEO_RESOLUTION_720P, false, 0)
                .videoBitrate;
        int sdBitrate = resolve(RecordingOptions.VIDEO_RESOLUTION_480P, false, 0)
                .videoBitrate;

        assertEquals(7_581_600, nativeBitrate);
        assertEquals(5_103_360, fullHdBitrate);
        assertEquals(2_265_600, hdBitrate);
        assertEquals(1_009_428, sdBitrate);
        assertTrue(nativeBitrate > fullHdBitrate);
        assertTrue(fullHdBitrate > hdBitrate);
        assertTrue(hdBitrate > sdBitrate);
    }

    @Test
    public void forcedLandscapeIsAppliedBeforeLetterboxing() {
        VideoEncodingProfile.Layout layout = VideoEncodingProfile.resolve(
                1080,
                2340,
                RecordingOptions.ORIENTATION_LANDSCAPE,
                RecordingOptions.VIDEO_RESOLUTION_1080P,
                true,
                RecordingOptions.VIDEO_BITRATE_AUTO);

        assertLayout(layout, 1920, 1080, 1920, 886);
    }

    @Test
    public void presetDoesNotUpscaleSmallerContent() {
        VideoEncodingProfile.Layout layout = VideoEncodingProfile.resolve(
                720,
                1280,
                RecordingOptions.ORIENTATION_AUTOMATIC,
                RecordingOptions.VIDEO_RESOLUTION_1080P,
                false,
                RecordingOptions.VIDEO_BITRATE_AUTO);

        assertLayout(layout, 720, 1280, 720, 1280);
    }

    @Test
    public void everyFixedBitrateIsPassedThroughExactly() {
        int[] fixedBitrates = {
                RecordingOptions.VIDEO_BITRATE_4_MBPS,
                RecordingOptions.VIDEO_BITRATE_8_MBPS,
                RecordingOptions.VIDEO_BITRATE_16_MBPS,
                RecordingOptions.VIDEO_BITRATE_24_MBPS,
        };

        for (int bitrate : fixedBitrates) {
            assertEquals(bitrate, resolve(
                    RecordingOptions.VIDEO_RESOLUTION_1080P,
                    true,
                    bitrate).videoBitrate);
        }
    }

    @Test
    public void invalidResolutionFallsBackTo1080p() {
        VideoEncodingProfile.Layout layout = resolve(
                Integer.MAX_VALUE,
                false,
                RecordingOptions.VIDEO_BITRATE_AUTO);

        assertLayout(layout, 886, 1920, 886, 1920);
    }

    @Test
    public void automaticBitrateScalesWithFrameRate() {
        VideoEncodingProfile.Layout at30Fps = VideoEncodingProfile.resolve(
                1080,
                2340,
                RecordingOptions.ORIENTATION_AUTOMATIC,
                RecordingOptions.VIDEO_RESOLUTION_720P,
                false,
                RecordingOptions.VIDEO_BITRATE_AUTO,
                RecordingOptions.VIDEO_FRAME_RATE_30_FPS);
        VideoEncodingProfile.Layout at60Fps = VideoEncodingProfile.resolve(
                1080,
                2340,
                RecordingOptions.ORIENTATION_AUTOMATIC,
                RecordingOptions.VIDEO_RESOLUTION_720P,
                false,
                RecordingOptions.VIDEO_BITRATE_AUTO,
                RecordingOptions.VIDEO_FRAME_RATE_60_FPS);
        VideoEncodingProfile.Layout at120Fps = VideoEncodingProfile.resolve(
                1080,
                2340,
                RecordingOptions.ORIENTATION_AUTOMATIC,
                RecordingOptions.VIDEO_RESOLUTION_720P,
                false,
                RecordingOptions.VIDEO_BITRATE_AUTO,
                RecordingOptions.VIDEO_FRAME_RATE_120_FPS);

        assertEquals(2_265_600, at30Fps.videoBitrate);
        assertEquals(4_531_200, at60Fps.videoBitrate);
        assertEquals(9_062_400, at120Fps.videoBitrate);
    }

    @Test
    public void automaticBitrateIsCappedAt24Mbps() {
        VideoEncodingProfile.Layout layout = VideoEncodingProfile.resolve(
                1080,
                2340,
                RecordingOptions.ORIENTATION_AUTOMATIC,
                RecordingOptions.VIDEO_RESOLUTION_NATIVE,
                false,
                RecordingOptions.VIDEO_BITRATE_AUTO,
                RecordingOptions.VIDEO_FRAME_RATE_120_FPS);

        assertEquals(24_000_000, layout.videoBitrate);
    }

    private static VideoEncodingProfile.Layout resolve(
            int resolution,
            boolean letterbox,
            int bitrate) {
        return VideoEncodingProfile.resolve(
                1080,
                2340,
                RecordingOptions.ORIENTATION_AUTOMATIC,
                resolution,
                letterbox,
                bitrate);
    }

    private static void assertLayout(
            VideoEncodingProfile.Layout layout,
            int outputWidth,
            int outputHeight,
            int contentWidth,
            int contentHeight) {
        assertEquals(outputWidth, layout.outputWidth);
        assertEquals(outputHeight, layout.outputHeight);
        assertEquals(contentWidth, layout.contentWidth);
        assertEquals(contentHeight, layout.contentHeight);
    }
}
