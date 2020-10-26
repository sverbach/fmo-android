package com.android.grafika;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.support.annotation.NonNull;
import android.view.SurfaceHolder;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import cz.fmo.Lib;
import cz.fmo.R;
import cz.fmo.data.Track;
import cz.fmo.data.TrackSet;
import cz.fmo.events.EventDetectionCallback;
import cz.fmo.events.EventDetector;
import cz.fmo.tabletennis.Match;
import cz.fmo.tabletennis.MatchType;
import cz.fmo.tabletennis.Side;
import cz.fmo.tabletennis.Table;
import cz.fmo.tabletennis.UICallback;
import cz.fmo.util.Config;

public class DebugHandler extends android.os.Handler implements EventDetectionCallback, UICallback {
    final WeakReference<DebugActivity> mActivity;
    private EventDetector eventDetector;
    private int canvasWidth;
    private int canvasHeight;
    private Paint p;
    private int videoWidth;
    private int videoHeight;
    private Config config;
    private TrackSet tracks;
    private Table table;
    private boolean hasNewTable;
    private Lib.Detection latestNearlyOutOfFrame;
    private Match match;

    public DebugHandler(@NonNull DebugActivity activity) {
        mActivity = new WeakReference<>(activity);
        tracks = TrackSet.getInstance();
        tracks.clear();
        hasNewTable = true;
        match = new Match(MatchType.BO5, "Hans", "Peter", this);
        p = new Paint();
    }

    @Override
    public void onBounce() {
        // update game logic
        // then display game state to some views
        final DebugActivity activity = mActivity.get();
        final TextView mBounceCountText = activity.getmBounceCountText();
        final int newBounceCount = Integer.parseInt(mBounceCountText.getText().toString()) + 1;
        setTextInTextView(R.id.txtPlayMovieState, match.getReferee().getState().toString());
        setTextInTextView(R.id.txtPlayMovieServing, match.getReferee().getServer().toString());
        setTextInTextView(R.id.txtBounce, String.valueOf(newBounceCount));
    }

    @Override
    public void onSideChange(final Side side) {
        final DebugActivity activity = mActivity.get();
        final TextView mShotSideText = activity.getmShotSideText();
        this.post(new Runnable() {
            @Override
            public void run() {
                String txt = "Left Side";
                if (side == Side.RIGHT) {
                    txt = "Right Side";
                }
                mShotSideText.setText(txt);
            }
        });
    }

    @Override
    public void onNearlyOutOfFrame(Lib.Detection detection) {
        latestNearlyOutOfFrame = detection;
    }

    @Override
    public void onStrikeFound(TrackSet tracks) {
        DebugActivity activity = mActivity.get();
        if (activity == null) {
            return;
        }
        if (activity.ismSurfaceHolderReady()) {
            SurfaceHolder surfaceHolder = activity.getmSurfaceTrack().getHolder();
            Canvas canvas = surfaceHolder.lockCanvas();
            if (canvas == null) {
                return;
            }
            if (this.canvasWidth == 0 || this.canvasHeight == 0) {
                canvasWidth = canvas.getWidth();
                canvasHeight = canvas.getHeight();
            }
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            if (hasNewTable) {
                drawTable(activity);
                hasNewTable = false;
            }
            drawAllTracks(canvas, tracks);
            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    @Override
    public void onTableSideChange(Side side) {

    }

    @Override
    public void onMatchEnded() {
        this.match = null;
        Lib.detectionStop();
        resetScoreTextViews();
    }

    @Override
    public void onScore(Side side, int score) {
        if (side == Side.LEFT) {
            setTextInTextView(R.id.txtPlayMovieScoreLeft, String.valueOf(score));
        } else {
            setTextInTextView(R.id.txtPlayMovieScoreRight, String.valueOf(score));
        }
    }

    @Override
    public void onWin(Side side, int wins) {
        resetScoreTextViews();
        if(side == Side.LEFT) {
            setTextInTextView(R.id.txtPlayMovieGameLeft, String.valueOf(wins));
        } else {
            setTextInTextView(R.id.txtPlayMovieGameRight, String.valueOf(wins));
        }
    }

    void init(Config config, int srcWidth, int srcHeight) {
        this.videoWidth = srcWidth;
        this.videoHeight = srcHeight;
        this.config = config;
        List<EventDetectionCallback> callbacks = new ArrayList<>();
        callbacks.add(this);
        callbacks.add(this.match.getReferee());
        eventDetector = new EventDetector(config, srcWidth, srcHeight, callbacks, tracks, this.table);
    }

    void startDetections() {
        Lib.detectionStart(this.videoWidth, this.videoHeight, this.config.getProcRes(), this.config.isGray(), eventDetector);
    }

    void stopDetections() {
        Lib.detectionStop();
    }

    void setTable(Table table) {
        if (table != null) {
            hasNewTable = true;
            this.table = table;
            eventDetector.setTable(table);
        }
    }

    void clearCanvas(SurfaceHolder surfaceHolder) {
        Canvas canvas = surfaceHolder.lockCanvas();
        if (canvas == null) {
            return;
        }
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        surfaceHolder.unlockCanvasAndPost(canvas);
    }

    void drawTable(DebugActivity activity) {
        SurfaceHolder surfaceHolderTable = activity.getmSurfaceTable().getHolder();
        Canvas canvas = surfaceHolderTable.lockCanvas();
        if (canvas == null) {
            return;
        }
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        Point[] corners = table.getCorners();
        for (int i = 0; i < corners.length; i++) {
            Point c1 = corners[i];
            Point c2;
            if (i < corners.length - 1) {
                c2 = corners[i + 1];
            } else {
                c2 = corners[0];
            }
            c1 = scalePoint(c1);
            c2 = scalePoint(c2);
            p.setColor(Color.CYAN);
            p.setStrokeWidth(5f);
            canvas.drawLine(c1.x, c1.y, c2.x, c2.y, p);
        }
        Point closeNetEnd = scalePoint(table.getCloseNetEnd());
        Point farNetEnd = scalePoint(table.getFarNetEnd());
        canvas.drawLine(closeNetEnd.x, closeNetEnd.y, farNetEnd.x, farNetEnd.y, p);
        surfaceHolderTable.unlockCanvasAndPost(canvas);
    }

    private void drawAllTracks(Canvas canvas, TrackSet set) {
        for (Track t : set.getTracks()) {
            t.updateColor();
            Lib.Detection pre = t.getLatest();
            cz.fmo.util.Color.RGBA r = t.getColor();
            int c = Color.argb(255, Math.round(r.rgba[0] * 255), Math.round(r.rgba[1] * 255), Math.round(r.rgba[2] * 255));
            p.setColor(c);
            p.setStrokeWidth(pre.radius);
            while (pre != null) {
                canvas.drawCircle(scaleX(pre.centerX), scaleY(pre.centerY), pre.radius, p);
                if (pre.predecessor != null) {
                    int x1 = scaleX(pre.centerX);
                    int x2 = scaleX(pre.predecessor.centerX);
                    int y1 = scaleY(pre.centerY);
                    int y2 = scaleY(pre.predecessor.centerY);
                    canvas.drawLine(x1, y1, x2, y2, p);
                }
                pre = pre.predecessor;
            }
        }
        drawLatestOutOfFrameDetection(canvas);
    }

    private void drawLatestOutOfFrameDetection(Canvas canvas) {
        if (latestNearlyOutOfFrame != null) {
            p.setColor(Color.rgb(255, 165, 0));
            p.setStrokeWidth(latestNearlyOutOfFrame.radius);
            canvas.drawCircle(scaleX(latestNearlyOutOfFrame.centerX), scaleY(latestNearlyOutOfFrame.centerY), latestNearlyOutOfFrame.radius, p);
        }
    }

    private int scaleY(int value) {
        float relPercentage = ((float) value) / ((float) this.videoHeight);
        return Math.round(relPercentage * this.canvasHeight);
    }

    private int scaleX(int value) {
        float relPercentage = ((float) value) / ((float) this.videoWidth);
        return Math.round(relPercentage * this.canvasWidth);
    }

    private Point scalePoint(Point p) {
        return new Point(scaleX(p.x), scaleY(p.y));
    }

    private void resetScoreTextViews() {
        setTextInTextView(R.id.txtPlayMovieScoreLeft, String.valueOf(0));
        setTextInTextView(R.id.txtPlayMovieScoreRight, String.valueOf(0));
    }

    private void setTextInTextView(int id, final String text) {
        final DebugActivity activity = mActivity.get();
        if (activity == null) {
            return;
        }
        final TextView txtView = activity.findViewById(id);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txtView.setText(text);
            }
        });
    }
}
