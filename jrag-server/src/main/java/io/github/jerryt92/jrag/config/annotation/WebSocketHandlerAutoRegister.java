package io.github.jerryt92.jrag.config.annotation;

import io.github.jerryt92.jrag.config.CommonProperties;
import io.github.jerryt92.jrag.interceptor.WebsocketLoginInterceptor;
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
    private final WebsocketLoginInterceptor websocketLoginInterceptor;
    private final CommonProperties commonProperties;

    public WebSocketHandlerAutoRegister(List<WebSocketHandler> webSocketHandlers, WebsocketLoginInterceptor websocketLoginInterceptor, CommonProperties commonProperties) {
        this.commonProperties = commonProperties;
        this.webSocketHandlers = webSocketHandlers;
        this.websocketLoginInterceptor = websocketLoginInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        if (null != webSocketHandlers && !webSocketHandlers.isEmpty()) {
            for (WebSocketHandler webSocketHandler : webSocketHandlers) {
                if (webSocketHandler.getClass().isAnnotationPresent(AutoRegisterWebSocketHandler.class)) {
                    AutoRegisterWebSocketHandler annotation =
                            webSocketHandler.getClass().getDeclaredAnnotation(AutoRegisterWebSocketHandler.class);
                    if (!commonProperties.publicMode) {
                        registry.addHandler(webSocketHandler, annotation.path())
                                .addInterceptors(websocketLoginInterceptor)
                                .setAllowedOrigins(annotation.allowedOrigin());
                    } else {
                        registry.addHandler(webSocketHandler, annotation.path())
                                .setAllowedOrigins(annotation.allowedOrigin());
                    }
                }
            }
        }
    }
}