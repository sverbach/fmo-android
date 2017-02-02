package cz.fmo.graphics;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;

/**
 * A simple, specialized OpenGL ES 2.0 renderer for drawing whole surface textures
 * (e.g. android.graphics.SurfaceTexture). Usage:
 * <ul>
 * <li>initialize OpenGL, e.g. create an instance of the EGL class</li>
 * <li>create an instance of Renderer</li>
 * <li>bind the renderer's surface texture to a source, e.g.
 * camera.setPreviewTexture(renderer.getInputTexture())</li>
 * </ul>
 * When a new frame is available in the surface texture:
 * <ul>
 * <li>make some surface current for writing, e.g. using EGL.Surface.makeCurrent()</li>
 * <li>call the drawRectangle() method to draw the surface texture onto the current surface</li>
 * </ul>
 * To clean up, call release().
 */
public class Renderer implements SurfaceTexture.OnFrameAvailableListener {
    private static final int TEXTURE_TYPE = GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
    private static final String VERTEX_SOURCE = "" +
            "uniform mat4 uvMat;\n" +
            "attribute vec4 pos;\n" +
            "attribute vec4 uv1;\n" +
            "varying vec2 uv2;\n" +
            "void main() {\n" +
            "    gl_Position = pos;\n" +
            "    uv2 = (uvMat * uv1).xy;\n" +
            "}\n";
    private static final String FRAGMENT_SOURCE = "" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 uv2;\n" +
            "uniform samplerExternalOES tex;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(tex, uv2);\n" +
            "}\n";
    private static final float[] RECTANGLE_POS_DATA = {-1, -1, 1, -1, -1, 1, 1, 1};
    private static final float[] RECTANGLE_UV_DATA = {0, 0, 1, 0, 0, 1, 1, 1};
    private static final java.nio.Buffer RECTANGLE_POS = GL.makeBuffer(RECTANGLE_POS_DATA);
    private static final java.nio.Buffer RECTANGLE_UV = GL.makeBuffer(RECTANGLE_UV_DATA);

    private final int mId;
    private final int mTexId;
    private final Shader mVert;
    private final Shader mFrag;
    private final int mLoc_pos;
    private final int mLoc_uv1;
    private final int mLoc_uvMat;
    private final SurfaceTexture mInputTex;
    private final float[] mTemp = new float[16];
    private final Callback mCb;
    private boolean mReleased = false;

    public Renderer(Callback cb) throws RuntimeException {
        mId = GLES20.glCreateProgram();
        GL.checkError();
        mVert = new Shader(GLES20.GL_VERTEX_SHADER, VERTEX_SOURCE);
        mFrag = new Shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE);
        GLES20.glAttachShader(mId, mVert.getId());
        GLES20.glAttachShader(mId, mFrag.getId());
        GLES20.glLinkProgram(mId);

        int[] result = {0};
        GLES20.glGetProgramiv(mId, GLES20.GL_LINK_STATUS, result, 0);
        if (result[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(mId);
            release();
            throw new RuntimeException(log);
        }

        mLoc_pos = GLES20.glGetAttribLocation(mId, "pos");
        mLoc_uv1 = GLES20.glGetAttribLocation(mId, "uv1");
        mLoc_uvMat = GLES20.glGetUniformLocation(mId, "uvMat");

        GLES20.glGenTextures(1, result, 0);
        GL.checkError();
        mTexId = result[0];
        GLES20.glBindTexture(TEXTURE_TYPE, mTexId);
        GL.checkError();
        GLES20.glTexParameterf(TEXTURE_TYPE, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(TEXTURE_TYPE, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(TEXTURE_TYPE, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(TEXTURE_TYPE, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GL.checkError();

        mInputTex = new SurfaceTexture(mTexId);
        mInputTex.setOnFrameAvailableListener(this);
        mCb = cb;
    }

    public void release() {
        if (mReleased) return;
        mReleased = true;
        if (mInputTex != null) mInputTex.release();
        if (mTexId != 0) {
            int[] textures = {mTexId};
            GLES20.glDeleteTextures(1, textures, 0);
        }
        if (mVert != null) mVert.release();
        if (mFrag != null) mFrag.release();
        GLES20.glDeleteProgram(mId);
    }

    /**
     * Provides the input surface texture. Bind this texture to some source of images (e.g. a camera
     * preview stream). The renderer will draw the contents of this surface texture.
     *
     * @return an internal input surface texture
     */
    public SurfaceTexture getInputTexture() {
        return mInputTex;
    }

    /**
     * @return timestamp of last input frame, in nanoseconds
     */
    public long getTimestamp() {
        return mInputTex.getTimestamp();
    }

    /**
     * Draw the whole input surface texture onto the current output surface.
     */
    public void drawRectangle() {
        if (mReleased) throw new RuntimeException("Draw after release");
        mInputTex.updateTexImage();
        mInputTex.getTransformMatrix(mTemp);
        GLES20.glUseProgram(mId);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(TEXTURE_TYPE, mTexId);
        GLES20.glEnableVertexAttribArray(mLoc_pos);
        GLES20.glVertexAttribPointer(mLoc_pos, 2, GLES20.GL_FLOAT, false, 8, RECTANGLE_POS);
        GLES20.glEnableVertexAttribArray(mLoc_uv1);
        GLES20.glVertexAttribPointer(mLoc_uv1, 2, GLES20.GL_FLOAT, false, 8, RECTANGLE_UV);
        GLES20.glUniformMatrix4fv(mLoc_uvMat, 1, false, mTemp, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glUseProgram(0);
        GL.checkError();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mCb.onFrameAvailable();
    }

    public interface Callback {
        void onFrameAvailable();
    }
}