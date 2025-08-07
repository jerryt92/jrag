package io.github.jerryt92.jrag.interceptor;

import io.github.jerryt92.jrag.service.security.LoginService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 全局登录拦截器
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final LoginService loginService;

    public LoginInterceptor(LoginService loginService) {
        this.loginService = loginService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("SESSION")) {
                    if (cookie.getValue() != null) {
                        if (loginService.validateSession(cookie.getValue())) {
                            loginService.sessionThreadLocal.set(cookie.getValue());
                            return true;
                        }
                    }
                }
            }
        }
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }
}