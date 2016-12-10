package renderer;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.android.grafika.gles.EglCore;
import com.android.grafika.gles.WindowSurface;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import filter.MyBeautyFilter;
import filter.SimpleCameraInput;
import filter.base.GPUImageFilter;
import filter.utils.Rotation;
import filter.utils.TextureRotationUtil;
import timber.log.Timber;

/**
 * Created by shuailongcheng on 10/12/2016.
 */
public class BeautySurfaceView extends SurfaceView {

    private Object mReadyFence = new Object();      // guards ready/running
    private boolean mReady;
    private boolean mRunning;

    RenderHandler mHandler;

    private volatile boolean mSurfaceReady = false;

    private static final int MSG_START_RENDER = 0;
    private static final int MSG_STOP_RENDER = 1;
    private static final int MSG_FRAME_AVAILABLE = 2;
    private static final int MSG_SET_TEXTURE_ID = 3;
    private static final int MSG_QUIT = 4;


    private EglCore mEglCore;
    private WindowSurface mDisplaySurface;
    private SurfaceTexture mCameraTexture;  // receives the output from the camera preview
    private final float[] mSTTransMatrix = new float[16];
    private int mTextureId;
    private int mFrameNum = -1;

    private int mSurfaceWidth, mSurfaceHeight;

    //    private GPUImageFilter mBeautyFilter = new SimpleSmoothFilter();
    private GPUImageFilter mBeautyFilter = new MyBeautyFilter();
    //    private GPUImageFilter mBeautyFilter = new SimpleRGBMapFilter();
    private SimpleCameraInput mSimpleCameraInput = new SimpleCameraInput();


    public volatile FloatBuffer gLCubeBuffer;
    public volatile FloatBuffer gLTextureBufferMirror;
    public volatile FloatBuffer gLTextureBufferNormal;

    private int mCameraWidth = 640, mCameraHeight = 480;

    public BeautySurfaceView(Context context) {
        super(context);
        init();
    }

    public BeautySurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BeautySurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }


    public void setCameraSize(int cameraWidth, int cameraHeight) {
        mCameraWidth = cameraWidth;
        mCameraHeight = cameraHeight;
    }

    private void init() {
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
        gLTextureBufferNormal.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);

        adjustSize(270, true, false, gLTextureBufferNormal, gLCubeBuffer);
//        adjustSize(90, true, false, gLTextureBufferMirror, gLCubeBuffer);

        getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mSurfaceReady = true;

                mEglCore = new EglCore(null, EglCore.FLAG_TRY_GLES3);
                mDisplaySurface = new WindowSurface(mEglCore, holder.getSurface(), false);
                mDisplaySurface.makeCurrent();

                mSimpleCameraInput.init();
                mSimpleCameraInput.initCameraFrameBuffer(mCameraWidth, mCameraHeight);
                mBeautyFilter.init();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                mSurfaceWidth = width;
                mSurfaceHeight = height;
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mSurfaceReady = false;
            }
        });
    }

    private void adjustSize(int rotation, boolean flipHorizontal, boolean flipVertical, FloatBuffer texBuffer, FloatBuffer cubeBuffer) {
        float[] textureCords = TextureRotationUtil.getRotation(Rotation.fromInt(rotation),
                flipHorizontal, flipVertical);

        float[] cube = TextureRotationUtil.CUBE;
        cubeBuffer.clear();
        cubeBuffer.put(cube).position(0);
        texBuffer.clear();
        texBuffer.put(textureCords).position(0);
    }

    /**
     * 整个必须在activity 的onCreate里调用
     */
    private void startRender(SurfaceView surfaceView) {
    }

    public void stopRender() {
        mHandler.removeMessages(MSG_FRAME_AVAILABLE);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_RENDER));
        mHandler.sendMessage(mHandler.obtainMessage(MSG_QUIT));
    }

    public void frameAvailable() {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_FRAME_AVAILABLE));
    }

    private void startRenderThread() {
        synchronized (mReadyFence) {
            if (mRunning) {
                Timber.w("Encoder thread already running");
                return;
            }
            mRunning = true;

            new Thread("BeautyOutputer") {
                @Override
                public void run() {
                    BeautySurfaceView.this.run();
                }
            }.start();

            while (!mReady) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_START_RENDER));
    }

    private void setTextureId(int id) {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXTURE_ID, id, 0, null));
    }


    public void run() {
        Looper.prepare();

        synchronized (mReadyFence) {
            mHandler = new RenderHandler(this);
            mReady = true;
            mReadyFence.notify();
        }

        Looper.loop();

        Timber.d("SurfaceRenderThread exiting");
        synchronized (mReadyFence) {
            mReady = mRunning = false;
            mHandler = null;
        }
    }

    private static class RenderHandler extends Handler {
        private WeakReference<BeautySurfaceView> mWeakRenderer;

        public RenderHandler(BeautySurfaceView renderer) {
            mWeakRenderer = new WeakReference<BeautySurfaceView>(renderer);
        }

        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
            Object obj = msg.obj;

            BeautySurfaceView outputer = mWeakRenderer.get();
            if (outputer == null) {
                Timber.d("mWeakRenderer.get() outputer = null  ");
                return;
            }


            switch (msg.what) {
                case MSG_START_RENDER:
                    break;
                case MSG_STOP_RENDER:
                    outputer.handleStopRender();
                    break;
                case MSG_FRAME_AVAILABLE:
                    outputer.handleDrawFrame();
                    break;
                case MSG_SET_TEXTURE_ID:
                    outputer.handleSetTexture(msg.arg1);
                    break;
                case MSG_QUIT:
                    Looper.myLooper().quit();
                    break;
                default:
                    throw new RuntimeException("Unknown message " + msg.what);
            }
        }
    }

    private void handleSetTexture(int arg1) {
        mTextureId = arg1;
    }

    private void handleStopRender() {
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }

        if (mSimpleCameraInput != null) {
            mSimpleCameraInput.destroyFramebuffers();
            mSimpleCameraInput.destroy();
        }
        if (mBeautyFilter != null) {
            mBeautyFilter.destroy();
        }
    }


    private boolean first = true;
    private boolean mPreReadPixel = false;
    private boolean mOutput2Image, mUseBeauty, mReadPixel;

    private void handleDrawFrame() {
        Timber.d("drawFrame");
        if (mEglCore == null) {
            Timber.d("Skipping drawFrame after shutdown");
            return;
        }


        // Latch the next frame from the camera.
        mDisplaySurface.makeCurrent();


        if (first) {
            first = false;
        } else {
            if (mReadPixel && mPreReadPixel) {
//            MyUtil.tryReadPixels(VIDEO_WIDTH, 640, mOutput2Image ? mIvDump : null);
//                MyUtil.tryReadPixels(480, 640, mOutput2Image ? mIvDump : null);
            }
        }

        mCameraTexture.updateTexImage();
        mCameraTexture.getTransformMatrix(mSTTransMatrix);

        int viewWidth = mSurfaceWidth;
        int viewHeight = mSurfaceHeight;
        GLES20.glViewport(0, 0, viewWidth, viewHeight);

        if (mUseBeauty) {
            int id;
            mSimpleCameraInput.setTextureTransformMatrix(mSTTransMatrix);
            id = mSimpleCameraInput.onDrawToTexture(mTextureId);

            mBeautyFilter.onDraw(id, gLCubeBuffer, gLTextureBufferNormal);
        } else {
            mSimpleCameraInput.setTextureTransformMatrix(mSTTransMatrix);
            mSimpleCameraInput.onDraw(mTextureId, gLCubeBuffer, gLTextureBufferNormal);
        }

        drawExtra(mFrameNum, viewWidth, viewHeight);
//        mDisplaySurface.setPresentationTime(mCameraTexture.getTimestamp());
        Timber.v("mDisplaySurface before swapBuffers");
        mDisplaySurface.swapBuffers();
        Timber.v("mDisplaySurface after swapBuffers");

//        if (mReadPixel && !mPreReadPixel) {
////            MyUtil.tryReadPixels(VIDEO_WIDTH, 640, mOutput2Image ? mIvDump : null);
//            MyUtil.tryReadPixels(480, 640, mOutput2Image ? mIvDump : null);
//        }

        int frameNum = (mFrameNum++) % 10;

//        if (frameNum == 0) {
//            mFpsStartTs = System.nanoTime() / 1000000;
//        } else if (frameNum == 9) {
//            long intevalMs = ((System.nanoTime() / 1000000 - mFpsStartTs) / 10);
//
//            mTvFps.setText(String.format("fps: %d", 1000 / intevalMs));
//        }
    }

    /**
     * Adds a bit of extra stuff to the display just to give it flavor.
     */
    private static void drawExtra(int frameNum, int width, int height) {
        // We "draw" with the scissor rect and clear calls.  Note this uses window coordinates.
        int val = frameNum % 3;
        switch (val) {
            case 0:
                GLES20.glClearColor(1.0f, 0.0f, 0.0f, 0.0f);
                break;
            case 1:
                GLES20.glClearColor(0.0f, 1.0f, 0.0f, 0.0f);
                break;
            case 2:
                GLES20.glClearColor(0.0f, 0.0f, 1.0f, 0.0f);
                break;
        }

        int xpos = (int) (width * ((frameNum % 100) / 100.0f));
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(xpos, 0, width / 32, height / 32);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }
}
