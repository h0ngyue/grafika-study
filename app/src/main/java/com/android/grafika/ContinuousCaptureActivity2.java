/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.grafika;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.grafika.gles.EglCore;
import com.android.grafika.gles.FullFrameRect;
import com.android.grafika.gles.Texture2dProgram;
import com.android.grafika.gles.WindowSurface;
import com.android.grafika.kikyo.MyUtil;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import filter.MagicBaseGroupFilter;
import filter.MyBeautyFilter;
import filter.MyRGBMapFilter;
import filter.MySmoothFilter;
import filter.base.MagicCameraInputFilter;
import filter.base.gpuimage.MyGPUImageFilter;
import filter.utils.TextureRotationUtil;
import timber.log.Timber;
import utils.OpenGlUtils;

/**
 * Demonstrates capturing video into a ring buffer.  When the "capture" button is clicked,
 * the buffered video is saved.
 * <p>
 * Capturing and storing raw frames would be slow and require lots of memory.  Instead, we
 * feed the frames into the video encoder and buffer the output.
 * <p>
 * Whenever we receive a new frame from the camera, our SurfaceTexture callback gets
 * notified.  That can happen on an arbitrary thread, so we use it to send a message
 * through our Handler.  That causes us to render the new frame to the display and to
 * our video encoder.
 */
public class ContinuousCaptureActivity2 extends Activity implements SurfaceHolder.Callback,
        SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = MainActivity.TAG;

    public static final boolean use_camera_inputer = true;

    //    private static final int VIDEO_WIDTH = 1280;  // dimensions for 720p video
//    private static final int VIDEO_HEIGHT = 720;
    private static final int VIDEO_WIDTH = 640;  // dimensions for 720p video
    private static final int VIDEO_HEIGHT = 480;
    private static final int DESIRED_PREVIEW_FPS = 15;

    private EglCore mEglCore;
    private WindowSurface mDisplaySurface;
    private SurfaceTexture mCameraTexture;  // receives the output from the camera preview
    private FullFrameRect mFullFrameBlit;
    private final float[] mTmpMatrix = new float[16];
    private int mTextureId;
    private int mFrameNum = -1;

    private Camera mCamera;
    private int mCameraPreviewThousandFps;


    private MainHandler mHandler;

    private ImageView mIvDump;
    private CheckBox mCbOutput2Image, mCbUseBeauty, mCbReadPixel;
    private boolean mOutput2Image, mUseBeauty, mReadPixel;

    private MyGPUImageFilter mCameraInputFilter = new MagicCameraInputFilter();

    //    private MyGPUImageFilter mBeautyFilter = new MyBeautyFilter();
    private MyGPUImageFilter mBeautyFilter = new MySmoothFilter();
//    private MyGPUImageFilter mBeautyFilter = new MyRGBMapFilter();

    private TextView mTvFps;

    private long mFpsStartTs;

    public volatile FloatBuffer gLCubeBuffer;
    public volatile FloatBuffer gLTextureBufferMirror;
    public volatile FloatBuffer gLTextureBufferNormal;


    /**
     * Custom message handler for main UI thread.
     * <p>
     * Used to handle camera preview "frame available" notifications, and implement the
     * blinking "recording" text.  Receives callback messages from the encoder thread.
     */
    private static class MainHandler extends Handler {
        public static final int MSG_FRAME_AVAILABLE = 1;
        public static final int MSG_FILE_SAVE_COMPLETE = 2;

        private WeakReference<ContinuousCaptureActivity2> mWeakActivity;

        public MainHandler(ContinuousCaptureActivity2 activity) {
            mWeakActivity = new WeakReference<ContinuousCaptureActivity2>(activity);
        }


        @Override
        public void handleMessage(Message msg) {
            ContinuousCaptureActivity2 activity = mWeakActivity.get();
            if (activity == null) {
                Log.d(TAG, "Got message for dead activity");
                return;
            }

            switch (msg.what) {
                case MSG_FRAME_AVAILABLE: {
                    activity.drawFrame();
                    break;
                }
                case MSG_FILE_SAVE_COMPLETE: {
                    break;
                }
                default:
                    throw new RuntimeException("Unknown message " + msg.what);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_continuous_capture);

        SurfaceView sv = (SurfaceView) findViewById(R.id.continuousCapture_surfaceView);
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(this);

        mIvDump = (ImageView) findViewById(R.id.mIvDump);
        mCbOutput2Image = (CheckBox) findViewById(R.id.mCbOutput2Image);
        mCbOutput2Image.setChecked(mOutput2Image);
        mCbOutput2Image.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mOutput2Image = isChecked;
                mIvDump.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });

        mCbUseBeauty = (CheckBox) findViewById(R.id.mCbUseBeauty);
        mCbUseBeauty.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mUseBeauty = isChecked;
            }
        });

        mCbReadPixel = (CheckBox) findViewById(R.id.mCbReadPixel);
        mCbReadPixel.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mReadPixel = isChecked;
            }
        });

        mTvFps = (TextView) findViewById(R.id.mTvFps);


        mHandler = new MainHandler(this);

        gLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLCubeBuffer.put(TextureRotationUtil.CUBE).position(0);

        gLTextureBufferMirror = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLTextureBufferMirror.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);

        gLTextureBufferNormal = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLTextureBufferMirror.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);
    }

    @Override
    public File getFilesDir() {
        return Environment.getExternalStorageDirectory();
    }


    @Override
    protected void onResume() {
        super.onResume();

        // Ideally, the frames from the camera are at the same resolution as the input to
        // the video encoder so we don't have to scale.
        openCamera(VIDEO_WIDTH, VIDEO_HEIGHT, DESIRED_PREVIEW_FPS);
    }

    @Override
    protected void onPause() {
        super.onPause();

        releaseCamera();

        if (mCameraTexture != null) {
            mCameraTexture.release();
            mCameraTexture = null;
        }
        if (mDisplaySurface != null) {
            mDisplaySurface.release();
            mDisplaySurface = null;
        }

        if (mFullFrameBlit != null) {
            mFullFrameBlit.release(false);
            mFullFrameBlit = null;
        }
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }
        Log.d(TAG, "onPause() done");
    }

    /**
     * Opens a camera, and attempts to establish preview mode at the specified width and height.
     * <p>
     * Sets mCameraPreviewFps to the expected frame rate (which might actually be variable).
     */
    private void openCamera(int desiredWidth, int desiredHeight, int desiredFps) {
        if (mCamera != null) {
            throw new RuntimeException("camera already initialized");
        }

        Camera.CameraInfo info = new Camera.CameraInfo();

        // Try to find a front-facing camera (e.g. for videoconferencing).
        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCamera = Camera.open(i);
                break;
            }
        }
        if (mCamera == null) {
            Log.d(TAG, "No front-facing camera found; opening default");
            mCamera = Camera.open();    // opens first back-facing camera
        }
        if (mCamera == null) {
            throw new RuntimeException("Unable to open camera");
        }

        Camera.Parameters parms = mCamera.getParameters();

        CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight);

        // Try to set the frame rate to a constant value.
        mCameraPreviewThousandFps = CameraUtils.chooseFixedPreviewFps(parms, desiredFps * 1000);

        // Give the camera a hint that we're recording video.  This can have a big
        // impact on frame rate.
        parms.setRecordingHint(true);

        mCamera.setParameters(parms);

        Camera.Size cameraPreviewSize = parms.getPreviewSize();
        String previewFacts = cameraPreviewSize.width + "x" + cameraPreviewSize.height +
                " @" + (mCameraPreviewThousandFps / 1000.0f) + "fps";
        Log.i(TAG, "Camera config: " + previewFacts);

        // Set the preview aspect ratio.
        AspectFrameLayout layout = (AspectFrameLayout) findViewById(R.id.continuousCapture_afl);
        layout.setAspectRatio((double) cameraPreviewSize.width / cameraPreviewSize.height);
    }

    /**
     * Stops camera preview, and releases the camera to the system.
     */
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            Log.d(TAG, "releaseCamera -- done");
        }
    }

    /**
     * Handles onClick for "capture" button.
     */
    public void clickCapture(@SuppressWarnings("unused") View unused) {
        Log.d(TAG, "capture");
    }


    @Override   // SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated holder=" + holder);

        // Set up everything that requires an EGL context.
        //
        // We had to wait until we had a surface because you can't make an EGL context current
        // without one, and creating a temporary 1x1 pbuffer is a waste of time.
        //
        // The display surface that we use for the SurfaceView, and the encoder surface we
        // use for video, use the same EGL context.
        mEglCore = new EglCore(null, EglCore.FLAG_TRY_GLES3);
        mDisplaySurface = new WindowSurface(mEglCore, holder.getSurface(), false);
        mDisplaySurface.makeCurrent();


        if (!use_camera_inputer) {
            mFullFrameBlit = new FullFrameRect(
                    new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        } else {
            mCameraInputFilter.init();

            mBeautyFilter.init();

            mBeautyFilter.onOutputSizeChanged(VIDEO_WIDTH, VIDEO_HEIGHT);
            mBeautyFilter.onInputSizeChanged(VIDEO_WIDTH, VIDEO_HEIGHT);
            if (mBeautyFilter instanceof MagicBaseGroupFilter) {
                ((MagicBaseGroupFilter) mBeautyFilter).setViewportParam(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);
            }
        }

        mTextureId = OpenGlUtils.getExternalOESTextureID();//mFullFrameBlit.createTextureObject();


        mCameraTexture = new SurfaceTexture(mTextureId);
        mCameraTexture.setOnFrameAvailableListener(this);

        Log.d(TAG, "starting camera preview");
        try {
            mCamera.setPreviewTexture(mCameraTexture);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        mCamera.startPreview();

        // TODO: adjust bit rate based on frame rate?
        // TODO: adjust video width/height based on what we're getting from the camera preview?
        //       (can we guarantee that camera preview size is compatible with AVC video encoder?)
    }

    @Override   // SurfaceHolder.Callback
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged fmt=" + format + " size=" + width + "x" + height +
                " holder=" + holder);
    }

    @Override   // SurfaceHolder.Callback
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed holder=" + holder);
    }

    @Override   // SurfaceTexture.OnFrameAvailableListener; runs on arbitrary thread
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        //Log.d(TAG, "frame available");
        mHandler.sendEmptyMessage(MainHandler.MSG_FRAME_AVAILABLE);
    }

    /**
     * Draws a frame onto the SurfaceView and the encoder surface.
     * <p>
     * This will be called whenever we get a new preview frame from the camera.  This runs
     * on the UI thread, which ordinarily isn't a great idea -- you really want heavy work
     * to be on a different thread -- but we're really just throwing a few things at the GPU.
     * The upside is that we don't have to worry about managing state changes between threads.
     * <p>
     * If there was a pending frame available notification when we shut down, we might get
     * here after onPause().
     */
    private void drawFrame() {
        Log.d(TAG, "drawFrame");
        if (mEglCore == null) {
            Log.d(TAG, "Skipping drawFrame after shutdown");
            return;
        }

        // Latch the next frame from the camera.
        mDisplaySurface.makeCurrent();
        mCameraTexture.updateTexImage();
        mCameraTexture.getTransformMatrix(mTmpMatrix);

        // Fill the SurfaceView with it.
        SurfaceView sv = (SurfaceView) findViewById(R.id.continuousCapture_surfaceView);
        int viewWidth = sv.getWidth();
        int viewHeight = sv.getHeight();
        GLES20.glViewport(0, 0, viewWidth, viewHeight);

        if (!use_camera_inputer) {
            mFullFrameBlit.drawFrame(mTextureId, mTmpMatrix);
        } else {
            if (mUseBeauty) {
                if (mBeautyFilter instanceof  MagicBaseGroupFilter) {
                    mBeautyFilter.onDraw(mTextureId, gLCubeBuffer, gLTextureBufferNormal);
                } else {
                    mBeautyFilter.onDraw(mTextureId);
                }
            } else {
                ((MagicCameraInputFilter) mCameraInputFilter).setTextureTransformMatrix(mTmpMatrix);
                mCameraInputFilter.onDraw(mTextureId);
            }
        }

        drawExtra(mFrameNum, viewWidth, viewHeight);
//        mDisplaySurface.setPresentationTime(mCameraTexture.getTimestamp());
        Timber.v("mDisplaySurface before swapBuffers");
        mDisplaySurface.swapBuffers();
        Timber.v("mDisplaySurface after swapBuffers");

        if (mReadPixel) {
            MyUtil.tryReadPixels(VIDEO_WIDTH, VIDEO_HEIGHT, mOutput2Image ? mIvDump : null);
//        MyUtil.tryReadPixels(480, 640, mOutput2Image ? mIvDump : null);
        }


        int frameNum = (mFrameNum++) % 10;

        if (frameNum == 0) {
            mFpsStartTs = System.nanoTime() / 1000000;
        } else if (frameNum == 9) {
            long intevalMs = ((System.nanoTime() / 1000000 - mFpsStartTs) / 10);

            mTvFps.setText(String.format("fps: %d", 1000 / intevalMs));
        }
    }

    /**
     * Adds a bit of extra stuff to the display just to give it flavor.
     */
    private static void drawExtra(int frameNum, int width, int height) {
        // We "draw" with the scissor rect and clear calls.  Note this uses window coordinates.
        int val = frameNum % 3;
        switch (val) {
            case 0:
                GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
                break;
            case 1:
                GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
                break;
            case 2:
                GLES20.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
                break;
        }

        int xpos = (int) (width * ((frameNum % 100) / 100.0f));
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(xpos, 0, width / 32, height / 32);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }
}
