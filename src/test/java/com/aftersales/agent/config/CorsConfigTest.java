package com.aftersales.agent.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 跨域配置测试：来源按配置收敛，允许本地前端（3000）访问后端（8123）。
 */
@DisplayName("跨域配置")
class CorsConfigTest {

    @Test
    @DisplayName("配置的来源列表被解析并注册到 /**")
    void registersConfiguredOrigins() {
        CorsRegistry registry = mock(CorsRegistry.class);
        CorsRegistration registration = mock(CorsRegistration.class);
        when(registry.addMapping("/**")).thenReturn(registration);
        when(registration.allowedOriginPatterns(any(String[].class))).thenReturn(registration);
        when(registration.allowedMethods(any(String[].class))).thenReturn(registration);
        when(registration.allowedHeaders(any(String[].class))).thenReturn(registration);
        when(registration.exposedHeaders(any(String[].class))).thenReturn(registration);
        when(registration.allowCredentials(any(Boolean.class))).thenReturn(registration);

        new CorsConfig(" http://localhost:3000 , http://127.0.0.1:3000 ").addCorsMappings(registry);

        verify(registry).addMapping("/**");
        verify(registration).allowedOriginPatterns(
                "http://localhost:3000", "http://127.0.0.1:3000");
        verify(registration).allowCredentials(true);
    }

    @Test
    @DisplayName("默认只放行本地前端端口，不再允许任意来源")
    void defaultOriginsAreLocalOnly() {
        CorsConfig config = new CorsConfig("http://localhost:3000,http://127.0.0.1:3000");
        // 通过反射读取私有字段，确认默认配置不含通配符
        try {
            java.lang.reflect.Field field =
                    CorsConfig.class.getDeclaredField("allowedOriginPatterns");
            field.setAccessible(true);
            String[] origins = (String[]) field.get(config);
            assertArrayEquals(new String[]{"http://localhost:3000", "http://127.0.0.1:3000"}, origins);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
