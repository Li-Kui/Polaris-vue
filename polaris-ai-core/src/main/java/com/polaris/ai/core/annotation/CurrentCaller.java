package com.polaris.ai.core.annotation;

import java.lang.annotation.*;

/**
 * Controller 参数注解：自动注入当前请求的 CallerContext。
 *
 * <pre>
 * &#64;GetMapping("/my-api")
 * public ResultData handle(&#64;CurrentCaller CallerContext caller) {
 *     ...
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentCaller {

    /**
     * 是否必须存在上下文，默认 false（不存在时注入 AnonymousCallerContext）
     */
    boolean required() default false;
}
