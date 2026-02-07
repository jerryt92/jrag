package io.github.jerryt92.jrag.interceptor;

import io.github.jerryt92.jrag.model.security.SessionBo;
import io.github.jerryt92.jrag.service.security.LoginService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Websocket全局登录拦截器
 */
@Component
public class WebsocketLoginInterceptor implements HandshakeInterceptor {

    private final ApiLoginChecker apiLoginChecker;
    private final LoginService loginService;

    public WebsocketLoginInterceptor(ApiLoginChecker apiLoginChecker, LoginService loginService) {
        this.apiLoginChecker = apiLoginChecker;
        this.loginService = loginService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        Cookie[] cookies = servletRequest.getCookies();
        int port = servletRequest.getServerPort();
        boolean checkedLogin = apiLoginChecker.checkLogin(cookies, port);
        if (!checkedLogin) {
            response.setStatusCode(HttpStatusCode.valueOf(HttpServletResponse.SC_UNAUTHORIZED));
            return false;
        }
        if (!hasRequiredRole(servletRequest)) {
            response.setStatusCode(HttpStatusCode.valueOf(HttpServletResponse.SC_FORBIDDEN));
            return false;
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

    }

    private boolean hasRequiredRole(HttpServletRequest servletRequest) {
        int requiredRole = isChatPath(servletRequest) ? 2 : 1;
        SessionBo session = loginService.getSession();
        return session != null && session.hasAccess(requiredRole);
    }

    private boolean isChatPath(HttpServletRequest servletRequest) {
        String uri = servletRequest.getRequestURI();
        return uri != null && uri.contains("/ws/rest/jrag/chat");
    }
}