package com.polaris.framework.config;

import com.polaris.framework.config.properties.PermitAllUrlProperties;
import com.polaris.framework.security.filter.JwtAuthenticationTokenFilter;
import com.polaris.framework.security.handle.AuthenticationEntryPointImpl;
import com.polaris.framework.security.handle.LogoutSuccessHandlerImpl;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.filter.CorsFilter;

/**
 * spring security配置
 * 
 * @author polaris
 */
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@Configuration
public class SecurityConfig
{
    /**
     * 认证失败处理类
     */
    @Autowired
    private AuthenticationEntryPointImpl unauthorizedHandler;

    /**
     * 退出处理类
     */
    @Autowired
    private LogoutSuccessHandlerImpl logoutSuccessHandler;

    /**
     * token认证过滤器
     */
    @Autowired
    private JwtAuthenticationTokenFilter authenticationTokenFilter;
    
    /**
     * 跨域过滤器
     */
    @Autowired
    private CorsFilter corsFilter;

    /**
     * 中台JWT认证过滤器
     */
    @Autowired(required = false)
    private com.polaris.platform.auth.PlatformJwtFilter platformJwtFilter;

    /**
     * 中台API Key认证过滤器
     */
    @Autowired(required = false)
    private com.polaris.platform.auth.ApiKeyAuthFilter apiKeyAuthFilter;

    /**
     * 允许匿名访问的地址
     */
    @Autowired
    private PermitAllUrlProperties permitAllUrl;

	/**
	 * 身份验证实现
	 */
	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception 
	{
		return authenticationConfiguration.getAuthenticationManager();
	}

    @Bean
    protected SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception
    {
        httpSecurity
            // CSRF禁用，因为不使用session
            .csrf(csrf -> csrf.disable())
            // 禁用HTTP响应标头
            .headers((headersCustomizer) -> {
                headersCustomizer.cacheControl(cache -> cache.disable()).frameOptions(options -> options.sameOrigin());
            })
            // 认证失败处理类
            .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
            // 基于token，所以不需要session
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 注解标记允许匿名访问的url
            .authorizeHttpRequests((requests) -> {
                permitAllUrl.getUrls().forEach(url -> requests.requestMatchers(url).permitAll());
                // 允许所有异步分派请求（支持 SSE / SseEmitter 等异步请求）
                requests.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll();
                // 对于登录login 注册register 验证码captchaImage 允许匿名访问
                requests.requestMatchers("/login", "/register", "/captchaImage", "/captcha/get", "/captcha/check").permitAll()
                    // 静态资源，可匿名访问
                    .requestMatchers(HttpMethod.GET, "/", "/*.html", "/**.html", "/**.css", "/**.js", "/profile/**").permitAll()
                    .requestMatchers("/swagger-ui.html", "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/druid/**", "/doc.html", "/webjars/**").permitAll()
                    // 中台登录接口允许匿名访问，其他接口按认证类型分别鉴权
                    .requestMatchers("/platform/login").permitAll()
                    .requestMatchers("/platform/api/**").hasRole("PLATFORM_API")
                    .requestMatchers("/platform/**").hasRole("PLATFORM_USER")
                    // 除上面外的所有请求全部需要鉴权认证
                    .anyRequest().authenticated();
            })
            // 添加Logout filter
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessHandler(logoutSuccessHandler));

        // 添加中台 JWT filter (如果存在)
        if (platformJwtFilter != null) {
            httpSecurity.addFilterBefore(platformJwtFilter, UsernamePasswordAuthenticationFilter.class);
        }
        // 添加中台 API Key filter (如果存在)
        if (apiKeyAuthFilter != null) {
            if (platformJwtFilter != null) {
                httpSecurity.addFilterBefore(apiKeyAuthFilter, com.polaris.platform.auth.PlatformJwtFilter.class);
            } else {
                httpSecurity.addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);
            }
        }
        // 添加管理端 JWT filter
        httpSecurity.addFilterBefore(authenticationTokenFilter, UsernamePasswordAuthenticationFilter.class);
        // 添加CORS filter
        httpSecurity.addFilterBefore(corsFilter, LogoutFilter.class);

        return httpSecurity.build();
    }

    /**
     * 强散列哈希加密实现
     */
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder()
    {
        return new BCryptPasswordEncoder();
    }
}
