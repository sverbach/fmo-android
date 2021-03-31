package ch.m3ts.connection;

import android.graphics.Point;

import com.pubnub.api.Callback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import ch.m3ts.Log;
import ch.m3ts.connection.pubnub.ByteToBase64;
import ch.m3ts.connection.pubnub.JSONInfo;
import ch.m3ts.display.DisplayConnectCallback;
import ch.m3ts.display.DisplayScoreEventCallback;
import ch.m3ts.tabletennis.helper.Side;
import ch.m3ts.tabletennis.match.UICallback;

public abstract class ImplDisplayConnection extends Callback implements DisplayConnection {
    protected UICallback uiCallback;
    protected DisplayScoreEventCallback displayScoreCallback;
    protected DisplayConnectCallback displayConnectCallback;
    private int numberOfEncodedFrameParts;
    private String encodedFrameComplete;

    protected abstract void send(String event, String side);

    protected abstract void sendData(JSONObject json);

    public void onRestartMatch() {
        send("onRestartMatch", null);
    }

    public void requestStatusUpdate() {
        send("requestStatus", null);
    }

    public void onPointDeduction(Side side) {
        send("onPointDeduction", side.toString());
    }

    public void onPointAddition(Side side) {
        send("onPointAddition", side.toString());
    }

    public void onRequestTableFrame() {
        send("onRequestTableFrame", null);
    }

    public void onPause() {
        send("onPause", null);
    }

    public void onResume() {
        send("onResume", null);
    }

    public void onSelectTableCorners(Point[] tableCorners) {
        try {
            int[] corners = new int[tableCorners.length * 2];
            for (int i = 0; i < tableCorners.length; i++) {
                Log.d("Point" + i + ": " + tableCorners[i].x + ", " + tableCorners[i].y);
                corners[2 * i] = tableCorners[i].x;
                corners[2 * i + 1] = tableCorners[i].y;
            }
            JSONObject json = new JSONObject();
            json.put(JSONInfo.CORNERS, new JSONArray(corners));
            json.put(JSONInfo.EVENT_PROPERTY, "onSelectTableCorner");
            sendData(json);
        } catch (JSONException ex) {
            Log.d("Unable to send JSON to endpoint \n" + ex.getMessage());
        }
    }

    public void setUiCallback(UICallback uiCallback) {
        this.uiCallback = uiCallback;
    }

    public void setDisplayScoreEventCallback(DisplayScoreEventCallback displayScoreCallback) {
        this.displayScoreCallback = displayScoreCallback;
    }

    public void setDisplayConnectCallback(DisplayConnectCallback displayConnectCallback) {
        this.displayConnectCallback = displayConnectCallback;
    }

    protected void handleMessage(JSONObject json) {
        try {
            String event = json.getString(JSONInfo.EVENT_PROPERTY);
            if (event != null) {
                switch (event) {
                    case "onMatchEnded":
                        uiCallback.onMatchEnded(json.getString(JSONInfo.SIDE_PROPERTY));
                        break;
                    case "onScore":
                        uiCallback.onScore(Side.valueOf(json.getString(JSONInfo.SIDE_PROPERTY)), Integer.parseInt(json.getString(JSONInfo.SCORE_PROPERTY)),
                                Side.valueOf(json.getString(JSONInfo.NEXT_SERVER_PROPERTY)), Side.valueOf(json.getString(JSONInfo.LAST_SERVER_PROPERTY)));
                        break;
                    case "onWin":
                        uiCallback.onWin(Side.valueOf(json.getString(JSONInfo.SIDE_PROPERTY)), Integer.parseInt(json.getString(JSONInfo.WINS_PROPERTY)));
                        break;
                    case "onReadyToServe":
                        uiCallback.onReadyToServe(Side.valueOf(json.getString(JSONInfo.SIDE_PROPERTY)));
                        break;
                    case "onNotReadyButPlaying":
                        uiCallback.onNotReadyButPlaying();
                        break;
                    case "onStatusUpdate":
                        displayScoreCallback.onStatusUpdate(json.getString(JSONInfo.PLAYER_NAME_LEFT_PROPERTY), json.getString(JSONInfo.PLAYER_NAME_RIGHT_PROPERTY),
                                Integer.parseInt(json.getString(JSONInfo.SCORE_LEFT_PROPERTY)), Integer.parseInt(json.getString(JSONInfo.SCORE_RIGHT_PROPERTY)),
                                Integer.parseInt(json.getString(JSONInfo.WINS_LEFT_PROPERTY)), Integer.parseInt(json.getString(JSONInfo.WINS_RIGHT_PROPERTY)),
                                Side.valueOf(json.getString(JSONInfo.NEXT_SERVER_PROPERTY)), Integer.parseInt(json.getString(JSONInfo.GAMES_NEEDED_PROPERTY)));
                        break;
                    case "onConnected":
                        if (displayConnectCallback != null) {
                            displayConnectCallback.onConnected();
                        }
                        break;
                    case "onTableFrame":
                        this.handleOnTableFrame(json);
                        break;
                    default:
                        Log.d("Unhandled event received:\n" + json.toString());
                        break;
                }
            }
        } catch (Exception ex) {
            Log.d("Unable to receive JSON from endpoint \n" + ex.getMessage());
        }
    }

    private void handleOnTableFrame(JSONObject json) throws JSONException {
        int encodedFramePartIndex = json.getInt(JSONInfo.TABLE_FRAME_INDEX);
        int numberOfFramePartsSent = json.getInt(JSONInfo.TABLE_FRAME_NUMBER_OF_PARTS);
        String encodedFramePart = json.getString(JSONInfo.TABLE_FRAME);
        if (encodedFramePartIndex == 0) {
            this.numberOfEncodedFrameParts = 1;
            this.encodedFrameComplete = encodedFramePart;
            this.displayConnectCallback.onImageTransmissionStarted(numberOfFramePartsSent);
        } else {
            this.numberOfEncodedFrameParts++;
            this.encodedFrameComplete += encodedFramePart;
            this.displayConnectCallback.onImagePartReceived(encodedFramePartIndex + 1);
            if (encodedFramePartIndex == numberOfFramePartsSent - 1) {
                Log.d("number of frame parts sent: " + numberOfFramePartsSent);
                Log.d("number of frame parts received: " + numberOfEncodedFrameParts);
                if (this.numberOfEncodedFrameParts == numberOfFramePartsSent) {
                    Log.d("encodedFrame length: " + this.encodedFrameComplete.length());
                    byte[] frame = ByteToBase64.decodeToByte(this.encodedFrameComplete);
                    Log.d("frame length: " + frame.length);
                    this.displayConnectCallback.onImageReceived(frame, json.getInt(JSONInfo.TABLE_FRAME_WIDTH), json.getInt(JSONInfo.TABLE_FRAME_HEIGHT));
                } else {
                    onRequestTableFrame();
                }
            }
        }
    }
}