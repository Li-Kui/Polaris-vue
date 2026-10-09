package com.polaris.framework.web.exception;

import com.alibaba.fastjson2.JSON;
import com.polaris.common.core.domain.AjaxResult;
import com.polaris.common.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** 执行归属拒绝统一为 HTTP/业务 404；不改变其他接口的历史业务错误映射。 */
@Component
public class WorkflowExecutionExceptionResolver extends AbstractHandlerExceptionResolver {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutionExceptionResolver.class);
    private static final Pattern EXECUTION_PATH = Pattern.compile(
            "^/(?:ai/workflow/executions/[^/]+|platform/api/workflow-executions/[^/]+"
                    + "|platform/runtime/shares/[^/]+/executions/[^/]+)(?:/.*)?$");

    public WorkflowExecutionExceptionResolver() {
        // 现有公开/API Key SSE 解析器优先；此处同时补齐管理端 SSE 和普通 JSON 请求。
        setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    }

    @Override
    protected ModelAndView doResolveException(HttpServletRequest request,
            HttpServletResponse response, Object handler, Exception exception) {
        if (!(handler instanceof HandlerMethod) || response.isCommitted()
                || !(exception instanceof ServiceException service)
                || !Integer.valueOf(404).equals(service.getCode())) {
            return null;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!EXECUTION_PATH.matcher(path).matches()) return null;
        try {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            // 不回显执行 ID、租户、主体及存在性等信息。
            response.getWriter().write(JSON.toJSONString(
                    AjaxResult.error(404, "工作流执行不存在或无权访问")));
            return new ModelAndView();
        } catch (IOException e) {
            log.warn("工作流执行不可访问响应写入失败", e);
            return null;
        }
    }
}
