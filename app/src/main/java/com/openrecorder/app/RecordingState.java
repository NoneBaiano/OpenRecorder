package com.openrecorder.app;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

final class RecordingState {
    interface Listener {
        void onStateChanged(int state);
    }

    static final int IDLE = 0;
    static final int PREPARING = 1;
    static final int RECORDING = 2;
    static final int PAUSED = 3;
    static final int SAVING = 4;

    private static final AtomicInteger CURRENT_STATE = new AtomicInteger(IDLE);
    private static final CopyOnWriteArraySet<Listener> LISTENERS =
            new CopyOnWriteArraySet<>();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private RecordingState() {
    }

    static int get() {
        return CURRENT_STATE.get();
    }

    static void set(int state) {
        int normalizedState = normalize(state);
        if (CURRENT_STATE.getAndSet(normalizedState) == normalizedState) {
            return;
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            notifyListenersIfCurrent(normalizedState);
        } else {
            MAIN_HANDLER.post(() -> notifyListenersIfCurrent(normalizedState));
        }
    }

    static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    private static int normalize(int state) {
        return state >= IDLE && state <= SAVING ? state : IDLE;
    }

    private static void notifyListenersIfCurrent(int state) {
        if (CURRENT_STATE.get() != state) {
            return;
        }
        for (Listener listener : LISTENERS) {
            listener.onStateChanged(state);
        }
    }
}
