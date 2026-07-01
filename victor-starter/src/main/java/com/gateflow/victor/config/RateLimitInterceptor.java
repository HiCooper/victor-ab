package com.gateflow.victor.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 基于 Redis 的限流拦截器。
 * <p>
 * 按「客户端身份 + 接口」计数（而非全局按 URI），避免单个客户端打满共享 SDK 接口而误伤所有人。
 * 计数与过期通过 Lua 脚本原子完成，杜绝 INCR 与 EXPIRE 两步之间进程崩溃导致 key 永不过期。
 * Redis 不可用时降级为放行，不阻塞请求。
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /** 原子递增并在首次计数时设置过期时间。返回当前窗口内计数。 */
    private static final RedisScript<Long> INCR_WITH_EXPIRE = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return c",
            Long.class);

    @Autowired(required = false)
    @Lazy
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        RateLimit annotation = hm.getMethodAnnotation(RateLimit.class);
        if (annotation == null || redisTemplate == null) {
            return true;
        }

        String clientId = resolveClientId(request);
        String key = "rate:" + clientId + ":" + request.getRequestURI();
        int windowSeconds = Math.max(1, annotation.windowSeconds());

        Long count = redisTemplate.execute(INCR_WITH_EXPIRE, List.of(key), String.valueOf(windowSeconds));

        if (count != null && count > annotation.value()) {
            log.warn("Rate limit exceeded: client={}, uri={}, count={}", clientId, request.getRequestURI(), count);
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write(
                    "{\"code\":\"RATE_LIMITED\",\"message\":\"请求过于频繁，请稍后重试\"}");
            return false;
        }

        return true;
    }

    /** 客户端身份：优先 API Key，其次 X-Forwarded-For 首段，最后 remoteAddr。 */
    private String resolveClientId(HttpServletRequest request) {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return "key:" + apiKey;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return "ip:" + (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
