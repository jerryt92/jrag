package io.github.jerryt92.jrag.service.llm.mcp;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jerryt92.jrag.model.McpStatusItem;
import io.github.jerryt92.jrag.model.McpToolItem;
import io.github.jerryt92.jrag.service.llm.tools.FunctionCallingService;
import io.github.jerryt92.jrag.service.llm.tools.ToolInterface;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpSseClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStdioClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class McpService {
    public Map<String, McpSseClientProperties.SseParameters> mcpSseServerParameters = new ConcurrentHashMap<>();
    public Map<String, McpStreamableHttpClientProperties.ConnectionParameters> mcpStreamableServerParameters = new ConcurrentHashMap<>();
    public Map<String, McpStdioClientProperties.Parameters> mcpStdioServerParameters = new ConcurrentHashMap<>();
    public Map<String, McpSyncClient> mcpName2Client = new ConcurrentHashMap<>();
    public Map<String, Set<String>> mcpClient2tools = new ConcurrentHashMap<>();
    public Map<String, Map<String, String>> mcpClient2toolDescriptions = new ConcurrentHashMap<>();
    public Map<String, Map<String, String>> mcpHeaders = new ConcurrentHashMap<>();
    private final FunctionCallingService functionCallingService;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15L);
    private static final String DEFAULT_MCP_JSON = "{\"mcpServers\":{}}";
    private static final String MCP_DEFAULT_SUBDIR = "jrag/mcp";
    private static final String MCP_CONFIG_FILE = "mcp.json";

    public McpService(FunctionCallingService functionCallingService) {
        this.functionCallingService = functionCallingService;
    }

    @PostConstruct
    public void init() {
        Thread.startVirtualThread(this::reloadFromFile);
    }

    public void reloadFromFile() {
        String mcpJsonText = resolveMcpJsonText();
        loadMcpServers(mcpJsonText);
    }

    public JSONObject getMcpConfig() {
        String jsonText = resolveMcpJsonText();
        JSONObject config = JSONObject.parseObject(jsonText);
        return config == null ? new JSONObject() : config;
    }

    public void updateMcpConfig(JSONObject config) {
        if (config == null) {
            throw new IllegalArgumentException("MCP config is empty");
        }
        String jsonText = JSONObject.toJSONString(config);
        persistMcpJson(jsonText);
        Thread.startVirtualThread(() -> loadMcpServers(jsonText));
    }

    public List<McpStatusItem> getMcpServerStatus() {
        Set<String> mcpServerNames = new HashSet<>();
        mcpServerNames.addAll(mcpSseServerParameters.keySet());
        mcpServerNames.addAll(mcpStreamableServerParameters.keySet());
        mcpServerNames.addAll(mcpStdioServerParameters.keySet());
        List<McpStatusItem> result = new ArrayList<>();
        for (String serverName : mcpServerNames) {
            McpStatusItem item = new McpStatusItem();
            item.setName(serverName);
            String type = "stdio";
            String endpoint = "";
            if (mcpSseServerParameters.containsKey(serverName)) {
                type = "sse";
                McpSseClientProperties.SseParameters params = mcpSseServerParameters.get(serverName);
                endpoint = params.url() + params.sseEndpoint();
            } else if (mcpStreamableServerParameters.containsKey(serverName)) {
                type = "streamable_http";
                McpStreamableHttpClientProperties.ConnectionParameters params = mcpStreamableServerParameters.get(serverName);
                endpoint = params.url() + params.endpoint();
            } else if (mcpStdioServerParameters.containsKey(serverName)) {
                McpStdioClientProperties.Parameters params = mcpStdioServerParameters.get(serverName);
                List<String> args = params.args() == null ? Collections.emptyList() : params.args();
                endpoint = params.command() + (args.isEmpty() ? "" : " " + String.join(" ", args));
            }
            item.setType(type);
            item.setEndpoint(endpoint.trim());
            Map<String, String> toolDesc = mcpClient2toolDescriptions.getOrDefault(serverName, Collections.emptyMap());
            List<McpToolItem> tools = new ArrayList<>();
            for (Map.Entry<String, String> entry : toolDesc.entrySet()) {
                McpToolItem toolItem = new McpToolItem();
                toolItem.setName(entry.getKey());
                toolItem.setDescription(entry.getValue());
                tools.add(toolItem);
            }
            item.setTools(tools);
            McpSyncClient client = mcpName2Client.get(serverName);
            boolean online = client != null;
            if (online && !"stdio".equals(type)) {
                try {
                    client.ping();
                } catch (Exception e) {
                    online = false;
                }
            }
            item.setStatus(online ? "online" : "offline");
            result.add(item);
        }
        return result;
    }

    private String resolveMcpJsonText() {
        Path configPath = resolveConfigFilePath();
        if (Files.exists(configPath)) {
            try {
                return Files.readString(configPath, StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                log.warn("Read MCP config failed.", e);
            }
        }
        persistMcpJson(DEFAULT_MCP_JSON);
        return DEFAULT_MCP_JSON;
    }

    private void persistMcpJson(String jsonText) {
        Path configPath = resolveConfigFilePath();
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, jsonText, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Write MCP config failed: {}", configPath, e);
        }
    }

    private Path resolveConfigFilePath() {
        String userHome = System.getProperty("user.home", ".");
        return Paths.get(userHome, MCP_DEFAULT_SUBDIR, MCP_CONFIG_FILE).toAbsolutePath();
    }

    private void loadMcpServers(String jsonText) {
        Set<String> mcpServerNames = null;
        clearMcpState();
        JSONObject mcpJson;
        try {
            mcpJson = JSONObject.parseObject(jsonText);
        } catch (Exception e) {
            log.warn("Invalid MCP config, skip loading.", e);
            return;
        }
        if (mcpJson != null) {
            JSONObject mcpServers = mcpJson.getJSONObject("mcpServers");
            if (mcpServers != null) {
                mcpServerNames = mcpServers.keySet();
                for (String mcpServerName : mcpServerNames) {
                    JSONObject mcpServer = mcpServers.getJSONObject(mcpServerName);
                    if (mcpServer != null) {
                        JSONObject headers = mcpServer.getJSONObject("headers");
                        if (headers != null && !headers.isEmpty()) {
                            mcpHeaders.put(mcpServerName, new HashMap<>());
                            for (Map.Entry<String, Object> stringObjectEntry : headers.entrySet()) {
                                mcpHeaders.get(mcpServerName).put(stringObjectEntry.getKey(), String.valueOf(stringObjectEntry.getValue()));
                            }
                        }
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
                            try {
                                URI uri = new URI(mcpServer.getString("url"));
                                String baseUrl = uri.getScheme() + "://" + uri.getHost();
                                mcpStreamableServerParameters.put(
                                        mcpServerName,
                                        new McpStreamableHttpClientProperties.ConnectionParameters(baseUrl, uri.getPath())
                                );
                            } catch (URISyntaxException e) {
                                log.error("", e);
                            }
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
                mcpServerNames.addAll(mcpStreamableServerParameters.keySet());
                mcpServerNames.addAll(mcpStdioServerParameters.keySet());
            }
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

    private void clearMcpState() {
        if (!CollectionUtils.isEmpty(mcpClient2tools)) {
            for (Set<String> removedTools : mcpClient2tools.values()) {
                if (!CollectionUtils.isEmpty(removedTools)) {
                    for (String removedTool : removedTools) {
                        functionCallingService.getTools().remove(removedTool);
                    }
                }
            }
        }
        mcpSseServerParameters.clear();
        mcpStreamableServerParameters.clear();
        mcpStdioServerParameters.clear();
        mcpName2Client.clear();
        mcpClient2tools.clear();
        mcpClient2toolDescriptions.clear();
        mcpHeaders.clear();
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
            McpStreamableHttpClientProperties.ConnectionParameters streamableHttpParameters = mcpStreamableServerParameters.get(mcpServerName);
            if (parameters != null) {
                mcpClientTransport = new StdioClientTransport(parameters.toServerParameters(), new JacksonMcpJsonMapper(new ObjectMapper()));
            } else if (sseParameters != null) {
                HttpRequest.Builder builder = HttpRequest.newBuilder();
                if (!CollectionUtils.isEmpty(mcpHeaders)) {
                    for (Map.Entry<String, String> entry : mcpHeaders.get(mcpServerName).entrySet()) {
                        builder.header(entry.getKey(), entry.getValue());
                    }
                }
                mcpClientTransport = HttpClientSseClientTransport
                        .builder(sseParameters.url())
                        .sseEndpoint(sseParameters.sseEndpoint())
                        .requestBuilder(builder)
                        .build();
            } else if (streamableHttpParameters != null) {
                HttpRequest.Builder builder = HttpRequest.newBuilder();
                if (!CollectionUtils.isEmpty(mcpHeaders)) {
                    for (Map.Entry<String, String> entry : mcpHeaders.get(mcpServerName).entrySet()) {
                        builder.header(entry.getKey(), entry.getValue());
                    }
                }
                mcpClientTransport = HttpClientStreamableHttpTransport
                        .builder(streamableHttpParameters.url())
                        .endpoint(streamableHttpParameters.endpoint())
                        .requestBuilder(builder)
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
            Map<String, String> toolDescriptions = new HashMap<>();
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
                toolDescriptions.put(mcpTool.name(), mcpTool.description());
            }
            mcpName2Client.put(mcpServerName, mcpSyncClient);
            mcpClient2tools.put(mcpServerName, tools);
            mcpClient2toolDescriptions.put(mcpServerName, toolDescriptions);
            return tools;
        } catch (Exception e) {
            log.error("", e);
            return Collections.emptySet();
        }
    }
}
