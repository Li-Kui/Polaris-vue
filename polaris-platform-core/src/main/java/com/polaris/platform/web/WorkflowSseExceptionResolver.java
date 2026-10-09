package com.polaris.platform.web;

import com.alibaba.fastjson2.JSON;
import com.polaris.common.core.domain.AjaxResult;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.controller.WorkflowShareRuntimeController;
import com.polaris.platform.controller.openApi.PlatformWorkflowOpenApiController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** 工作流 SSE 建立订阅前明确返回 JSON 错误，避免按 text/event-stream 序列化业务异常。 */
@Component
public class WorkflowSseExceptionResolver extends AbstractHandlerExceptionResolver {

    private static final Logger log = LoggerFactory.getLogger(WorkflowSseExceptionResolver.class);

    public WorkflowSseExceptionResolver() {
        setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    @Override
    protected ModelAndView doResolveException(
            HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception exception) {
        if (!isWorkflowStream(handler) || response.isCommitted()) {
            return null;
        }
        int code;
        String message;
        if (exception instanceof AccessDeniedException) {
            code = 403;
            message = "没有权限，请联系管理员授权";
        } else if (exception instanceof ServiceException service) {
            code = service.getCode() == null ? 500 : service.getCode();
            message = service.getMessage();
        } else if (exception instanceof MethodArgumentTypeMismatchException
                || exception instanceof IllegalArgumentException) {
            code = 400;
            message = "Last-Event-ID格式无效".equals(exception.getMessage())
                    ? "Last-Event-ID格式无效" : "请求参数无效";
        } else {
            code = 500;
            message = "系统执行异常，请稍后重试";
        }
        log.error("请求地址'{}',工作流 SSE 订阅失败", request.getRequestURI(), exception);
        try {
            response.setStatus(code >= 400 && code <= 599 ? code : 500);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.getWriter().write(JSON.toJSONString(AjaxResult.error(code, message)));
            return new ModelAndView();
        } catch (IOException e) {
            log.warn("工作流 SSE 错误响应写入失败", e);
            return null;
        }
    }

    private boolean isWorkflowStream(Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return false;
        }
        Class<?> type = method.getBeanType();
        return (PlatformWorkflowOpenApiController.class.isAssignableFrom(type)
                || WorkflowShareRuntimeController.class.isAssignableFrom(type))
                && SseEmitter.class.isAssignableFrom(method.getMethod().getReturnType());
    }
}
