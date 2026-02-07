package io.github.jerryt92.jrag.controller;

import com.alibaba.fastjson2.JSONObject;
import io.github.jerryt92.jrag.service.llm.mcp.McpService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/rest/jrag/mcp")
public class McpController {
    private final McpService mcpService;

    public McpController(McpService mcpService) {
        this.mcpService = mcpService;
    }

    @GetMapping("/config")
    public ResponseEntity<JSONObject> getConfig() {
        return ResponseEntity.ok(mcpService.getMcpConfig());
    }

    @PutMapping("/config")
    public ResponseEntity<Void> updateConfig(@RequestBody JSONObject config) {
        mcpService.updateMcpConfig(config);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/status")
    public ResponseEntity<List<Map<String, Object>>> getStatus() {
        return ResponseEntity.ok(mcpService.getMcpServerStatus());
    }
}
