package com.mu.transitmap.config;

import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import com.mu.transitmap.websocket.AgentWebSocketHandler;
import com.mu.transitmap.websocket.AgentWebSocketInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    @Autowired
    private AgentWebSocketHandler agentHandler;

    @Autowired
    private AgentWebSocketInterceptor authInterceptor;

    @Autowired
    private SystemConfigServiceImpl configService;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = resolveAllowedOrigins();
        var reg = registry.addHandler(agentHandler, "/ws/agent")
                .addInterceptors(authInterceptor);
        if (origins.length == 1 && "*".equals(origins[0])) {
            reg.setAllowedOriginPatterns("*");
            log.warn("WebSocket Origin = *  生产环境请到「Agent 配置 → WebSocket」配置具体域名白名单");
        } else {
            reg.setAllowedOrigins(origins);
            log.info("WebSocket allowed origins: {}", Arrays.toString(origins));
        }
    }

    private String[] resolveAllowedOrigins() {
        String raw;
        try {
            raw = configService.getConfigValue("agent.security.ws_allowed_origins");
        } catch (Exception e) {
            raw = "*";
        }
        if (raw == null || raw.isBlank()) return new String[]{"*"};
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    @Bean
    public ServletServerContainerFactoryBean wsContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(16 * 1024);
        container.setMaxBinaryMessageBufferSize(128 * 1024);
        container.setMaxSessionIdleTimeout(5 * 60 * 1000L);
        return container;
    }
}
