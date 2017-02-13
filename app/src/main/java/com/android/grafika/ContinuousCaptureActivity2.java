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
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;

import filter.utils.OpenGlUtils;
import beauty_surfaceview.BeautySurfaceView;
import timber.log.Timber;

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
public class ContinuousCaptureActivity2 extends Activity implements
        SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = MainActivity.TAG;

    //    private static final int VIDEO_WIDTH = 1280;  // dimensions for 720p video
//    private static final int VIDEO_HEIGHT = 720;
    private static final int VIDEO_WIDTH = 640;  // dimensions for 720p video
    private static final int VIDEO_HEIGHT = 480;
    private static final int DESIRED_PREVIEW_FPS = 15;

    private SurfaceTexture mCameraTexture;  // receives the output from the camera preview
    private final float[] mSTTransMatrix = new float[16];

    private Camera mCamera;
    private int mCameraPreviewThousandFps;


    private ImageView mIvDump;
    private CheckBox mCbOutput2Image, mCbUseBeauty, mCbReadPixel, mCbPreReadPixel;

    private TextView mTvFps;

    private long mFpsStartTs;

    private int mCameraTextureId;

    /**
     * Handles onClick for "capture" button.
     */
    public void clickCapture(@SuppressWarnings("unused") View unused) {
        SurfaceView sv = (SurfaceView) findViewById(R.id.continuousCapture_surfaceView);
        SurfaceHolder sh = sv.getHolder();
        Timber.d("capture, sh.isCreating():%b", sh.isCreating());
        sh.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Timber.d("surfaceCreated");
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

                Timber.d("surfaceChanged");
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

                Timber.d("surfaceDestroyed");
            }
        });
    }

    BeautySurfaceView mBeautySurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_continuous_capture2);

        mBeautySurfaceView = (BeautySurfaceView) findViewById(R.id.continuousCapture_surfaceView);
        SurfaceHolder sh = mBeautySurfaceView.getHolder();

        Timber.d("onCreate, sh.isCreating():%b", sh.isCreating());
        sh.setFixedSize(VIDEO_HEIGHT, VIDEO_WIDTH);
//        sh.setFormat(PixelFormat.RGBA_8888);

        mIvDump = (ImageView) findViewById(R.id.mIvDump);
        mCbOutput2Image = (CheckBox) findViewById(R.id.mCbOutput2Image);
        mCbOutput2Image.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mBeautySurfaceView.setmOutput2Image(isChecked);
                mIvDump.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });

        mCbPreReadPixel = (CheckBox) findViewById(R.id.mCbPreReadPixel);
        mCbPreReadPixel.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mBeautySurfaceView.setmPreReadPixel(isChecked);
            }
        });


        mCbUseBeauty = (CheckBox) findViewById(R.id.mCbUseBeauty);
        mCbUseBeauty.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mBeautySurfaceView.setmUseBeauty(isChecked);
            }
        });

        mCbReadPixel = (CheckBox) findViewById(R.id.mCbReadPixel);
        mCbReadPixel.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mBeautySurfaceView.setmReadPixel(isChecked);
            }
        });

        mTvFps = (TextView) findViewById(R.id.mTvFps);
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
            mBeautySurfaceView.setCameraTexture(null);
            mCameraTexture.release();
            mCameraTexture = null;
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
//        layout.setAspectRatio((double) cameraPreviewSize.width / cameraPreviewSize.height);
        layout.setAspectRatio((double) cameraPreviewSize.height / cameraPreviewSize.width);

        doStartCamera();
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

    @Override   // SurfaceTexture.OnFrameAvailableListener; runs on arbitrary thread
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        mBeautySurfaceView.setTextureId(mCameraTextureId);
        mBeautySurfaceView.frameAvailable(surfaceTexture);
    }


    public void doStartCamera() {
        mCameraTextureId = OpenGlUtils.getExternalOESTextureID();
        mCameraTexture = new SurfaceTexture(mCameraTextureId);
        mCameraTexture.setOnFrameAvailableListener(this);

        mBeautySurfaceView.setCameraTexture(mCameraTexture);
        mBeautySurfaceView.setTestCallback(new BeautySurfaceView.TestCallback() {
            @Override
            public TextView getFpsTextView() {
                return mTvFps;
            }

            @Override
            public ImageView getDumpImageView() {
                return mIvDump;
            }
        });

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
}
