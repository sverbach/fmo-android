package ch.m3ts.tabletennis.events.trackselection;

import java.util.List;

import cz.fmo.Lib;
import cz.fmo.data.Track;

public class SameXDirectionTrackSelection implements TrackSelectionStrategy {
    @Override
    public Track selectTrack(List<Track> tracks, int previousDirectionX, int previousDirectionY, int previousCenterX, int previousCenterY) {
        Track selectedTrack = null;
        for (Track t : tracks) {
            Lib.Detection d = t.getLatest();
            if (d.directionX == previousDirectionX && t.hasCrossedTable()) {
                selectedTrack = t;
            }
        }
        return selectedTrack;
    }
}
