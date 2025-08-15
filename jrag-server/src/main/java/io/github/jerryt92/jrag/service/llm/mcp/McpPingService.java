package io.github.jerryt92.jrag.service.llm.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.client.autoconfigure.properties.McpSseClientProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
            Set<McpSyncClient> mcpSyncClients = mcpService.mcpClient2name.keySet();
            for (McpSyncClient mcpSyncClient : mcpSyncClients) {
                try {
                    mcpSyncClient.ping();
                } catch (Exception e) {
                    log.error("MCP server {} ping failed", mcpSyncClient.getServerInfo());
                    reconnectMcpServer(mcpSyncClient);
                }
            }
        } catch (Throwable e) {
            log.error("", e);
        }
    }

    private void reconnectMcpServer(McpSyncClient oldMcpSyncClient) {
        try {
            String mcpServerName = mcpService.mcpClient2name.remove(oldMcpSyncClient);
            McpSseClientProperties.SseParameters sseParameters = mcpService.mcpServerParameters.get(mcpServerName);
            Set<String> tools = mcpService.registerMcpTools(mcpServerName, sseParameters);
            log.info("MCP server {} reconnect success, has {} tools", mcpServerName, tools.size());
        } catch (Throwable e) {
            log.error("MCP server {} reconnect failed", oldMcpSyncClient.getServerInfo());
        }
    }
}
