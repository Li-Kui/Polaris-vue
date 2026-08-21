package com.polaris.ai.core.web;

import com.polaris.ai.core.annotation.CurrentCaller;
import com.polaris.ai.core.context.AnonymousCallerContext;
import com.polaris.ai.core.context.CallerContext;
import com.polaris.ai.core.context.CallerContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Spring MVC 控制器方法参数自动注入解析器。
 * 支持直接声明 CallerContext 参数类型，或标注 @CurrentCaller 注解自动注入。
 *
 * @author polaris
 */
public class CallerContextArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CallerContext.class.isAssignableFrom(parameter.getParameterType())
                || parameter.hasParameterAnnotation(CurrentCaller.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        CallerContext ctx = CallerContextHolder.get();
        if (ctx == null) {
            CurrentCaller ann = parameter.getParameterAnnotation(CurrentCaller.class);
            if (ann != null && ann.required()) {
                throw new IllegalStateException("当前请求未设置有效的 CallerContext 上下文");
            }
            return AnonymousCallerContext.INSTANCE;
        }
        return ctx;
    }
}
