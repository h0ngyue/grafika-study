package filter;

import java.util.Arrays;
import java.util.List;

/**
 * Created by shuailongcheng on 06/12/2016.
 */

public class MyBeautyFilter extends MagicBaseGroupFilter {
    public MyBeautyFilter(List<MyGPUImageFilter> filters) {
        super(filters);
    }
//    public MyBeautyFilter() {
//        super(Arrays.asList(new MySmoothFilter(), new MyRGBMapFilter()));
//    }
}
