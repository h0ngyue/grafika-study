package renderer;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.grafika.gles.EglCore;
import com.android.grafika.gles.WindowSurface;
import com.android.grafika.kikyo.MyUtil;

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

    private volatile TestCallback mTestCallback;

    public interface TestCallback {
        TextView getFpsTextView();

        ImageView getDumpImageView();
    }

    public void setTestCallback(TestCallback cb) {
        mTestCallback = cb;
    }

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
    private volatile SurfaceTexture mCameraTexture;  // receives the output from the camera preview
    private int mTextureId;
    private int mFrameNum = -1;

    private int mSurfaceWidth, mSurfaceHeight;

    private SimpleCameraInput mSimpleCameraInput = new SimpleCameraInput();

    //    private GPUImageFilter mBeautyFilter = new SimpleSmoothFilter();
    private GPUImageFilter mBeautyFilter = new MyBeautyFilter();
    //    private GPUImageFilter mBeautyFilter = new SimpleRGBMapFilter();


    public volatile FloatBuffer gLCubeBuffer;
    public volatile FloatBuffer gLTextureBufferMirror;
    public volatile FloatBuffer gLTextureBufferNormal;

    private boolean mIsCamera1;
    private boolean mIsFront;
    private int mDefaultCameraWidth = 640;
    private int mDefaultCameraHeight = 480;

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


    public void setCameraInfo(boolean isCamera1, boolean isFront, int cameraWidth, int cameraHeight) {
        mIsCamera1 = isCamera1;
        mIsFront = isFront;
        mDefaultCameraWidth = cameraWidth;
        mDefaultCameraHeight = cameraHeight;

        adjustToCameraInfo();
    }

    private void init() {
        startRenderThread();

        Timber.d("lifecycle init");
        initAdjustBuffer();
        adjustToCameraInfo();

        getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Timber.d("lifecycle surfaceCreated");
                mSurfaceReady = true;

                startRender(new RenderConfig(holder.getSurface(), mDefaultCameraWidth, mDefaultCameraHeight));

                holder.setFixedSize(mDefaultCameraHeight, mDefaultCameraWidth);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Timber.d("lifecycle surfaceChanged, width:%d, height:%d", width, height);
                mSurfaceWidth = width;
                mSurfaceHeight = height;
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Timber.d("lifecycle surfaceDestroyed");
                mSurfaceReady = false;
            }
        });
    }

    private void initAdjustBuffer() {
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
    }

    private void adjustToCameraInfo() {
        // TODO: 10/12/2016 adjust according to camera1/camera2 and front/back
        adjustSize(270, true, false, gLTextureBufferNormal, gLCubeBuffer);
//        adjustSize(90, true, false, gLTextureBufferMirror, gLCubeBuffer);
    }


    private static void adjustSize(int rotation, boolean flipHorizontal, boolean flipVertical, FloatBuffer texBuffer, FloatBuffer cubeBuffer) {
        float[] textureCords = TextureRotationUtil.getRotation(Rotation.fromInt(rotation),
                flipHorizontal, flipVertical);
        float[] cube = TextureRotationUtil.CUBE;
        cubeBuffer.clear();
        cubeBuffer.put(cube).position(0);
        texBuffer.clear();
        texBuffer.put(textureCords).position(0);
    }


    private static class RenderConfig {
        public final Surface mSurface;
        public final int mCameraWidth;
        public final int mCameraHeight;

        private RenderConfig(Surface mSurface, int mCameraWidth, int mCameraHeight) {
            this.mSurface = mSurface;
            this.mCameraWidth = mCameraWidth;
            this.mCameraHeight = mCameraHeight;
        }
    }

    private void startRender(RenderConfig config) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_START_RENDER, config));
    }

    public void stopRender() {
        mHandler.removeMessages(MSG_FRAME_AVAILABLE);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_RENDER));
        mHandler.sendMessage(mHandler.obtainMessage(MSG_QUIT));
    }

    public void frameAvailable(SurfaceTexture st) {
        synchronized (mReadyFence) {
            if (!mReady) {
                Timber.e("frameAvailable but not readey");
                return;
            }
        }

        float[] transform = new float[16];      // TODO - avoid alloc every frame
        st.getTransformMatrix(transform);

        long timestamp = st.getTimestamp();
//        if (timestamp == 0) {
//            // Seeing this after device is toggled off/on with power button.  The
//            // first frame back has a zero timestamp.
//            //
//            // MPEG4Writer thinks this is cause to abort() in native code, so it's very
//            // important that we just ignore the frame.
//            Timber.e("frameAvailable HEY: got SurfaceTexture with timestamp of zero");
//            return;
//        }

        Message message = mHandler.obtainMessage(MSG_FRAME_AVAILABLE, (int) (timestamp >> 32), (int) timestamp, transform);
        mHandler.sendMessage(message);
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
                    Looper.prepare();

                    synchronized (mReadyFence) {
                        mHandler = new RenderHandler(BeautySurfaceView.this);
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
            }.start();

            while (!mReady) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
        }
    }

    public void setTextureId(int id) {
        synchronized (mReadyFence) {
            if (!mReady) {
                Timber.e("setTextureId but not readey");
                return;
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXTURE_ID, id, 0, null));
    }


    public void setCameraTexture(SurfaceTexture cameraTexture) {
        Timber.d("setCameraTexture:" + cameraTexture);
        this.mCameraTexture = cameraTexture;
    }

    private static class RenderHandler extends Handler {
        private WeakReference<BeautySurfaceView> mWeakRenderer;

        public RenderHandler(BeautySurfaceView renderer) {
            mWeakRenderer = new WeakReference<BeautySurfaceView>(renderer);
        }

        @Override
        public void handleMessage(Message msg) {
            Object obj = msg.obj;

            BeautySurfaceView outputer = mWeakRenderer.get();
            if (outputer == null) {
                Timber.d("mWeakRenderer.get() outputer = null  ");
                return;
            }

            switch (msg.what) {
                case MSG_START_RENDER:
                    Timber.e("handleMessage MSG_START_RENDER");
                    outputer.handleStartRender((RenderConfig) obj);
                    break;
                case MSG_STOP_RENDER:
                    Timber.e("handleMessage MSG_STOP_RENDER");
                    outputer.handleStopRender();
                    break;
                case MSG_FRAME_AVAILABLE:
                    Timber.d("handleMessage MSG_FRAME_AVAILABLE");
                    long timestamp = (((long) msg.arg1) << 32) |
                            (((long) msg.arg2) & 0xffffffffL);
                    outputer.handleDrawFrame((float[]) obj, timestamp);
                    break;
                case MSG_SET_TEXTURE_ID:
                    Timber.d("handleMessage MSG_SET_TEXTURE_ID");
                    outputer.handleSetTexture(msg.arg1);
                    break;
                case MSG_QUIT:
                    Timber.d("handleMessage MSG_QUIT");
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


    /**
     * 整个必须在activity 的onCreate里调用
     */
    private void handleStartRender(RenderConfig config) {
        Timber.d("handleStartRender,  mDefaultCameraWidth:%d, mDefaultCameraHeight:%d", mDefaultCameraWidth, mDefaultCameraHeight);
        mEglCore = new EglCore(null, EglCore.FLAG_TRY_GLES3);
        mDisplaySurface = new WindowSurface(mEglCore, config.mSurface, false);
        mDisplaySurface.makeCurrent();

        mSimpleCameraInput.init();
        mSimpleCameraInput.initCameraFrameBuffer(config.mCameraWidth, config.mCameraHeight);
        mBeautyFilter.init();


//        mSimpleCameraInput.onOutputSizeChanged(mSurfaceWidth, mSurfaceHeight);
//        mBeautyFilter.onOutputSizeChanged(mSurfaceWidth, mSurfaceHeight);

        mSimpleCameraInput.onOutputSizeChanged(mDefaultCameraHeight, mDefaultCameraWidth);
        mBeautyFilter.onOutputSizeChanged(mDefaultCameraHeight, mDefaultCameraWidth);
    }

    private boolean first = true;
    private volatile boolean mOutput2Image, mUseBeauty, mReadPixel, mPreReadPixel;
    private long mFpsStartTs;

    public void setmOutput2Image(boolean mOutput2Image) {
        this.mOutput2Image = mOutput2Image;
    }

    public void setmUseBeauty(boolean mUseBeauty) {
        this.mUseBeauty = mUseBeauty;
    }

    public void setmReadPixel(boolean mReadPixel) {
        this.mReadPixel = mReadPixel;
    }

    public void setmPreReadPixel(boolean mPreReadPixel) {
        this.mPreReadPixel = mPreReadPixel;
    }

    private void handleDrawFrame(float[] trans, long timestamp) {
        if (mEglCore == null) {
            Timber.d("Skipping drawFrame after shutdown");
            return;
        }

        // Latch the next frame from the camera.
        mDisplaySurface.makeCurrent();

        SurfaceTexture cameraST = mCameraTexture;

        Timber.d("handleDrawFrame:" + cameraST);
        if (cameraST == null) {
            return;
        }
        cameraST.updateTexImage();


        if (first) {
            first = false;
        } else {
            if (mReadPixel && mPreReadPixel) {
//            MyUtil.tryReadPixels(VIDEO_WIDTH, 640, mOutput2Image ? mIvDump : null);
                MyUtil.tryReadPixels(480, 640, mOutput2Image && mTestCallback != null ? mTestCallback.getDumpImageView() : null);
            }
        }

        mCameraTexture.updateTexImage();
        mCameraTexture.getTransformMatrix(trans);

        int viewWidth = mSurfaceWidth;
        int viewHeight = mSurfaceHeight;
        GLES20.glViewport(0, 0, viewWidth, viewHeight);

        if (mUseBeauty) {
            int id;
            mSimpleCameraInput.setTextureTransformMatrix(trans);
            id = mSimpleCameraInput.onDrawToTexture(mTextureId);

            mBeautyFilter.onDraw(id, gLCubeBuffer, gLTextureBufferNormal);
        } else {
            mSimpleCameraInput.setTextureTransformMatrix(trans);
            mSimpleCameraInput.onDraw(mTextureId, gLCubeBuffer, gLTextureBufferNormal);
        }

        drawExtra(mFrameNum, viewWidth, viewHeight);
//        mDisplaySurface.setPresentationTime(timestamp);
        Timber.v("mDisplaySurface before swapBuffers");
        mDisplaySurface.swapBuffers();
        Timber.v("mDisplaySurface after swapBuffers");

        if (mReadPixel && !mPreReadPixel) {
//            MyUtil.tryReadPixels(VIDEO_WIDTH, 640, mOutput2Image ? mIvDump : null);
            MyUtil.tryReadPixels(480, 640, mOutput2Image && (mTestCallback != null) ? mTestCallback.getDumpImageView() : null);
        }

        int frameNum = (mFrameNum++) % 10;

        if (frameNum == 0) {
            mFpsStartTs = System.nanoTime() / 1000000;
        } else if (frameNum == 9) {
            final long intevalMs = ((System.nanoTime() / 1000000 - mFpsStartTs) / 10);

            if (mTestCallback != null) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        mTestCallback.getFpsTextView().setText(String.format("fps: %d", 1000 / intevalMs));
                    }
                });
            }
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
