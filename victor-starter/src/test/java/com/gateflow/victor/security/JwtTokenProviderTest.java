package com.gateflow.victor.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtTokenProvider 单元测试 —— 重点覆盖内置不安全默认密钥的 fail-fast 行为。
 */
class JwtTokenProviderTest {

    private static final long EXP_MS = 86_400_000L;
    private static final String STRONG_SECRET = "a-very-strong-custom-secret-key-of-64-bytes-length-1234567890abcd";

    @Test
    @DisplayName("生产 profile 使用内置默认密钥 → 拒绝启动")
    void rejectsInsecureDefaultInProd() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtTokenProvider(JwtTokenProvider.INSECURE_DEFAULT_SECRET, EXP_MS, env));
        assertTrue(ex.getMessage().contains("INSECURE default"));
    }

    @Test
    @DisplayName("dev profile 使用内置默认密钥 → 允许（仅告警）")
    void allowsInsecureDefaultInDev() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        JwtTokenProvider provider =
                new JwtTokenProvider(JwtTokenProvider.INSECURE_DEFAULT_SECRET, EXP_MS, env);
        assertNotNull(provider.generateToken(1L, "u", List.of("ADMIN")));
    }

    @Test
    @DisplayName("无 profile 使用内置默认密钥 → 允许（本地运行不被阻断）")
    void allowsInsecureDefaultWithNoProfile() {
        MockEnvironment env = new MockEnvironment();

        assertDoesNotThrow(() ->
                new JwtTokenProvider(JwtTokenProvider.INSECURE_DEFAULT_SECRET, EXP_MS, env));
    }

    @Test
    @DisplayName("生产 profile + 自定义强密钥 → 正常签发与校验")
    void customSecretWorksInProd() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        JwtTokenProvider provider = new JwtTokenProvider(STRONG_SECRET, EXP_MS, env);
        String token = provider.generateToken(7L, "alice", List.of("ADMIN"));

        assertTrue(provider.validateToken(token));
        assertEquals(7L, provider.getUserId(token));
        assertEquals(List.of("ADMIN"), provider.getRoles(token));
    }
}
