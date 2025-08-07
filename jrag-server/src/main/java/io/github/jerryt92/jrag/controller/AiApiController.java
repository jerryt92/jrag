package io.github.jerryt92.jrag.controller;

import io.github.jerryt92.jrag.model.CheckApCenterApiResponse;
import io.github.jerryt92.jrag.server.api.AiApiApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiApiController implements AiApiApi {

    @Override
    public ResponseEntity<CheckApCenterApiResponse> checkApCenterApi() {
        CheckApCenterApiResponse response = new CheckApCenterApiResponse();
        response.setStatus(CheckApCenterApiResponse.StatusEnum.NORMAL);
        response.setDescription("AI center api is normal.");
        return ResponseEntity.ok(response);
    }
}
