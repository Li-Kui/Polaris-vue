package com.polaris.ai.context;

import com.polaris.ai.core.context.CallerContextHolder;
import com.polaris.common.core.domain.model.LoginUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 在 Spring Security JWT 过滤器之后执行，将已认证的用户信息填充到 CallerContextHolder。
 * 这样 AI Service 层可以统一通过 CallerContextHolder 获取调用者信息。
 */
@Component
public class SecurityCallerContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
                CallerContextHolder.set(new SecurityCallerContext(loginUser));
            }
            filterChain.doFilter(request, response);
        } finally {
            CallerContextHolder.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 中台路径由其他 Filter 处理
        return path.startsWith("/platform/");
    }
}
