package com.android.grafika;

import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.Display;
import android.view.View;

import cz.fmo.R;
import cz.fmo.SettingsActivity;
import cz.fmo.tabletennis.MatchType;
import cz.fmo.tabletennis.Side;
import cz.fmo.tabletennis.Table;
import cz.fmo.util.Config;

/**
 * The main activity, facilitating video preview, encoding and saving.
 */
public final class LiveDebugActivity extends DebugActivity {
    private static final String CORNERS_PARAM = "CORNERS_UNSORTED";
    private static final String MATCH_TYPE_PARAM = "MATCH_TYPE";
    private static final String SERVING_SIDE_PARAM = "SERVING_SIDE";
    private Config mConfig;
    private LiveDebugHandler mHandler;
    private int[] tableCorners;
    private MatchType matchType;
    private Side servingSide;

    @Override
    protected void onCreate(android.os.Bundle savedBundle) {
        super.onCreate(savedBundle);
        getDataFromIntent();
        this.mHandler = new LiveDebugHandler(this, this.servingSide, this.matchType);
        cameraCallback = this.mHandler;
        this.mConfig = new Config(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        init();
    }

    @Override
    public void init() {
        super.init();
        if (!mConfig.isDisableDetection() && ismSurfaceHolderReady()) {
            // C++ initialization
            mHandler.init(mConfig, this.getCameraWidth(), this.getCameraHeight());
            trySettingTableLocationFromIntent();
            mHandler.startDetections();
        }
    }

    @Override
    public void setCurrentContentView() {
        setContentView(R.layout.activity_live_debug);
    }

    /**
     * Called when a decision has been made regarding the camera permission. Whatever the response
     * is, the initialization procedure continues. If the permission is denied, the init() method
     * will display a proper error message on the screen.
     */
    @Override
    public void onRequestPermissionsResult(int requestID, @NonNull String[] permissionList,
                                           @NonNull int[] grantedList) {
        init();
    }

    public void onOpenMenu(View toggle) {
        setmSurfaceHolderReady(false);
        startActivity(new Intent(this, SettingsActivity.class));
    }

    /**
     * Perform cleanup after the activity has been paused.
     */
    @Override
    protected void onPause() {
        mHandler.stopDetections();
        super.onPause();
    }

    private void trySettingTableLocationFromIntent() {
        scaleCornerIntsToSelectedCamera();
        mHandler.setTable(Table.makeTableFromIntArray(tableCorners));
    }

    private void getDataFromIntent() {
        Bundle bundle = getIntent().getExtras();
        if (bundle == null) {
            throw new UnableToGetBundleException();
        }
        tableCorners = bundle.getIntArray(CORNERS_PARAM);
        servingSide = Side.values()[bundle.getInt(SERVING_SIDE_PARAM)];
        matchType = MatchType.values()[bundle.getInt(MATCH_TYPE_PARAM)];
        if (tableCorners == null) {
            throw new NoCornersInIntendFoundException();
        }
    }

    private void scaleCornerIntsToSelectedCamera() {
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        float xScale = (float) this.getCameraWidth() / size.x;
        float yScale = (float) this.getCameraHeight() / size.y;
        for (int i = 0; i < tableCorners.length; i++) {
            if (i % 2 == 0) {
                tableCorners[i] = Math.round(tableCorners[i] * xScale);
            } else {
                tableCorners[i] = Math.round(tableCorners[i] * yScale);
            }
        }
    }

    static class NoCornersInIntendFoundException extends RuntimeException {
        private static final String MESSAGE = "No corners have been found in the intent's bundle!";
        NoCornersInIntendFoundException() {
            super(MESSAGE);
        }
    }

    static class UnableToGetBundleException extends RuntimeException {
        private static final String MESSAGE = "Unable to get the bundle from Intent!";
        UnableToGetBundleException() {
            super(MESSAGE);
        }
    }
}