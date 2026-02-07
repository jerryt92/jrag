package io.github.jerryt92.jrag.controller;

import io.github.jerryt92.jrag.model.PropertyDto;
import io.github.jerryt92.jrag.server.api.PropertyApi;
import io.github.jerryt92.jrag.service.PropertiesService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import io.github.jerryt92.jrag.config.annotation.RequiredRole;

import java.util.List;
import java.util.Map;

@RestController
@RequiredRole(1)
public class PropertyController implements PropertyApi {
    private final PropertiesService propertiesService;

    public PropertyController(PropertiesService propertiesService) {
        this.propertiesService = propertiesService;
    }

    @Override
    public ResponseEntity<Map<String, String>> getProperty(List<String> requestBody) {
        return ResponseEntity.ok(propertiesService.getProperties(requestBody));
    }

    @Override
    public ResponseEntity<Void> putProperty(List<@Valid PropertyDto> propertyDto) {
        propertiesService.putProperty(propertyDto);
        return ResponseEntity.ok().build();
    }
}
