package io.github.jerryt92.jrag.service.llm.mcp;

import com.alibaba.fastjson2.JSONObject;
import io.github.jerryt92.jrag.service.llm.tools.FunctionCallingService;
import io.github.jerryt92.jrag.service.llm.tools.ToolInterface;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.client.autoconfigure.properties.McpSseClientProperties;
import org.springframework.ai.mcp.client.autoconfigure.properties.McpStdioClientProperties;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class McpService {
    public Map<String, McpSseClientProperties.SseParameters> mcpSseServerParameters = new HashMap<>();
    public Map<String, McpStdioClientProperties.Parameters> mcpStdioServerParameters = new HashMap<>();
    public Map<String, McpSyncClient> mcpName2Client = new HashMap<>();
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
        Set<String> mcpServerNames = null;
        // 读取 mcp.json
        try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("mcp.json")) {
            if (inputStream != null) {
                String jsonText = new String(inputStream.readAllBytes());
                JSONObject mcpJson = JSONObject.parseObject(jsonText);
                if (mcpJson != null) {
                    JSONObject mcpServers = mcpJson.getJSONObject("mcpServers");
                    if (mcpServers != null) {
                        mcpServerNames = mcpServers.keySet();
                        for (String mcpServerName : mcpServerNames) {
                            JSONObject mcpServer = mcpServers.getJSONObject(mcpServerName);
                            if (mcpServer != null) {
                                if ("sse".equals(mcpServer.getString("type"))) {
                                    try {
                                        URI uri = new URI(mcpServer.getString("url"));
                                        String baseUrl = uri.getScheme() + "://" + uri.getHost();
                                        mcpSseServerParameters.put(
                                                mcpServerName,
                                                new McpSseClientProperties.SseParameters(baseUrl, uri.getPath())
                                        );
                                    } catch (URISyntaxException e) {
                                        log.error("", e);
                                    }
                                } else if ("streamable_http".equals(mcpServer.getString("type"))) {
                                    log.error("Streamable http is not supported yet, waiting for Spring AI release, mcp server name: {}", mcpServerName);
                                } else {
                                    String command = mcpServer.getString("command");
                                    List<String> args = mcpServer.getList("args", String.class);
                                    Map<String, String> env = null;
                                    JSONObject envJsonObject = mcpServer.getJSONObject("env");
                                    if (envJsonObject != null && !envJsonObject.isEmpty()) {
                                        env = new HashMap<>();
                                        for (Map.Entry<String, Object> stringObjectEntry : envJsonObject.entrySet()) {
                                            env.put(stringObjectEntry.getKey(), String.valueOf(stringObjectEntry.getValue()));
                                        }
                                    }
                                    mcpStdioServerParameters.put(
                                            mcpServerName,
                                            new McpStdioClientProperties.Parameters(command, args, env)
                                    );
                                }
                            }
                        }
                        mcpServerNames = new HashSet<>();
                        mcpServerNames.addAll(mcpSseServerParameters.keySet());
                        mcpServerNames.addAll(mcpStdioServerParameters.keySet());
                    }
                }
            }
        } catch (IOException e) {
            log.error("", e);
        }
        int mcpTollCount = 0;
        int mcpServerCount = 0;
        if (!CollectionUtils.isEmpty(mcpServerNames)) {
            mcpServerCount += mcpServerNames.size();
            for (String mcpServerName : mcpServerNames) {
                mcpTollCount += registerMcpTools(mcpServerName).size();
            }
        }
        log.info("Loaded {} mcp tools form {} mcp servers", mcpTollCount, mcpServerCount);
    }

    public Set<String> registerMcpTools(String mcpServerName) {
        try {
            Set<String> removedTools = mcpClient2tools.remove(mcpServerName);
            if (!CollectionUtils.isEmpty(removedTools)) {
                for (String removedTool : removedTools) {
                    functionCallingService.getTools().remove(removedTool);
                }
            }
            McpClientTransport mcpClientTransport = null;
            McpStdioClientProperties.Parameters parameters = mcpStdioServerParameters.get(mcpServerName);
            McpSseClientProperties.SseParameters sseParameters = mcpSseServerParameters.get(mcpServerName);
            if (parameters != null) {
                mcpClientTransport = new StdioClientTransport(parameters.toServerParameters());
            } else if (sseParameters != null) {
                mcpClientTransport = HttpClientSseClientTransport
                        .builder(sseParameters.url())
                        .sseEndpoint(sseParameters.sseEndpoint())
                        .build();
            }
            McpSyncClient mcpSyncClient = McpClient.sync(mcpClientTransport)
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
                    if (tool instanceof McpToolInfImpl existedMcpTool) {
                        // 已存在的工具是MCP工具
                        if (!existedMcpTool.getMcpSyncClient().equals(mcpSyncClient)) {
                            throw new RuntimeException(
                                    String.format("Duplicate mcp tool name: %s from mcp server: %s with another mcp server: %s",
                                            mcpTool.name(), mcpSyncClient.getServerInfo().name(), existedMcpTool.getMcpSyncClient().getServerInfo().name()
                                    )
                            );
                        }
                    } else {
                        throw new RuntimeException(
                                String.format("Duplicate mcp tool name: %s with function calling tool ,from mcp server: %s",
                                        mcpTool.name(), mcpSyncClient.getServerInfo().name()
                                )
                        );
                    }
                } else {
                    toolInf = new McpToolInfImpl(mcpSyncClient, mcpTool);
                    functionCallingService.getTools().put(mcpTool.name(), toolInf);
                }
                tools.add(mcpTool.name());
            }
            mcpName2Client.put(mcpServerName, mcpSyncClient);
            mcpClient2tools.put(mcpServerName, tools);
            return tools;
        } catch (Exception e) {
            log.error("", e);
            return Collections.emptySet();
        }
    }
}
