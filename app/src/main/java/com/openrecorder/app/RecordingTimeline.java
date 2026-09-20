package com.openrecorder.app;

import java.util.ArrayList;
import java.util.List;

/** Maps capture-clock timestamps onto one pause-free recording timeline. */
final class RecordingTimeline {
    private final long startedAtNanos;
    private final List<PauseInterval> completedPauses = new ArrayList<>();

    private long pausedAtNanos = Long.MIN_VALUE;
    private long stoppedAtNanos = Long.MAX_VALUE;

    RecordingTimeline(long startedAtNanos) {
        this.startedAtNanos = startedAtNanos;
    }

    long getStartedAtNanos() {
        return startedAtNanos;
    }

    synchronized void pause(long timestampNanos) {
        if (pausedAtNanos != Long.MIN_VALUE || stoppedAtNanos != Long.MAX_VALUE) {
            return;
        }
        pausedAtNanos = Math.max(startedAtNanos, timestampNanos);
    }

    synchronized void resume(long timestampNanos) {
        if (pausedAtNanos == Long.MIN_VALUE || stoppedAtNanos != Long.MAX_VALUE) {
            return;
        }
        long resumedAtNanos = Math.max(pausedAtNanos, timestampNanos);
        completedPauses.add(new PauseInterval(pausedAtNanos, resumedAtNanos));
        pausedAtNanos = Long.MIN_VALUE;
    }

    synchronized void stop(long timestampNanos) {
        if (stoppedAtNanos != Long.MAX_VALUE) {
            return;
        }
        stoppedAtNanos = Math.max(startedAtNanos, timestampNanos);
        if (pausedAtNanos != Long.MIN_VALUE) {
            completedPauses.add(new PauseInterval(
                    pausedAtNanos,
                    Math.max(pausedAtNanos, stoppedAtNanos)));
            pausedAtNanos = Long.MIN_VALUE;
        }
    }

    synchronized boolean shouldInclude(long timestampNanos) {
        if (timestampNanos < startedAtNanos || timestampNanos > stoppedAtNanos) {
            return false;
        }
        for (PauseInterval pause : completedPauses) {
            if (pause.contains(timestampNanos)) {
                return false;
            }
        }
        return pausedAtNanos == Long.MIN_VALUE || timestampNanos < pausedAtNanos;
    }

    synchronized long toPresentationTimeUs(long timestampNanos) {
        long boundedTimestamp = Math.max(
                startedAtNanos,
                Math.min(timestampNanos, stoppedAtNanos));
        long pausedDurationNanos = 0L;
        for (PauseInterval pause : completedPauses) {
            pausedDurationNanos += pause.durationBefore(boundedTimestamp);
        }
        if (pausedAtNanos != Long.MIN_VALUE && boundedTimestamp > pausedAtNanos) {
            pausedDurationNanos += boundedTimestamp - pausedAtNanos;
        }
        return Math.max(
                0L,
                (boundedTimestamp - startedAtNanos - pausedDurationNanos) / 1_000L);
    }

    /** Returns the next continuous capture range that survives pause removal. */
    synchronized IncludedRange findNextIncludedRange(
            long searchStartNanos,
            long searchEndNanos) {
        if (searchEndNanos <= searchStartNanos) {
            return null;
        }

        long cursorNanos = Math.max(searchStartNanos, startedAtNanos);
        long limitNanos = searchEndNanos;
        if (stoppedAtNanos != Long.MAX_VALUE) {
            limitNanos = Math.min(limitNanos, saturatingIncrement(stoppedAtNanos));
        }
        if (cursorNanos >= limitNanos) {
            return null;
        }

        for (PauseInterval pause : completedPauses) {
            IncludedRange included = rangeBeforePause(
                    cursorNanos,
                    limitNanos,
                    pause.startNanos,
                    pause.endNanos);
            if (included != null) {
                return included;
            }
            if (cursorNanos < pause.endNanos && pause.startNanos < limitNanos) {
                cursorNanos = Math.max(cursorNanos, pause.endNanos);
            }
            if (cursorNanos >= limitNanos) {
                return null;
            }
        }

        if (pausedAtNanos != Long.MIN_VALUE) {
            IncludedRange included = rangeBeforePause(
                    cursorNanos,
                    limitNanos,
                    pausedAtNanos,
                    Long.MAX_VALUE);
            if (included != null) {
                return included;
            }
            if (cursorNanos >= pausedAtNanos) {
                return null;
            }
        }
        return new IncludedRange(cursorNanos, limitNanos);
    }

    private static IncludedRange rangeBeforePause(
            long cursorNanos,
            long limitNanos,
            long pauseStartNanos,
            long pauseEndNanos) {
        if (pauseEndNanos <= cursorNanos || pauseStartNanos >= limitNanos) {
            return null;
        }
        if (cursorNanos < pauseStartNanos) {
            return new IncludedRange(cursorNanos, Math.min(limitNanos, pauseStartNanos));
        }
        return null;
    }

    private static long saturatingIncrement(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }

    static final class IncludedRange {
        final long startNanos;
        final long endNanos;

        IncludedRange(long startNanos, long endNanos) {
            this.startNanos = startNanos;
            this.endNanos = endNanos;
        }
    }

    private static final class PauseInterval {
        final long startNanos;
        final long endNanos;

        PauseInterval(long startNanos, long endNanos) {
            this.startNanos = startNanos;
            this.endNanos = endNanos;
        }

        boolean contains(long timestampNanos) {
            return timestampNanos >= startNanos && timestampNanos < endNanos;
        }

        long durationBefore(long timestampNanos) {
            if (timestampNanos <= startNanos) {
                return 0L;
            }
            return Math.min(timestampNanos, endNanos) - startNanos;
        }
    }
}
