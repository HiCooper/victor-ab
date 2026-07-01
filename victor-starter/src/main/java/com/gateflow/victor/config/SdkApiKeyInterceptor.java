package com.gateflow.victor.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SDK 面向接口的 API Key 校验拦截器（/config、/bucketing、/events）。
 * <p>
 * 配置了 {@code victor.security.sdk-api-keys}（逗号分隔）时，要求请求头 {@code X-API-Key}
 * 命中其中之一，否则 401；未配置则不启用校验（向后兼容，SDK 侧改造前不影响现网）。
 */
@Slf4j
@Component
public class SdkApiKeyInterceptor implements HandlerInterceptor {

    private final Set<String> validApiKeys;

    public SdkApiKeyInterceptor(@Value("${victor.security.sdk-api-keys:}") String apiKeysCsv) {
        this.validApiKeys = Arrays.stream(apiKeysCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 是否启用了 API Key 校验（配置了至少一个 key）。 */
    public boolean isEnabled() {
        return !validApiKeys.isEmpty();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (validApiKeys.isEmpty()) {
            return true; // 未配置 → 不启用校验
        }
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || !validApiKeys.contains(apiKey)) {
            log.warn("Rejected SDK request with missing/invalid API key: uri={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"INVALID_API_KEY\",\"message\":\"无效的 API Key\"}");
            return false;
        }
        return true;
    }
}
