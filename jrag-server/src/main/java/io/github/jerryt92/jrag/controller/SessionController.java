package io.github.jerryt92.jrag.controller;

import io.github.jerryt92.jrag.config.annotation.RequiredRole;
import io.github.jerryt92.jrag.model.SessionInfoDto;
import io.github.jerryt92.jrag.model.security.SessionBo;
import io.github.jerryt92.jrag.service.security.LoginService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredRole(2)
@RequestMapping("/v1/rest/jrag/session")
public class SessionController {
    private final LoginService loginService;

    public SessionController(LoginService loginService) {
        this.loginService = loginService;
    }

    @GetMapping
    public ResponseEntity<SessionInfoDto> getSessionInfo() {
        SessionBo session = loginService.getSession();
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        SessionInfoDto dto = new SessionInfoDto();
        dto.setUserId(session.getUserId());
        dto.setUsername(session.getUsername());
        dto.setRole(session.getRole());
        return ResponseEntity.ok(dto);
    }
}
