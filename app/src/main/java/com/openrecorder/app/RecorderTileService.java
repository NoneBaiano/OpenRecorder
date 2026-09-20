package com.openrecorder.app;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;

public final class RecorderTileService extends TileService {
    private static final String TAG = "RecorderTileService";

    private volatile boolean listening;
    private final RecordingState.Listener stateListener = state -> {
        if (listening) {
            updateTile(state);
        }
    };

    static void requestStateRefresh(Context context) {
        try {
            TileService.requestListeningState(
                    context,
                    new ComponentName(context, RecorderTileService.class));
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to request a Quick Settings tile refresh", error);
        }
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        updateTile(RecordingState.get());
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        listening = true;
        RecordingState.addListener(stateListener);
        updateTile(RecordingState.get());
    }

    @Override
    public void onStopListening() {
        listening = false;
        RecordingState.removeListener(stateListener);
        super.onStopListening();
    }

    @Override
    public void onClick() {
        super.onClick();
        int state = RecordingState.get();
        if (state == RecordingState.IDLE) {
            if (isLocked()) {
                unlockAndRun(this::openCapturePermission);
            } else {
                openCapturePermission();
            }
            return;
        }
        if (state == RecordingState.SAVING) {
            updateTile(state);
            return;
        }

        showStoppingState();
        try {
            startService(RecordingService.createStopIntent(this));
        } catch (RuntimeException error) {
            Log.e(TAG, "Unable to stop recording from Quick Settings", error);
            Toast.makeText(this, R.string.stop_recording_failed, Toast.LENGTH_LONG).show();
            updateTile(RecordingState.get());
        }
    }

    @Override
    public void onDestroy() {
        listening = false;
        RecordingState.removeListener(stateListener);
        super.onDestroy();
    }

    private void openCapturePermission() {
        Intent intent = new Intent(this, RecorderTileActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    20,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapseLegacy(intent);
        }
    }

    @SuppressWarnings("deprecation")
    private void startActivityAndCollapseLegacy(Intent intent) {
        startActivityAndCollapse(intent);
    }

    private void showStoppingState() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        String description = getString(R.string.quick_settings_stopping);
        tile.setState(Tile.STATE_UNAVAILABLE);
        tile.setSubtitle(description);
        tile.setContentDescription(getString(
                R.string.quick_settings_description,
                getString(R.string.quick_settings_tile_label),
                description));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.setStateDescription(description);
        }
        tile.updateTile();
    }

    private void updateTile(int recordingState) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }

        int tileState;
        int descriptionRes;
        switch (recordingState) {
            case RecordingState.PREPARING:
                tileState = Tile.STATE_ACTIVE;
                descriptionRes = R.string.quick_settings_preparing;
                break;
            case RecordingState.RECORDING:
                tileState = Tile.STATE_ACTIVE;
                descriptionRes = R.string.quick_settings_recording;
                break;
            case RecordingState.PAUSED:
                tileState = Tile.STATE_ACTIVE;
                descriptionRes = R.string.quick_settings_paused;
                break;
            case RecordingState.SAVING:
                tileState = Tile.STATE_UNAVAILABLE;
                descriptionRes = R.string.quick_settings_saving;
                break;
            case RecordingState.IDLE:
            default:
                tileState = Tile.STATE_INACTIVE;
                descriptionRes = 0;
                break;
        }

        String label = getString(R.string.quick_settings_tile_label);
        String description = descriptionRes == 0 ? "" : getString(descriptionRes);
        boolean hasDescription = !description.isEmpty();
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_recorder_toggle));
        tile.setLabel(label);
        tile.setSubtitle(description);
        tile.setState(tileState);
        tile.setContentDescription(!hasDescription
                ? label
                : getString(R.string.quick_settings_description, label, description));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.setStateDescription(description);
        }
        tile.updateTile();
    }
}
