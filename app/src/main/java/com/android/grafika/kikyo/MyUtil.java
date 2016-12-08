package com.android.grafika.kikyo;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.util.Log;
import android.widget.ImageView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by shuailongcheng on 28/11/2016.
 */

public class MyUtil {
    static ByteBuffer pixelBuf;

    public static void tryReadPixels(int w, int h, final ImageView imageView) {

        if (pixelBuf == null || pixelBuf.limit() < w * h * 4) {
            pixelBuf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.LITTLE_ENDIAN);
        }

        long startWhen = System.nanoTime();

        // Try to ensure that rendering has finished.
        GLES20.glFinish();
//        pixelBuf.position(0);
//        GLES20.glReadPixels(0, 0, 1, 1,
//                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuf);

        // Time individual extraction.  Ideally we'd be timing a bunch of these calls
        // and measuring the aggregate time, but we want the isolated time, and if we
        // just read the same buffer repeatedly we might get some sort of cache effect.

        pixelBuf.position(0);
        GLES20.glReadPixels(0, 0, w, h,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuf);
        long consume = System.nanoTime() - startWhen;
        Log.d("MyUtil", String.format("ReadPixel, w:%d, h:%d, consume:%d ms", w, h, consume / 1000000));

        if (imageView != null) {

            final Bitmap tmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            tmp.copyPixelsFromBuffer(pixelBuf);
            imageView.post(new Runnable() {
                @Override
                public void run() {
                    imageView.setImageBitmap(tmp);
                }
            });
        }
    }
}
