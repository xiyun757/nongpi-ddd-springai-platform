package com.nongpi.fulfillment.ai.tools;

import com.nongpi.fulfillment.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 工具权限校验助手 — 在写操作工具方法内调用 {@link #requireAdmin()}
 * <p>通过 {@link SecurityContextHolder} 获取当前 JWT 认证用户角色，
 * 非 ADMIN 抛 {@link BusinessException}(403)。</p>
 */
public final class ToolPermission {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private ToolPermission() {
    }

    /**
     * 校验当前用户是否为管理员，否则抛 BusinessException(403)
     */
    public static void requireAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream()
                .noneMatch(a -> ROLE_ADMIN.equals(a.getAuthority()))) {
            throw new BusinessException(403, "FORBIDDEN", "需要管理员权限执行此操作");
        }
    }
}
