package io.github.jerryt92.jrag.controller;

import com.alibaba.fastjson2.JSONObject;
import io.github.jerryt92.jrag.server.api.HealthCheckApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import io.github.jerryt92.jrag.config.annotation.RequiredRole;

@RestController
@RequiredRole(1)
public class HealthCheckController implements HealthCheckApi {
    @Override
    public ResponseEntity<Object> checkHealth() {
        return ResponseEntity.ok(JSONObject.of(
                "status", "OK"
        ));
    }
}
