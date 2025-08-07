package io.github.jerryt92.jrag.config.annotation;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

/**
 * 2024-03-07 AutoRegisterWebSocketHandler注解处理器
 *
 * @author tianjingli
 */
@Configuration
@EnableWebSocket
public class WebSocketHandlerAutoRegister implements WebSocketConfigurer {
    final List<WebSocketHandler> webSocketHandlers;

    public WebSocketHandlerAutoRegister(List<WebSocketHandler> webSocketHandlers) {
        this.webSocketHandlers = webSocketHandlers;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        if (null != webSocketHandlers && !webSocketHandlers.isEmpty()) {
            for (WebSocketHandler webSocketHandler : webSocketHandlers) {
                if (webSocketHandler.getClass().isAnnotationPresent(AutoRegisterWebSocketHandler.class)) {
                    AutoRegisterWebSocketHandler annotation =
                            webSocketHandler.getClass().getDeclaredAnnotation(AutoRegisterWebSocketHandler.class);
                    registry.addHandler(webSocketHandler, annotation.path())
                            .setAllowedOrigins(annotation.allowedOrigin());
                }
            }
        }
    }
}