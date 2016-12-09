package filter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;

import com.android.grafika.R;

import filter.utils.OpenGlUtils;


/**
 * Created by shuailongcheng on 06/12/2016.
 */

public class MyRGBMapFilter extends MyGPUImageTwoInputFilter {
    int mLevel;

    public MyRGBMapFilter() {
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
