package ch.m3ts.tracker.init;

import android.content.Intent;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import ch.m3ts.pubnub.PubNubFactory;
import ch.m3ts.pubnub.TrackerPubNub;
import ch.m3ts.tracker.visualization.CameraPreviewActivity;
import ch.m3ts.tracker.visualization.live.LiveActivity;
import cz.fmo.R;

/**
 * "Main Activity" of the Tracker device.
 *
 * First it scans the QR-Code generated by the Display device and processes the there stored
 * information (PubNub Room ID and Match Settings).
 *
 * Then it waits until the handler tells this activity to switch to the LiveActivity -> the players
 * have decided to start the game.
 */
@SuppressWarnings("squid:S110")
public final class InitTrackerActivity extends CameraPreviewActivity implements SensorEventListener {

    private static final String CORNERS_PARAM = "CORNERS_UNSORTED";
    private static final String MATCH_TYPE_PARAM = "MATCH_TYPE";
    private static final String SERVING_SIDE_PARAM = "SERVING_SIDE";
    private static final String MATCH_ID = "MATCH_ID";
    private static final int MAX_ALLOWED_ADJUSTMENT_OFFSET = 3;
    private static final int MAX_ALLOWED_ADJUSTMENT_OFFSET_TOP = 20;
    private TrackerPubNub trackerPubNub;
    private SensorManager mSensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private float[] mGravity;
    private float[] mGeomagnetic;
    private TextView pitchText;
    private TextView rollText;
    private TextView adjustDeviceText;
    private ImageView adjustDeviceIcon;
    private View adjustDeviceOverlay;
    private View qrCodeOverlay;
    private long countSensorRefresh;

    @Override
    protected void onCreate(android.os.Bundle savedBundle) {
        super.onCreate(savedBundle);
        cameraCallback = new InitTrackerHandler(this);
    }

    /**
     * Responsible for querying and acquiring camera permissions. Whatever the response will be,
     * the permission request could result in the application being paused and resumed. For that
     * reason, requesting permissions at any later point, including in onResume(), might cause an
     * infinite loop.
     */
    @Override
    protected void onStart() {
        super.onStart();
        FrameLayout layout = findViewById(R.id.frameLayout);
        pitchText = findViewById(R.id.pitch);
        rollText = findViewById(R.id.roll);
        adjustDeviceText = findViewById(R.id.adjust_device_info_text);
        adjustDeviceOverlay = findViewById(R.id.adjust_device_overlay);
        qrCodeOverlay = findViewById(R.id.scan_overlay);
        adjustDeviceIcon = findViewById(R.id.adjust_device_info_icon);
        ViewGroup.LayoutParams params = layout.getLayoutParams();
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        params.height = size.y;
        params.width = size.x;
        layout.setLayoutParams(params);
    }

    @Override
    protected void onResume() {
        super.onResume();
        init();
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mSensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void setCurrentContentView() {
        setContentView(R.layout.activity_initialize);
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

    void enterPubNubRoom(String roomId) {
        mSensorManager.unregisterListener(this);
        this.trackerPubNub = PubNubFactory.createTrackerPubNub(this, roomId);
        this.trackerPubNub.setInitTrackerCallback((InitTrackerCallback) this.cameraCallback);
    }

    void switchToLiveActivity(String selectedMatchId, int selectedMatchType, int selectedServingSide, int[] tableCorners) {
        if (this.trackerPubNub != null) this.trackerPubNub.unsubscribe();
        Intent intent = new Intent(this, LiveActivity.class);
        Bundle bundle = new Bundle();
        bundle.putString(MATCH_ID, selectedMatchId);
        bundle.putInt(MATCH_TYPE_PARAM, selectedMatchType);
        bundle.putInt(SERVING_SIDE_PARAM, selectedServingSide);
        bundle.putIntArray(CORNERS_PARAM, tableCorners);
        intent.putExtras(bundle);
        startActivity(intent);
        this.finish();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = event.values;
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = event.values;
        if (mGravity != null && mGeomagnetic != null) {
            countSensorRefresh++;
            float rot[] = new float[9];
            float in[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(rot, in, mGravity, mGeomagnetic);
            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(rot, orientation);
                long rollDegrees = Math.round(Math.toDegrees(orientation[2]));
                long pitchDegrees = Math.round(Math.toDegrees(orientation[1]));
                rollText.setText(String.format(getString(R.string.adjustDeviceRollDegreeText), Math.abs(rollDegrees)));
                pitchText.setText(String.format(getString(R.string.adjustDeviceTiltDegreeText), pitchDegrees));
                if(countSensorRefresh % 50 == 0) {
                    if(pitchDegrees > MAX_ALLOWED_ADJUSTMENT_OFFSET) {
                        changeAdjustmentInfo(R.drawable.tilt_right, R.string.adjustDeviceTiltRightText);
                    } else if (pitchDegrees < -1 * MAX_ALLOWED_ADJUSTMENT_OFFSET) {
                        changeAdjustmentInfo(R.drawable.tilt_left, R.string.adjustDeviceTiltLeftText);
                    } else if (rollDegrees < -90 - MAX_ALLOWED_ADJUSTMENT_OFFSET_TOP) {
                        changeAdjustmentInfo(R.drawable.roll_back, R.string.adjustDeviceRollBottomText);
                    } else if (rollDegrees > -90 + MAX_ALLOWED_ADJUSTMENT_OFFSET) {
                        changeAdjustmentInfo(R.drawable.roll_front, R.string.adjustDeviceRollTopText);
                    } else {
                        adjustDeviceOverlay.setVisibility(View.INVISIBLE);
                        qrCodeOverlay.setVisibility(View.VISIBLE);
                        ((InitTrackerHandler) cameraCallback).setIsReadingQRCode(true);
                    }
                    countSensorRefresh = 0;
                }
            }
        }
    }

    private void changeAdjustmentInfo(int iconId, int messageId) {
        adjustDeviceIcon.setImageDrawable(getDrawable(iconId));
        adjustDeviceText.setText(this.getString(messageId));
        qrCodeOverlay.setVisibility(View.INVISIBLE);
        ((InitTrackerHandler) cameraCallback).setIsReadingQRCode(false);
        adjustDeviceOverlay.setVisibility(View.VISIBLE);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // no need
    }
}
