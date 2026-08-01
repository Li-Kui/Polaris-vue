package com.polaris.ai.tools.base;

import java.lang.annotation.*;

/** 声明 AI 工具方法所需的系统权限。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiToolPermission {
    /** 空字符串表示仅要求已认证，不需要额外业务权限。 */
    String value() default "";
}
