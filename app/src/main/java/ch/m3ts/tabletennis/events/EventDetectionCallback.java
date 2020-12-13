package ch.m3ts.tabletennis.events;

import ch.m3ts.tabletennis.helper.Side;
import cz.fmo.Lib;
import cz.fmo.data.Track;

public interface EventDetectionCallback {
    void onBounce(Lib.Detection detection, Side side);
    void onSideChange(Side side);
    void onNearlyOutOfFrame(Lib.Detection detection, Side side);
    void onStrikeFound(Track track);
    void onTableSideChange(Side side);
    void onBallDroppedSideWays();
    void onTimeout();
}