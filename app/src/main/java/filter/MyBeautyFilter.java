package filter;

import java.util.Arrays;

/**
 * Created by shuailongcheng on 06/12/2016.
 */

public class MyBeautyFilter extends MagicBaseGroupFilter {
    public MyBeautyFilter() {
        super(Arrays.asList(new MySmoothFilter(), new MyRGBMapFilter()));
    }
}
