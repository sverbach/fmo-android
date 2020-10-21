package cz.fmo.events;

import cz.fmo.Lib;
import cz.fmo.data.Track;
import cz.fmo.data.TrackSet;
import cz.fmo.tabletennis.Table;
import cz.fmo.util.Config;
import helper.DirectionX;
import helper.DirectionY;

public class EventDetector implements Lib.Callback {
    private static final int SIDE_CHANGE_DETECTION_SPEED = 2;
    private static final int BOUNCE_DETECTION_SPEED = 1;
    private static final double PERCENTAGE_OF_NEARLY_OUT_OF_FRAME = 0.1;
    private final TrackSet tracks;
    private final EventDetectionCallback callback;
    private final int[] nearlyOutOfFrameThresholds;
    private int srcWidth;
    private int srcHeight;
    private float lastXDirection;
    private float lastYDirection;
    private long detectionCount;
    private Table table;

    public EventDetector(Config config, int srcWidth, int srcHeight, EventDetectionCallback callback, TrackSet tracks) {
        this.srcHeight = srcHeight;
        this.srcWidth = srcWidth;
        this.callback = callback;
        this.tracks = tracks;
        this.nearlyOutOfFrameThresholds = new int[] {
                (int) (srcWidth*PERCENTAGE_OF_NEARLY_OUT_OF_FRAME),
                (int) (srcWidth*(1-PERCENTAGE_OF_NEARLY_OUT_OF_FRAME)),
                (int) (srcHeight*PERCENTAGE_OF_NEARLY_OUT_OF_FRAME),
                (int) (srcHeight*(1-PERCENTAGE_OF_NEARLY_OUT_OF_FRAME)),
        };
        tracks.setConfig(config);
    }

    @Override
    public void log(String message) {
        // Lib logs will be ignored for now
    }

    @Override
    public void onObjectsDetected(Lib.Detection[] detections) {
        this.onObjectsDetected(detections, System.nanoTime());
    }

    public void onObjectsDetected(Lib.Detection[] detections, long detectionTime) {
        detectionCount++;
        tracks.addDetections(detections, this.srcWidth, this.srcHeight, detectionTime); // after this, object direction is updated

        if(tracks.getTracks().size() == 1) {
            Track track = tracks.getTracks().get(0);
            Lib.Detection latestDetection = track.getLatest();
            if (table != null && table.isInsideTable(latestDetection.centerX)) {
                track.setTableCrossed();
            }

            if(isInsideTable(track)) {
                callback.onStrikeFound(tracks);
            }

            if(table == null) {
                callback.onStrikeFound(tracks);
            }

            if(isNearlyOutOfFrame(latestDetection)) {
                callback.onNearlyOutOfFrame(latestDetection);
            }
            if (isOnSideChange(latestDetection.directionX)) {
                callback.onSideChange(latestDetection.directionX < 0);
            }
            else if (isBounce(latestDetection.directionY)) {
                callback.onBounce();
            }
        }
    }

    public void setTable(Table table) {
        this.table = table;
    }

    private boolean isOnSideChange(float directionX) {
        boolean isOnSideChange = false;
        if (detectionCount % SIDE_CHANGE_DETECTION_SPEED == 0) {
            if (lastXDirection != directionX) {
                isOnSideChange = true;
            }
            lastXDirection = directionX;
        }
        return isOnSideChange;
    }

    private boolean isBounce(float directionY) {
        boolean isBounce = false;
        if (detectionCount % BOUNCE_DETECTION_SPEED == 0) {
            if (lastYDirection > 0 && directionY < 0) {
                isBounce = true;
            }
        }
        lastYDirection = directionY;
        return isBounce;
    }

    private boolean isNearlyOutOfFrame(Lib.Detection detection) {
        boolean isNearlyOutOfFrame = false;
        if(detection.predecessor != null) {
            if(detection.centerX < nearlyOutOfFrameThresholds[0] && detection.directionX == DirectionX.LEFT ||
                    detection.centerX > nearlyOutOfFrameThresholds[1] && detection.directionX == DirectionX.RIGHT ||
                    detection.centerY < nearlyOutOfFrameThresholds[2] && detection.directionY == DirectionY.UP ||
                    detection.centerY > nearlyOutOfFrameThresholds[3] && detection.directionY == DirectionY.DOWN) {
                isNearlyOutOfFrame = true;
            }
        }
        return isNearlyOutOfFrame;
    }

    private boolean isInsideTable(Track track) {
        return track.hasCrossedTable();
    }

    public int[] getNearlyOutOfFrameThresholds() {
        return nearlyOutOfFrameThresholds;
    }
}