package com.gateflow.victor.config;

import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.service.rbac.RbacService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * RBAC permission interceptor.
 * Checks {@code @RequirePermission} annotations against the authenticated user
 * from Spring Security context.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    private final RbacService rbacService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequirePermission requirePermission = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (requirePermission == null) {
            return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"未认证\",\"code\":\"UNAUTHORIZED\"}");
            return false;
        }

        Long userId;
        try {
            userId = Long.valueOf(auth.getPrincipal().toString());
        } catch (NumberFormatException e) {
            // API key auth has principal=0 (system user)
            userId = 0L;
        }

        Permission required = requirePermission.value();
        if (!rbacService.hasPermission(userId, required)) {
            log.warn("Permission denied: userId={}, permission={}, path={}",
                    userId, required, request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"权限不足\",\"code\":\"FORBIDDEN\",\"permission\":\""
                    + required.name() + "\"}");
            return false;
        }

        return true;
    }
}
