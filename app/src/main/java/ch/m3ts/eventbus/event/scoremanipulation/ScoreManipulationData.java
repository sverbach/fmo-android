package ch.m3ts.eventbus.event.scoremanipulation;

import ch.m3ts.tabletennis.match.game.ScoreManipulationListener;

public interface ScoreManipulationData {
    void call(ScoreManipulationListener scoreManipulationListener);
}