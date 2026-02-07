package io.github.jerryt92.jrag.controller;

import com.alibaba.fastjson2.JSONObject;
import io.github.jerryt92.jrag.model.McpStatusItem;
import io.github.jerryt92.jrag.server.api.McpApi;
import io.github.jerryt92.jrag.service.llm.mcp.McpService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class McpController implements McpApi {
    private final McpService mcpService;

    public McpController(McpService mcpService) {
        this.mcpService = mcpService;
    }

    @Override
    public ResponseEntity<Object> getMcpConfig() {
        return ResponseEntity.ok(mcpService.getMcpConfig());
    }

    @Override
    public ResponseEntity<List<McpStatusItem>> getMcpStatus() {
        return ResponseEntity.ok(mcpService.getMcpServerStatus());
    }

    @Override
    public ResponseEntity<Void> updateMcpConfig(Object body) {
        if (body == null) {
            return ResponseEntity.badRequest().build();
        }
        JSONObject config;
        if (body instanceof JSONObject jsonObject) {
            config = jsonObject;
        } else if (body instanceof Map<?, ?> mapBody) {
            config = new JSONObject((Map<String, Object>) mapBody);
        } else {
            config = JSONObject.parseObject(JSONObject.toJSONString(body));
        }
        mcpService.updateMcpConfig(config);
        return ResponseEntity.ok().build();
    }
}
