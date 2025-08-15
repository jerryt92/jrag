package io.github.jerryt92.jrag.controller;

import io.github.jerryt92.jrag.model.CheckApiResponse;
import io.github.jerryt92.jrag.server.api.AiApiApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiApiController implements AiApiApi {

    @Override
    public ResponseEntity<CheckApiResponse> checkApi() {
        CheckApiResponse response = new CheckApiResponse();
        response.setStatus(CheckApiResponse.StatusEnum.NORMAL);
        response.setDescription("AI center api is normal.");
        return ResponseEntity.ok(response);
    }
}
