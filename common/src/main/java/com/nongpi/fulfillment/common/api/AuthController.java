package com.nongpi.fulfillment.common.api;

import com.nongpi.fulfillment.common.api.dto.LoginRequest;
import com.nongpi.fulfillment.common.exception.BusinessException;
import com.nongpi.fulfillment.common.infrastructure.util.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import java.util.Map;

/**
 * 认证 REST 控制器
 * <p>POST /api/auth/login — 用户名密码换 JWT token。</p>
 * <p>hardcoded admin/admin123，MVP 阶段不引入 UserDetailsService。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String ADMIN_PASSWORD = "admin123";

    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final String adminHash;

    public AuthController(JwtTokenProvider tokenProvider, PasswordEncoder passwordEncoder) {
        this.tokenProvider = tokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.adminHash = passwordEncoder.encode(ADMIN_PASSWORD);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        if (!ADMIN_USER.equals(req.username())
                || !passwordEncoder.matches(req.password(), adminHash)) {
            throw new BusinessException(401, "INVALID_CREDENTIALS", "用户名或密码错误");
        }
        String token = tokenProvider.generateToken(req.username(), ADMIN_ROLE);
        return Map.of(
                "token", token,
                "tokenType", "Bearer",
                "username", req.username(),
                "role", ADMIN_ROLE
        );
    }
}
