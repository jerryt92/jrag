package io.github.jerryt92.jrag.interceptor;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 全局登录拦截器
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final ApiLoginChecker apiLoginChecker;

    public LoginInterceptor(ApiLoginChecker apiLoginChecker) {
        this.apiLoginChecker = apiLoginChecker;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Cookie[] cookies = request.getCookies();
        int port = request.getServerPort();
        boolean checkedLogin = apiLoginChecker.checkLogin(cookies, port);
        if (checkedLogin) {
            return true;
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }
}