package com.polaris.common.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 接口迭代版本号常量
 *
 * @author polaris
 */
public final class ApiVersionConstants
{
    /** 1.0.0 版本 */
    public static final String VERSION_1_0_0 = "1.0.0";
    /** 2.0.0 版本 */
    public static final String VERSION_2_0_0 = "2.0.0";
    /** 当前默认版本号 */
    public static final String CURRENT = VERSION_1_0_0;
    /** 全部迭代版本（新增版本时在此追加） */
    private static final List<String> ALL_VERSIONS = Arrays.asList(
        VERSION_1_0_0,
        VERSION_2_0_0
    );

    private ApiVersionConstants()
    {
    }

    /**
     * 获取全部迭代版本号列表
     */
    public static List<String> allVersions()
    {
        return Collections.unmodifiableList(ALL_VERSIONS);
    }
}
