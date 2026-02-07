package io.github.jerryt92.jrag.interceptor;

import io.github.jerryt92.jrag.config.annotation.RequiredRole;
import io.github.jerryt92.jrag.model.security.SessionBo;
import io.github.jerryt92.jrag.service.security.LoginService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 全局登录拦截器
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final ApiLoginChecker apiLoginChecker;
    private final LoginService loginService;

    public LoginInterceptor(ApiLoginChecker apiLoginChecker, LoginService loginService) {
        this.apiLoginChecker = apiLoginChecker;
        this.loginService = loginService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Cookie[] cookies = request.getCookies();
        int port = request.getServerPort();
        boolean checkedLogin = apiLoginChecker.checkLogin(cookies, port);
        if (!checkedLogin) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        if (!hasRequiredRole(handler)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }

    private boolean hasRequiredRole(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequiredRole requiredRole = handlerMethod.getMethodAnnotation(RequiredRole.class);
        if (requiredRole == null) {
            requiredRole = handlerMethod.getBeanType().getAnnotation(RequiredRole.class);
        }
        int requiredRoleValue = requiredRole == null ? 1 : requiredRole.value();
        SessionBo session = loginService.getSession();
        return session != null && session.hasAccess(requiredRoleValue);
    }
}