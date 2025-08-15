package io.github.jerryt92.jrag.service.llm.mcp;

import com.alibaba.fastjson2.JSONObject;
import io.github.jerryt92.jrag.model.FunctionCallingModel;
import io.github.jerryt92.jrag.service.llm.tools.ToolInterface;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.ModelOptionsUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class McpToolInfImpl extends ToolInterface {
    @Getter
    @Setter
    private McpSyncClient mcpSyncClient;
    private final McpSchema.Tool mcpTool;

    public McpToolInfImpl(McpSyncClient mcpSyncClient, McpSchema.Tool mcpTool) {
        toolInfo.setName(mcpTool.name())
                .setDescription(mcpTool.description());
        List<FunctionCallingModel.Tool.Parameter> parameters = new ArrayList<>();
        Set<String> requiredSet = new HashSet<>();
        if (mcpTool.inputSchema().required() != null) {
            requiredSet.addAll(mcpTool.inputSchema().required());
        }
        for (Map.Entry<String, Object> entry : mcpTool.inputSchema().properties().entrySet()) {
            FunctionCallingModel.Tool.Parameter parameter = new FunctionCallingModel.Tool.Parameter()
                    .setName(entry.getKey());
            JSONObject mcpToolParameterValue = JSONObject.parseObject(ModelOptionsUtils.toJsonString(entry.getValue()));
            parameter.setType(mcpToolParameterValue.getString("type"))
                    .setDescription(mcpToolParameterValue.getString("description"))
                    .setRequired(requiredSet.contains(entry.getKey()));
            parameters.add(parameter);
        }
        toolInfo.setParameters(parameters);
        this.mcpSyncClient = mcpSyncClient;
        this.mcpTool = mcpTool;
    }

    @Override
    public List<String> apply(List<Map<String, Object>> requests) {
        try {
            List<String> resultList = new ArrayList<>();
            for (Map<String, Object> request : requests) {
                McpSchema.CallToolRequest callToolRequest = new McpSchema.CallToolRequest(mcpTool.name(), request);
                McpSchema.CallToolResult result = mcpSyncClient.callTool(callToolRequest);
                resultList.add(ModelOptionsUtils.toJsonString(result));
            }
            return resultList;
        } catch (Exception e) {
            log.error("MCP tool error: {}", e.getMessage());
            return List.of("Exception happened");
        }
    }
}
