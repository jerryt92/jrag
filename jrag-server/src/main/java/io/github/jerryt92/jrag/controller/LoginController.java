package io.github.jerryt92.jrag.controller;

import io.github.jerryt92.jrag.config.CommonProperties;
import io.github.jerryt92.jrag.model.GetMode200Response;
import io.github.jerryt92.jrag.model.LoginRequestDto;
import io.github.jerryt92.jrag.model.SlideCaptchaResp;
import io.github.jerryt92.jrag.model.VerifySlideCaptcha200Response;
import io.github.jerryt92.jrag.model.security.SessionBo;
import io.github.jerryt92.jrag.server.api.LoginApi;
import io.github.jerryt92.jrag.service.security.CaptchaService;
import io.github.jerryt92.jrag.service.security.LoginService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController implements LoginApi {
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final LoginService loginService;
    private final CaptchaService captchaService;
    private final CommonProperties commonProperties;

    public LoginController(HttpServletRequest request, HttpServletResponse response, LoginService loginService, CaptchaService captchaService, CommonProperties commonProperties) {
        this.request = request;
        this.response = response;
        this.loginService = loginService;
        this.captchaService = captchaService;
        this.commonProperties = commonProperties;
    }

    @Override
    public ResponseEntity<GetMode200Response> getMode() {
        return ResponseEntity.ok(new GetMode200Response().mode(commonProperties.publicMode ? GetMode200Response.ModeEnum.PUBLIC : GetMode200Response.ModeEnum.USER));
    }

    @Override
    public ResponseEntity<Void> login(LoginRequestDto loginRequestDto) {
        SessionBo sessionBo = loginService.login(loginRequestDto.getUsername(), loginRequestDto.getPassword(), loginRequestDto.getValidateCode(), loginRequestDto.getHash());
        if (sessionBo != null) {
            Cookie cookie = new Cookie("SESSION", sessionBo.getSessionId());
            cookie.setHttpOnly(true); // 阻止JavaScript 访问 Cookie
            cookie.setPath("/");      // 根据实际需求设置 path
            response.addCookie(cookie);
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<SlideCaptchaResp> getSlideCaptcha() {
        return ResponseEntity.ok(captchaService.genSlideCaptcha());
    }

    @Override
    public ResponseEntity<VerifySlideCaptcha200Response> verifySlideCaptcha(Float sliderX, String hash) {
        VerifySlideCaptcha200Response response = new VerifySlideCaptcha200Response();
        String code = captchaService.verifySlideCaptchaGetClassicCaptcha(sliderX, hash);
        if (null != code) {
            response.setResult(true);
            response.setCode(code);
        } else {
            response.setResult(false);
        }
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> logout() {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals("SESSION")) {
                    if (cookie.getValue() != null) {
                        loginService.logout(cookie.getValue());
                    }
                    cookie.setPath("/");
                    cookie.setMaxAge(0);
                    response.addCookie(cookie);
                }
            }
        }
        return ResponseEntity.ok().build();
    }
}
