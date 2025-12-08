package io.github.jerryt92.jrag.interceptor;

import io.github.jerryt92.jrag.service.security.LoginService;
import jakarta.servlet.http.Cookie;
import org.springframework.stereotype.Component;

@Component
public class ApiLoginChecker {
    private final LoginService loginService;

    public ApiLoginChecker(LoginService loginService) {
        this.loginService = loginService;
    }

    public boolean checkLogin(Cookie[] cookies, int port) {
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("SESSION-" + port)) {
                    if (cookie.getValue() != null) {
                        if (loginService.validateSession(cookie.getValue())) {
                            loginService.sessionThreadLocal.set(cookie.getValue());
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
