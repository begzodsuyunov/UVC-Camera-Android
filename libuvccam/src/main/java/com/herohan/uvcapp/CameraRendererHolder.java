package com.herohan.uvcapp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;

import com.serenegiant.opengl.EGLBase;
import com.serenegiant.opengl.EGLTask;
import com.serenegiant.opengl.GLDrawer2D;
import com.serenegiant.opengl.renderer.MirrorMode;
import com.serenegiant.opengl.renderer.RendererHolder;
import com.serenegiant.opengl.renderer.RendererHolderCallback;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

class CameraRendererHolder extends RendererHolder implements ICameraRendererHolder {
    private static final boolean DEBUG = false;
    private static final String TAG = CameraRendererHolder.class.getSimpleName();

    private CaptureHolder mCaptureHolder;

    public CameraRendererHolder(int width, int height, @Nullable RendererHolderCallback callback) {
        this(width, height,
                null, EGLTask.EGL_FLAG_RECORDABLE, 3,
                callback);
    }

    public CameraRendererHolder(int width, int height, EGLBase.IContext sharedContext, int flags, int maxClientVersion, @Nullable RendererHolderCallback callback) {
        super(width, height, sharedContext, flags, maxClientVersion, callback);
    }

    @Override
    protected void onPrimarySurfaceCreate(Surface surface) {
        super.onPrimarySurfaceCreate(surface);
        mRendererHandler.post(() -> mCaptureHolder = new CaptureHolder());
    }

    public void drawOSDOnPreview() {
        // Assuming you have already initialized the canvas and set its size
        Canvas canvas = mPrimarySurface.lockCanvas(null);
        if (canvas != null) {
            try {
                // Clear the canvas before drawing the camera preview and OSD
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

                // Draw the camera preview (solid color in this example)
                Paint previewPaint = new Paint();
                previewPaint.setColor(Color.TRANSPARENT); // Replace this with your camera preview implementation
                Rect previewRect = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());
                canvas.drawRect(previewRect, previewPaint);

                // Draw the OSD (time and camera name)
                Paint timePaint = new Paint();
                timePaint.setColor(Color.WHITE);
                timePaint.setTextSize(48); // Adjust the text size as needed
                timePaint.setTypeface(Typeface.DEFAULT_BOLD);

                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                String currentTime = sdf.format(new Date());
                float timeX = 20; // Adjust the X-coordinate of the time text
                float timeY = 50; // Adjust the Y-coordinate of the time text
                canvas.drawText(currentTime, timeX, timeY, timePaint);

                Paint namePaint = new Paint();
                namePaint.setColor(Color.WHITE);
                namePaint.setTextSize(32); // Adjust the text size as needed
                namePaint.setTypeface(Typeface.DEFAULT_BOLD);

                String cameraName = "My Camera"; // Replace this with the actual camera name
                float nameX = 20; // Adjust the X-coordinate of the camera name text
                float nameY = 100; // Adjust the Y-coordinate of the camera name text
                canvas.drawText(cameraName, nameX, nameY, namePaint);

                // Unlock and post the primary canvas
                mPrimarySurface.unlockCanvasAndPost(canvas);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    protected void onPrimarySurfaceDestroy() {
        super.onPrimarySurfaceDestroy();
        mRendererHandler.post(() -> {
            if (mCaptureHolder != null) {
                mCaptureHolder.release();
                mCaptureHolder = null;
            }
        });
    }

    @Override
    public void captureImage(OnImageCapturedCallback callback) {
        mRendererHandler.post(() -> {
            // Capture still image
            ImageRawData data = mCaptureHolder.captureImageRawData();
            callback.onCaptureSuccess(data);
        });
    }

    private class CaptureHolder {
        EGLBase mCaptureEglBase;
        EGLBase.IEglSurface mCaptureSurface;
        GLDrawer2D mCaptureDrawer;

        int mWidth = -1;
        int mHeight = -1;
        ByteBuffer mBuf = null;

        public CaptureHolder() {
            mCaptureEglBase = EGLBase.createFrom(getContext(), 3,
                    false, 0, false);
            mCaptureSurface = mCaptureEglBase.createOffscreen(
                    mVideoWidth, mVideoHeight);
            mCaptureDrawer = new GLDrawer2D(true);
        }

        public ImageRawData captureImageRawData() {
            if (DEBUG) Log.v(TAG, "#captureImageData:start");
            ImageRawData data = null;
            if ((mBuf == null)
                    || (mWidth != mVideoWidth)
                    || (mHeight != mVideoHeight)) {

                mWidth = mVideoWidth;
                mHeight = mVideoHeight;
                mBuf = ByteBuffer.allocateDirect(mWidth * mHeight * 4);
                mBuf.order(ByteOrder.LITTLE_ENDIAN);
                if (mCaptureSurface != null) {
                    mCaptureSurface.release();
                    mCaptureSurface = null;
                }
                mCaptureSurface = mCaptureEglBase.createOffscreen(mWidth, mHeight);
            }
            if ((mWidth > 0) && (mHeight > 0)) {
                float[] mvpMatrix = Arrays.copyOf(mMvpMatrix, 16);
                float[] mirrorMatrix = new float[16];
                Matrix.setIdentityM(mirrorMatrix, 0);
                //Must flip up-side down otherwise our output will look upside down relative to what appears on screen
                RendererHolder.setMirrorMode(mirrorMatrix, MirrorMode.MIRROR_VERTICAL);

                Matrix.multiplyMM(mvpMatrix, 0, mirrorMatrix, 0, mvpMatrix, 0);
                mCaptureDrawer.setMvpMatrix(mvpMatrix, 0);

                mCaptureSurface.makeCurrent();
                mCaptureDrawer.draw(mTexId, mTexMatrix, 0);
                mCaptureSurface.swap();
                mBuf.clear();
                GLES20.glReadPixels(0, 0, mWidth, mHeight,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mBuf);

                makeCurrent();

                byte[] bytes = new byte[mBuf.capacity()];
                mBuf.rewind();
                mBuf.get(bytes);

                data = new ImageRawData(bytes, mWidth, mHeight);
            } else {
                Log.w(TAG, "#captureImageData:unexpectedly width/height is zero");
            }
            if (DEBUG) Log.i(TAG, "#captureImageData:end");
            return data;
        }

        public void release() {
            if (mCaptureDrawer != null) {
                mCaptureDrawer.release();
                mCaptureDrawer = null;
            }
            if (mCaptureSurface != null) {
                mCaptureSurface.makeCurrent();
                mCaptureSurface.release();
                mCaptureSurface = null;
            }
            if (mCaptureEglBase != null) {
                mCaptureEglBase.release();
                mCaptureEglBase = null;
            }
        }
    }
}
