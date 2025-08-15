package io.github.jerryt92.jrag.service.llm.mcp;

import com.alibaba.fastjson2.JSONObject;
import io.github.jerryt92.jrag.service.llm.tools.FunctionCallingService;
import io.github.jerryt92.jrag.service.llm.tools.ToolInterface;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.client.autoconfigure.properties.McpSseClientProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class McpService {
    public Map<String, McpSseClientProperties.SseParameters> mcpServerParameters = new HashMap<>();
    public Map<McpSyncClient, String> mcpClient2name = new HashMap<>();
    public Map<String, Set<String>> mcpClient2tools = new HashMap<>();
    private final FunctionCallingService functionCallingService;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15L);

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
                                        mcpServerParameters.put(
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
        if (!mcpServerParameters.isEmpty()) {
            // 初始化 mcp client
            int mcpServerCount = mcpServerParameters.size();
            int mcpTollCount = 0;
            for (Map.Entry<String, McpSseClientProperties.SseParameters> mcpEntry : mcpServerParameters.entrySet()) {
                mcpTollCount += registerMcpTools(mcpEntry.getKey(), mcpEntry.getValue()).size();
            }
            log.info("Loaded {} mcp tools form {} mcp servers", mcpTollCount, mcpServerCount);
        }
    }

    public Set<String> registerMcpTools(String mcpServerName, McpSseClientProperties.SseParameters sseParameters) {
        try {
            Set<String> removedTools = mcpClient2tools.remove(mcpServerName);
            if (!CollectionUtils.isEmpty(removedTools)) {
                for (String removedTool : removedTools) {
                    functionCallingService.getTools().remove(removedTool);
                }
            }
            HttpClientSseClientTransport httpClientSseClientTransport = HttpClientSseClientTransport
                    .builder(sseParameters.url())
                    .sseEndpoint(sseParameters.sseEndpoint())
                    .build();
            McpSyncClient mcpSyncClient = McpClient.sync(httpClientSseClientTransport)
                    .requestTimeout(REQUEST_TIMEOUT)
                    .capabilities(McpSchema.ClientCapabilities.builder()
                            .roots(true)
                            .sampling()
                            .build())
                    .build();
            Set<String> tools = new HashSet<>();
            mcpSyncClient.initialize();
            // 将每个 mcp server 的 tool 添加到 toolName2mcpServerName 中
            McpSchema.ListToolsResult mcpTools = mcpSyncClient.listTools();
            for (McpSchema.Tool mcpTool : mcpTools.tools()) {
                McpToolInfImpl toolInf;
                ToolInterface tool = functionCallingService.getTools().get(mcpTool.name());
                if (tool != null) {
                    if (!(tool instanceof McpToolInfImpl existedMcpTool)) {
                        throw new RuntimeException(
                                String.format("Duplicate mcp tool name: %s with function calling tool ,from mcp server: %s",
                                        mcpTool.name(), mcpSyncClient.getServerInfo().name()
                                )
                        );
                    } else if (!mcpServerName.equals(mcpClient2name.get(existedMcpTool.getMcpSyncClient()))) {
                        throw new RuntimeException(
                                String.format("Duplicate mcp tool name: %s from mcp server: %s with another mcp server: %s",
                                        mcpTool.name(), mcpSyncClient.getServerInfo().name(), existedMcpTool.getMcpSyncClient().getServerInfo().name()
                                )
                        );
                    } else {
                        functionCallingService.getTools().remove(mcpTool.name());
                    }
                } else {
                    toolInf = new McpToolInfImpl(mcpSyncClient, mcpTool);
                    functionCallingService.getTools().put(mcpTool.name(), toolInf);
                }
                tools.add(mcpTool.name());
            }
            mcpClient2name.put(mcpSyncClient, mcpServerName);
            mcpClient2tools.put(mcpServerName, tools);
            return tools;
        } catch (Exception e) {
            log.error("", e);
            return Collections.emptySet();
        }
    }
}
