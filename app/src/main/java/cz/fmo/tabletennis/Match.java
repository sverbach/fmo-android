package cz.fmo.tabletennis;

import java.util.HashMap;
import java.util.Map;

public class Match implements MatchCallback {
    private Game[] games;
    private MatchType type;
    private Map<Side, Player> players;
    private Map<Side, Integer> wins;
    private UICallback uiCallback;
    private Referee referee;
    private Side serverSide;
    private ServeRules serveRules;
    private GameType gameType;

    public Match(MatchType type, GameType gameType, ServeRules serveRules, String playerLeftName, String playerRightName, UICallback uiCallback, Side startingServer) {
        this.type = type;
        this.gameType = gameType;
        this.wins = new HashMap<>();
        this.wins.put(Side.LEFT, 0);
        this.wins.put(Side.RIGHT,0);
        this.games = new Game[type.amountOfGames];
        this.players = new HashMap<>();
        this.players.put(Side.LEFT, new Player(playerLeftName));
        this.players.put(Side.RIGHT, new Player(playerRightName));
        this.uiCallback = uiCallback;
        this.serveRules = serveRules;
        this.referee = new Referee(startingServer);
        this.serverSide = startingServer;
        startNewGame(true);
    }

    void startNewGame(boolean firstInit) {
        if(!firstInit) switchServers();
        Game game = new Game(this, uiCallback, gameType, serveRules, this.serverSide);
        this.games[this.wins.get(Side.RIGHT) + this.wins.get(Side.LEFT)] = game;
        this.referee.setGame(game);
    }

    void end(Side winner) {
        this.uiCallback.onMatchEnded(this.players.get(winner).getName());
    }

    @Override
    public void onWin(Side side) {
        int win = wins.get(side) + 1;
        wins.put(side, win);
        uiCallback.onWin(side, win);
        if (isMatchOver(win)) {
            end(side);
        } else {
            startNewGame(false);
        }
    }

    public Referee getReferee() {
        return referee;
    }

    private boolean isMatchOver(int wins) {
        return (wins >= this.type.gamesNeededToWin);
    }

    private void switchServers() {
        if (this.serverSide == Side.LEFT) {
            this.serverSide = Side.RIGHT;
        } else {
            this.serverSide = Side.LEFT;
        }
    }
}
