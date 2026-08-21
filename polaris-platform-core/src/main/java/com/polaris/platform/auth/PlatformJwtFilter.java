package com.polaris.platform.auth;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.platform.domain.PlatformUser;
import com.polaris.platform.mapper.PlatformUserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * 中台控制台 JWT 认证过滤器。
 * 拦截中台控制台请求及携带 Platform-Token 的 AI 模块请求，解析租户用户信息。
 */
@Component
public class PlatformJwtFilter extends OncePerRequestFilter {

    private static final String TOKEN_HEADER = "Platform-Token";

    @Autowired
    private PlatformJwtUtils jwtUtils;

    @Autowired
    private PlatformUserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader(TOKEN_HEADER);
        boolean authSet = false;
        if (token != null && jwtUtils.isTokenValid(token)) {
            try {
                Long userId = jwtUtils.getUserId(token);
                Long tenantId = jwtUtils.getTenantId(token);
                String username = jwtUtils.getUsername(token);

                PlatformUser user = userMapper.selectById(userId);
                boolean isAdmin = user != null && "admin".equals(user.getRole());

                PlatformUserCallerContext context = new PlatformUserCallerContext(userId, tenantId, username, isAdmin);
                CallerContextHolder.set(context);

                // 注入 Spring Security 上下文，支持无缝通过认证
                List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLATFORM_USER"));
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(context, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                authSet = true;
            } catch (Exception e) {
                // Token 解析失败
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (authSet) {
                CallerContextHolder.clear();
                SecurityContextHolder.clearContext();
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String token = request.getHeader(TOKEN_HEADER);

        // 1. 公开登录接口无需拦截
        if (path.equals("/platform/login")) {
            return true;
        }

        // 2. 中台开放API由API Key过滤器独立处理
        if (path.startsWith("/platform/api/")) {
            return true;
        }

        // 3. 其他 /platform/** 接口（如 /platform/getInfo, /platform/console/** 等）进入本 Filter
        if (path.startsWith("/platform/")) {
            return false;
        }

        // 4. 携带 Platform-Token 的 AI 模块接口也进入本 Filter
        if (path.startsWith("/ai/") && token != null && !token.isEmpty()) {
            return false;
        }

        return true;
    }
}
