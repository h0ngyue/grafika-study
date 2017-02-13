package beauty_surfaceview;

import java.util.Arrays;

import filter.base.GPUImageFilterGroup;

/**
 * Created by shuailongcheng on 06/12/2016.
 */

public class SimpleBeautyFilter extends GPUImageFilterGroup {
    public SimpleBeautyFilter() {
        super(Arrays.asList(new SimpleSmoothFilter(), new SimpleRGBMapFilter()));
    }
}
