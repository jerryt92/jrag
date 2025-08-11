//package io.github.jerryt92.jrag.service.llm.mcp;
//
//import io.modelcontextprotocol.client.McpClient;
//import io.modelcontextprotocol.client.McpSyncClient;
//import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
//import io.modelcontextprotocol.spec.McpSchema;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.ai.model.ModelOptionsUtils;
//import org.springframework.stereotype.Service;
//
//import javax.annotation.PostConstruct;
//import java.time.Duration;
//import java.util.Map;
//
//@Slf4j
//@Service
//public class McpService {
//    @PostConstruct
//    public void initSync() {
//        if (false) {
//            HttpClientSseClientTransport httpClientSseClientTransport = HttpClientSseClientTransport.
//                    builder("https://mcp.api-inference.modelscope.net")
//                    .sseEndpoint("/xxx/sse")
//                    .build();
//            McpSyncClient client = McpClient.sync(httpClientSseClientTransport)
//                    .requestTimeout(Duration.ofSeconds(20L))
//                    .capabilities(McpSchema.ClientCapabilities.builder()
//                            .roots(true)
//                            .sampling()
//                            .build())
//                    .build();
//            client.initialize();
//            // List available tools
//            McpSchema.ListToolsResult tools = client.listTools();
//            log.info("Available tools: {}", ModelOptionsUtils.toJsonStringPrettyPrinter(tools.tools()));
//            // Call a tool
//            McpSchema.CallToolResult result = client.callTool(
//                    new McpSchema.CallToolRequest("get-stations-code-in-city",
//                            Map.of(
//                                    "city", "深圳"
//                            ))
//            );
//            log.info("MCP tool result: {}", ModelOptionsUtils.toJsonStringPrettyPrinter(result));
//        }
//    }
//}
