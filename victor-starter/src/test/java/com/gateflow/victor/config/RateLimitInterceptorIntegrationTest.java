package com.gateflow.victor.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RateLimitInterceptor 集成测试（真实 Redis）。
 * <p>
 * 无 Docker 时由 {@code @Testcontainers(disabledWithoutDocker = true)} 自动跳过整个类；
 * 有 Docker（CI）时验证：按客户端隔离计数、超阈值 429、计数 key 被原子设置 TTL。
 */
@Testcontainers(disabledWithoutDocker = true)
class RateLimitInterceptorIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private RateLimitInterceptor interceptor;
    private StringRedisTemplate redisTemplate;

    /** 承载 @RateLimit 注解的假处理器方法。 */
    static class RateLimitedHandler {
        @RateLimit(value = 3, windowSeconds = 2)
        public void limited() {
        }
    }

    private HandlerMethod handler() throws NoSuchMethodException {
        return new HandlerMethod(new RateLimitedHandler(),
                RateLimitedHandler.class.getMethod("limited"));
    }

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory cf =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        cf.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(cf);
        redisTemplate.afterPropertiesSet();

        interceptor = new RateLimitInterceptor();
        ReflectionTestUtils.setField(interceptor, "redisTemplate", redisTemplate);
    }

    private MockHttpServletRequest request(String apiKey) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/config/version");
        req.addHeader("X-API-Key", apiKey);
        return req;
    }

    @Test
    @DisplayName("超过阈值返回 429，计数按客户端隔离")
    void limitsPerClientAndReturns429() throws Exception {
        HandlerMethod hm = handler();

        // client A：前 3 次放行，第 4 次 429
        for (int i = 0; i < 3; i++) {
            assertTrue(interceptor.preHandle(request("A"), new MockHttpServletResponse(), hm),
                    "第 " + (i + 1) + " 次应放行");
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request("A"), blocked, hm));
        assertEquals(429, blocked.getStatus());

        // client B：独立计数，不受 A 影响
        assertTrue(interceptor.preHandle(request("B"), new MockHttpServletResponse(), hm));
    }

    @Test
    @DisplayName("计数 key 被原子设置 TTL（INCR+EXPIRE 一次完成）")
    void setsTtlAtomically() throws Exception {
        interceptor.preHandle(request("C"), new MockHttpServletResponse(), handler());

        Long ttl = redisTemplate.getExpire("rate:key:C:/api/v1/config/version", TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 2, "TTL 应在 (0,2] 秒内，实际 " + ttl);
    }
}
