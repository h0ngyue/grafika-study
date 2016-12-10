package filter;

import java.util.Arrays;
import java.util.List;

import filter.base.GPUImageFilterGroup;

/**
 * Created by shuailongcheng on 06/12/2016.
 */

public class MyBeautyFilter extends GPUImageFilterGroup {
    public MyBeautyFilter() {
        super(Arrays.asList(new SimpleSmoothFilter(), new SimpleRGBMapFilter()));
    }
}
