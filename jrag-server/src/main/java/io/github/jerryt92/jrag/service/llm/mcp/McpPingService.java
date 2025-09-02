package io.github.jerryt92.jrag.service.llm.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@EnableScheduling
public class McpPingService {
    private final McpService mcpService;

    public McpPingService(McpService mcpService) {
        this.mcpService = mcpService;
    }

    @Scheduled(fixedRate = 15, timeUnit = TimeUnit.SECONDS)
    private void pingMcpServer() {
        try {
            for (Map.Entry<String, McpSyncClient> stringMcpSyncClientEntry : mcpService.mcpName2Client.entrySet()) {
                McpSyncClient mcpSyncClient = stringMcpSyncClientEntry.getValue();
                String mcpServerName = stringMcpSyncClientEntry.getKey();
                if (!mcpService.mcpStdioServerParameters.containsKey(mcpServerName)) {
                    // Stdio类型的MCP Server无需Ping
                    try {
                        mcpSyncClient.ping();
                    } catch (Exception e) {
                        log.error("MCP server {} ping failed", mcpSyncClient.getServerInfo());
                        reconnectMcpServer(mcpServerName);
                    }
                }
            }
        } catch (Throwable e) {
            log.error("", e);
        }
    }

    private void reconnectMcpServer(String mcpServerName) {
        McpSyncClient oldMcpSyncClient = mcpService.mcpName2Client.get(mcpServerName);
        try {
            Set<String> tools = mcpService.registerMcpTools(mcpServerName);
            log.info("MCP server {} reconnect success, has {} tools", mcpServerName, tools.size());
        } catch (Throwable e) {
            log.error("MCP server {} reconnect failed", oldMcpSyncClient.getServerInfo());
        }
    }
}
