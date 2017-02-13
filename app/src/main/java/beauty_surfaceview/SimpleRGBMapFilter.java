package beauty_surfaceview;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;

import com.android.grafika.R;

import filter.BeautyCamera;
import filter.base.gpuimage.GPUImageTwoInputFilter;
import filter.utils.OpenGlUtils;


/**
 * Created by shuailongcheng on 06/12/2016.
 */

public class SimpleRGBMapFilter extends GPUImageTwoInputFilter {
    int mLevel;

    public SimpleRGBMapFilter() {
        super(OpenGlUtils.readShaderFromRawResource(R.raw.lg_rgb_map_frag));

        Bitmap bitmap = BitmapFactory.decodeResource(BeautyCamera.context.getResources(), R.drawable.map);
        setBitmap(bitmap);
    }

    @Override
    public void onInit() {
        super.onInit();
        mLevel = GLES20.glGetUniformLocation(getProgram(), "mLevel");
    }
}
