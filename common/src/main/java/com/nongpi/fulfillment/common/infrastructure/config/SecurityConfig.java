package com.nongpi.fulfillment.common.infrastructure.config;

import com.nongpi.fulfillment.common.infrastructure.util.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置
 * <p>无状态 JWT，CSRF 关闭，CORS 白名单允许前端跨域，/api/auth/** 匿名访问。</p>
 */
@Configuration
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;

    /** CORS 允许的跨域来源（逗号分隔）— 生产通过环境变量 CORS_ALLOWED_ORIGINS 覆盖 */
    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:3001}")
    private String allowedOrigins;

    public SecurityConfig(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                // 仅放行登录接口；/api/health、/api/test/**、/api/ai/trace/**、/api/trace/chat/**
                // 均无对应 Controller（AI 模块已下线），幽灵白名单已清除
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/lots/inbound", "/api/lots/outbound", "/api/lots/transfer").hasRole("ADMIN")
                .requestMatchers("/api/inventory/adjust", "/api/inventory/freeze", "/api/inventory/unfreeze").hasRole("ADMIN")
                .requestMatchers("/api/alerts/{id}/handle", "/api/alerts/check",
                        "/api/alerts/rules", "/api/alerts/rules/**").hasRole("ADMIN")
                // 商品主数据：GET 列表/详情=登录可看；POST 新建 / DELETE 删除=仅管理员
                .requestMatchers(HttpMethod.GET, "/api/skus", "/api/skus/**").authenticated()
                .requestMatchers("/api/skus/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthenticationFilter(tokenProvider),
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS 白名单 — 只允许配置的前端来源跨域
     * <p>前端直连后端（localhost:3000 → localhost:8080）需要跨域；生产通过
     * {@code CORS_ALLOWED_ORIGINS} 环境变量收敛为显式白名单，禁止 {@code *} 通配。</p>
     */
    private CorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
