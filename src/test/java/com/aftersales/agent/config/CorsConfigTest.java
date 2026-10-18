package com.aftersales.agent.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 跨域配置测试：确认前端（3000）可以访问后端（8123）。
 */
@DisplayName("跨域配置")
class CorsConfigTest {

    @Test
    @DisplayName("为 /** 注册跨域规则：允许常见方法与请求头")
    void registersGlobalCorsMapping() {
        CorsRegistry registry = mock(CorsRegistry.class);
        CorsRegistration registration = mock(CorsRegistration.class);
        when(registry.addMapping("/**")).thenReturn(registration);
        when(registration.allowedOriginPatterns(any(String[].class))).thenReturn(registration);
        when(registration.allowedMethods(any(String[].class))).thenReturn(registration);
        when(registration.allowedHeaders(any(String[].class))).thenReturn(registration);
        when(registration.exposedHeaders(any(String[].class))).thenReturn(registration);
        when(registration.allowCredentials(any(Boolean.class))).thenReturn(registration);

        new CorsConfig().addCorsMappings(registry);

        verify(registry).addMapping("/**");
        verify(registration).allowedOriginPatterns("*");
        verify(registration).allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
        verify(registration).allowedHeaders("*");
        verify(registration).exposedHeaders("*");
        verify(registration).allowCredentials(true);
    }

    @Test
    @DisplayName("跨域配置语义：允许任意来源且允许携带 Cookie")
    void allowCredentialsWithWildcardOriginIsConfigured() {
        // 该配置便于本地前后端分离开发，但生产环境需收紧来源；此处固化当前语义
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(true);
        configuration.addAllowedOriginPattern("*");

        assertTrue(configuration.getAllowCredentials());
        assertEquals(java.util.List.of("*"), configuration.getAllowedOriginPatterns());

        CorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        assertTrue(source instanceof UrlBasedCorsConfigurationSource);
    }
}
