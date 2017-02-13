package beauty_surfaceview;


import com.android.grafika.R;

import filter.base.GPUImageFilter;
import filter.utils.OpenGlUtils;


/**
 * Created by shuailongcheng on 06/12/2016.
 */

public class SimpleSmoothFilter extends GPUImageFilter {
    public SimpleSmoothFilter() {
        super(NO_FILTER_VERTEX_SHADER, OpenGlUtils.readShaderFromRawResource(R.raw.lg_smooth_frag));
    }
}
