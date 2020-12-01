package cz.fmo.display;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Display;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.otaliastudios.zoom.ZoomLayout;

import java.nio.IntBuffer;

import cz.fmo.R;
import helper.ColorConversions;

public class MatchSelectCornerFragment extends android.app.Fragment implements View.OnClickListener, SurfaceHolder.Callback {
    private static final String TAG_MATCH_SCORE = "MATCH_SCORE";
    private TextView txtMaxCorners;
    private TextView txtSelectedCorners;
    private String maxCorners;
    private String selectedCorners;
    private final Point[] tableCorners = new Point[4];
    private int currentCornerIndex;
    protected int layout;
    private ZoomLayout zoomLayout;
    private FragmentReplaceCallback callback;
    private SurfaceView cornerSurface;
    private SurfaceView tableFrameSurface;
    private byte[] tableFrame;
    private Point displaySize;

    public MatchSelectCornerFragment() {
        // Required empty public constructor
        this.layout = R.layout.fragment_match_corners;
        this.currentCornerIndex = 0;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onStateChanged();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(this.layout, container, false);
        this.zoomLayout = view.findViewById(R.id.init_zoomLayout);
        this.txtMaxCorners = view.findViewById(R.id.init_cornersSelectedMaxTxt);
        this.txtSelectedCorners = view.findViewById(R.id.init_cornersSelectedTxt);
        this.txtMaxCorners.setText(maxCorners);
        this.txtSelectedCorners.setText(selectedCorners);
        this.cornerSurface = view.findViewById(R.id.init_cornerSurface);
        this.cornerSurface.getHolder().setFormat(PixelFormat.TRANSPARENT);
        this.tableFrameSurface = view.findViewById(R.id.table_frame_surface);
        view.findViewById(R.id.init_startMatch).setOnClickListener(this);
        this.tableFrameSurface.getHolder().addCallback(this);
        this.tableFrame = getArguments().getByteArray("tableFrame");
        setupOnLongTouchListener();
        setupOnRevertClickListener(view);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        FrameLayout layout = getActivity().findViewById(R.id.frameLayoutCorners);
        ViewGroup.LayoutParams params = layout.getLayoutParams();
        Display display = getActivity().getWindowManager().getDefaultDisplay();
        this.displaySize = new Point();
        display.getSize(this.displaySize);
        params.height = this.displaySize.y;
        params.width = this.displaySize.x;
        layout.setLayoutParams(params);
    }

    private void drawTableFrame() {
        Canvas canvas = this.tableFrameSurface.getHolder().lockCanvas();
        if (canvas != null) {
            Paint paint = new Paint();
            int width = 1280;
            int height = 720;
            int[] out = new int[width*height];
            ColorConversions.yuv420pToRGBA8888(out, this.tableFrame, width, height);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(IntBuffer.wrap(out));
            canvas.drawBitmap(bitmap, null, new Rect(0,0,this.displaySize.x, this.displaySize.y), null);
            this.tableFrameSurface.getHolder().unlockCanvasAndPost(canvas);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
            callback = (FragmentReplaceCallback) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement FragmentReplaceListener");
        }
    }

    public void onStateChanged() {
        maxCorners = String.valueOf(tableCorners.length);
        selectedCorners = String.valueOf(currentCornerIndex);
    }

    public void updateViews() {
        this.txtSelectedCorners.setText(selectedCorners);
        this.txtMaxCorners.setText(maxCorners);
        drawLines();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupOnLongTouchListener() {
        final GestureDetector gestureDetector = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
            public void onLongPress(MotionEvent e) {
                if (currentCornerIndex >= tableCorners.length) return;
                float x = e.getX();
                float y = e.getY();
                float zoom = zoomLayout.getZoom();
                float panX = zoomLayout.getPanX();
                float panY = zoomLayout.getPanY();
                Point absPoint = makeAbsPoint(x,y,zoom,panX,panY);
                tableCorners[currentCornerIndex] = absPoint;
                currentCornerIndex++;
                onStateChanged();
                updateViews();
                if (currentCornerIndex == tableCorners.length) {
                    final Activity activity = getActivity();
                    activity.findViewById(R.id.init_description).setVisibility(View.GONE);
                    activity.findViewById(R.id.init_startMatch).setVisibility(View.VISIBLE);
                }
            }
        });

        this.zoomLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return gestureDetector.onTouchEvent(motionEvent);
            }
        });
    }

    private void setupOnRevertClickListener(View v) {
        v.findViewById(R.id.init_revertButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentCornerIndex <= 0) return;
                if (currentCornerIndex == tableCorners.length) {
                    final Activity activity = getActivity();
                    activity.findViewById(R.id.init_description).setVisibility(View.VISIBLE);
                    activity.findViewById(R.id.init_startMatch).setVisibility(View.GONE);
                }
                currentCornerIndex--;
                tableCorners[currentCornerIndex] = null;
                onStateChanged();
                updateViews();
            }
        });
    }

    private Point makeAbsPoint(float x, float y, float zoom, float panX, float panY) {
        float absX = x/zoom + Math.abs(panX);
        float absY = y/zoom + Math.abs(panY);
        return new Point(Math.round(absX), Math.round(absY));
    }

    private void drawLines() {
        Canvas canvas = this.cornerSurface.getHolder().lockCanvas();
        ZoomLayout zoomLayout = this.zoomLayout;
        System.out.println("----------------beforedraw");
        if (canvas != null) {
            System.out.println("----------------draw");
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            Paint paint = new Paint();
            paint.setColor(Color.CYAN);

            for (int i = 0; i<this.tableCorners.length; i++) {
                Point p = this.tableCorners[i];
                if(p != null) {
                    paint.setStrokeWidth(15f);
                    Point relP = makeRelPoint(p.x, p.y, zoomLayout.getZoom(), zoomLayout.getPanX(), zoomLayout.getPanY());
                    canvas.drawCircle(relP.x, relP.y, 20f, paint);
                    drawLineIfPossible(i, paint, canvas, this.tableCorners, relP, zoomLayout);
                }
            }
            this.cornerSurface.getHolder().unlockCanvasAndPost(canvas);
        }
    }

    private void drawLineIfPossible(int i, Paint paint, Canvas canvas, Point[] corners, Point relP, ZoomLayout zoomLayout) {
        Point relPOther = null;
        if (i < corners.length-1 && corners[i+1] != null) {
            relPOther = makeRelPoint(corners[i+1].x, corners[i+1].y, zoomLayout.getZoom(), zoomLayout.getPanX(), zoomLayout.getPanY());
        } else if (i == corners.length-1 && corners[0] != null) {
            relPOther = makeRelPoint(corners[0].x, corners[0].y, zoomLayout.getZoom(), zoomLayout.getPanX(), zoomLayout.getPanY());
        }
        if (relPOther != null) {
            paint.setStrokeWidth(5f);
            canvas.drawLine(relP.x, relP.y, relPOther.x, relPOther.y, paint);
        }
    }

    private Point makeRelPoint(float absX, float absY, float zoom, float panX, float panY) {
        float relX = (absX - Math.abs(panX)) * zoom;
        float relY = (absY - Math.abs(panY)) * zoom;
        return new Point(Math.round(relX), Math.round(relY));
    }

    @Override
    public void onClick(View v) {
        ((MatchActivity) getActivity()).getPubNub().onStartMatch(tableCorners);
        Fragment fragment = new MatchScoreFragment();
        callback.replaceFragment(fragment, TAG_MATCH_SCORE);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        drawTableFrame();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }
}