package com.gateflow.victor.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SdkApiKeyInterceptor 单元测试。
 */
class SdkApiKeyInterceptorTest {

    @Test
    @DisplayName("未配置 key → 不启用校验，放行")
    void disabledWhenNoKeys() throws Exception {
        SdkApiKeyInterceptor interceptor = new SdkApiKeyInterceptor("");
        assertFalse(interceptor.isEnabled());
        assertTrue(interceptor.preHandle(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()));
    }

    @Test
    @DisplayName("配置了 key，请求缺失/错误 X-API-Key → 401")
    void rejectsInvalidKey() throws Exception {
        SdkApiKeyInterceptor interceptor = new SdkApiKeyInterceptor("k1, k2");
        assertTrue(interceptor.isEnabled());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());

        // 完全缺失
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(new MockHttpServletRequest(), response2, new Object()));
        assertEquals(401, response2.getStatus());
    }

    @Test
    @DisplayName("配置了 key，请求携带有效 X-API-Key → 放行")
    void acceptsValidKey() throws Exception {
        SdkApiKeyInterceptor interceptor = new SdkApiKeyInterceptor("k1, k2");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-API-Key", "k2");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }
}
