package com.polaris.ai.tools.base;

import java.lang.annotation.*;

/** 声明 AI 工具方法所需的系统权限。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiToolPermission {
    /** 空字符串表示仅要求已认证，不需要额外业务权限。 */
    String value() default "";

    /** 写操作必须显式声明，工作流等受控场景据此实施阻断或审批。 */
    ToolSideEffect sideEffect() default ToolSideEffect.UNKNOWN;

    /** 向外部服务传输输入数据的工具必须显式声明。 */
    ToolDataBoundary dataBoundary() default ToolDataBoundary.INTERNAL;
}
