package com.polaris.platform.auth;

import com.alibaba.fastjson2.JSON;
import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.common.core.domain.AjaxResult;
import com.polaris.platform.domain.PlatformApiKey;
import com.polaris.platform.mapper.PlatformApiKeyMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;

/**
 * API Key 认证过滤器。
 * 拦截 /platform/api/** 请求，从 X-API-Key 头中校验 Key。
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Autowired
    private PlatformApiKeyMapper apiKeyMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String keyValue = request.getHeader(API_KEY_HEADER);
        if (keyValue == null || keyValue.isBlank()) {
            sendError(response, 401, "缺少 X-API-Key 请求头");
            return;
        }

        PlatformApiKey apiKey = apiKeyMapper.selectByApiKey(keyValue);
        if (apiKey == null) {
            sendError(response, 401, "无效的 API Key");
            return;
        }

        if ("1".equals(apiKey.getStatus())) {
            sendError(response, 403, "API Key 已被停用");
            return;
        }

        if (apiKey.getExpireTime() != null && apiKey.getExpireTime().before(new Date())) {
            sendError(response, 403, "API Key 已过期");
            return;
        }

        // 更新最后使用时间（异步更佳，此处简化）
        apiKeyMapper.updateLastUsedTime(apiKey.getId());

        CallerContextHolder.set(
            new ApiKeyCallerContext(apiKey.getTenantId(), apiKey.getKeyName(), apiKey.getPermissions())
        );

        try {
            filterChain.doFilter(request, response);
        } finally {
            CallerContextHolder.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/platform/api/");
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.toJSONString(AjaxResult.error(status, message)));
    }
}
