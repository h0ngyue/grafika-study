package filter;


import com.android.grafika.R;

import filter.utils.OpenGlUtils;


/**
 * Created by shuailongcheng on 06/12/2016.
 */

public class MySmoothFilter extends MyGPUImageFilter {
    public MySmoothFilter() {
        super(NO_FILTER_VERTEX_SHADER, OpenGlUtils.readShaderFromRawResource(R.raw.lg_smooth_frag));
    }
}
