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

        long start = System.nanoTime() / 1000000;
        if (pixelBuf == null || pixelBuf.limit() < w * h * 4) {
            pixelBuf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.LITTLE_ENDIAN);
        }

        long tag1 = System.nanoTime() / 1000000;

        // Try to ensure that rendering has finished.
//        GLES20.glFinish();

//        pixelBuf.position(0);
//        GLES20.glReadPixels(0, 0, 1, 1,
//                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuf);

        // Time individual extraction.  Ideally we'd be timing a bunch of these calls
        // and measuring the aggregate time, but we want the isolated time, and if we
        // just read the same buffer repeatedly we might get some sort of cache effect.

        pixelBuf.position(0);
        GLES20.glReadPixels(0, 0, w, h,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuf);
        long tag2 = System.nanoTime() / 1000000;

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
        long tag3 = System.nanoTime() / 1000000;

        Log.d("MyUtil", String.format(", w:%d, h:%d,  tag1 consume:%d ms, glReadPixels consume:%d, tag2~tag3:%d, total:%d",
                w, h, tag1 - start, tag2 - tag1, tag3 - tag2, tag3 - start));
    }
}
