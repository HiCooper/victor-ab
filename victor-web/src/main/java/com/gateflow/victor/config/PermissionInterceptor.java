package com.gateflow.victor.config;

import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.service.rbac.RbacService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * RBAC 权限拦截器
 * 拦截 /api/v1/** 请求，检查 @RequirePermission 注解标注的方法，
 * 验证当前用户是否拥有所需权限。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    private final RbacService rbacService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 读取方法上的 @RequirePermission 注解
        RequirePermission requirePermission = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (requirePermission == null) {
            return true; // 无注解 = 无需鉴权
        }

        // 获取用户ID，无认证信息直接拒绝
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader == null || userIdHeader.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"未认证\",\"code\":\"UNAUTHORIZED\"}");
            return false;
        }
        Long userId = Long.parseLong(userIdHeader);

        Permission required = requirePermission.value();
        if (!rbacService.hasPermission(userId, required)) {
            log.warn("Permission denied: userId={}, permission={}, path={}",
                userId, required, request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"权限不足\",\"code\":\"FORBIDDEN\",\"permission\":\"" + required.name() + "\"}");
            return false;
        }

        return true;
    }
}