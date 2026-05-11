package com.gateflow.victor.config;

import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.service.rbac.RbacService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * RBAC 权限拦截器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    private final RbacService rbacService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取用户ID (从header或模拟)
        String userIdHeader = request.getHeader("X-User-Id");
        Long userId = userIdHeader != null ? Long.parseLong(userIdHeader) : 1L;

        // 获取请求的权限要求
        String requiredPermission = (String) request.getAttribute("requiredPermission");

        if (requiredPermission != null) {
            try {
                Permission permission = Permission.valueOf(requiredPermission);
                if (!rbacService.hasPermission(userId, permission)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"权限不足\",\"code\":\"FORBIDDEN\"}");
                    return false;
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid permission: {}", requiredPermission);
            }
        }

        return true;
    }
}