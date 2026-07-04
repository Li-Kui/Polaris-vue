package com.polaris.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import com.polaris.common.constant.ApiVersionConstants;

/**
 * 接口迭代版本分组注解（支持类和方法，方法优先）
 *
 * @author polaris
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiGroup
{
    /**
     * 迭代版本号，如 1.0.0
     */
    String value() default ApiVersionConstants.CURRENT;
}
