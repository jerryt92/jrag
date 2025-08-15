package io.github.jerryt92.jrag.service.llm.mcp;

import com.alibaba.fastjson2.JSONObject;
import io.github.jerryt92.jrag.service.llm.tools.FunctionCallingService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.client.autoconfigure.properties.McpSseClientProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class McpService {

    private final FunctionCallingService functionCallingService;
    private Map<String, McpSseClientProperties.SseParameters> mcpConnections = new HashMap<>();

    @Getter
    private Map<String, McpSyncClient> mcpClients = new HashMap<>();

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20L);

    public McpService(FunctionCallingService functionCallingService) {
        this.functionCallingService = functionCallingService;
    }

    @PostConstruct
    public void init() {
        Thread.startVirtualThread(this::loadMcpServers);
    }

    private void loadMcpServers() {
        // 读取 mcp.json
        try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("mcp.json")) {
            if (inputStream != null) {
                String jsonText = new String(inputStream.readAllBytes());
                JSONObject mcpJson = JSONObject.parseObject(jsonText);
                if (mcpJson != null) {
                    JSONObject mcpServers = mcpJson.getJSONObject("mcpServers");
                    if (mcpServers != null) {
                        Set<String> mcpServerNames = mcpServers.keySet();
                        for (String mcpServerName : mcpServerNames) {
                            JSONObject mcpServer = mcpServers.getJSONObject(mcpServerName);
                            if (mcpServer != null) {
                                if ("sse".equals(mcpServer.getString("type"))) {
                                    try {
                                        URI uri = new URI(mcpServer.getString("url"));
                                        String baseUrl = uri.getScheme() + "://" + uri.getHost();
                                        mcpConnections.put(
                                                mcpServerName,
                                                new McpSseClientProperties.SseParameters(baseUrl, uri.getPath())
                                        );
                                    } catch (URISyntaxException e) {
                                        log.error("", e);
                                    }
                                } else {
                                    log.error("Unsupported mcp server type: {}, mcp server name: {}", mcpServer.getString("type"), mcpServerName);
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("", e);
        }
        if (!mcpConnections.isEmpty()) {
            // 初始化 mcp client
            int mcpServerCount = mcpConnections.size();
            int mcpTollCount = 0;
            for (Map.Entry<String, McpSseClientProperties.SseParameters> mcpEntry : mcpConnections.entrySet()) {
                McpSseClientProperties.SseParameters sseParameters = mcpEntry.getValue();
                HttpClientSseClientTransport httpClientSseClientTransport = HttpClientSseClientTransport
                        .builder(sseParameters.url())
                        .sseEndpoint(sseParameters.sseEndpoint())
                        .build();
                McpSyncClient macSyncClient = McpClient.sync(httpClientSseClientTransport)
                        .requestTimeout(REQUEST_TIMEOUT)
                        .capabilities(McpSchema.ClientCapabilities.builder()
                                .roots(true)
                                .sampling()
                                .build())
                        .build();
                mcpClients.put(mcpEntry.getKey(), macSyncClient);
                macSyncClient.initialize();
                // 将每个 mcp server 的 tool 添加到 toolName2mcpServerName 中
                McpSchema.ListToolsResult mcpTools = macSyncClient.listTools();
                for (McpSchema.Tool mcpTool : mcpTools.tools()) {
                    McpToolInfImpl toolInf = new McpToolInfImpl(macSyncClient, mcpTool);
                    if (functionCallingService.getTools().containsKey(mcpTool.name())) {
                        throw new RuntimeException(
                                String.format("Duplicate mcp tool name: %s with function calling tool ,from mcp server: %s",
                                        mcpTool.name(), mcpEntry.getKey()
                                )
                        );
                    } else {
                        functionCallingService.getTools().put(mcpTool.name(), toolInf);
                        mcpTollCount++;
                    }
                }
            }
            log.info("Loaded {} mcp tools form {} mcp servers", mcpTollCount, mcpServerCount);
        }
    }
}
