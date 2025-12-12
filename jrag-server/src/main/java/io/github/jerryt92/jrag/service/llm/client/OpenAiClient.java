package io.github.jerryt92.jrag.service.llm.client;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.jerryt92.jrag.config.LlmProperties;
import io.github.jerryt92.jrag.model.ChatCallback;
import io.github.jerryt92.jrag.model.ChatModel;
import io.github.jerryt92.jrag.model.FunctionCallingModel;
import io.github.jerryt92.jrag.model.openai.OpenAIModel;
import io.github.jerryt92.jrag.utils.SmartJsonFixer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class OpenAiClient extends LlmClient {

    private final WebClient webClient;

    public OpenAiClient(LlmProperties llmProperties) {
        super(llmProperties);
        this.webClient = WebClient.builder().clientConnector(
                        new ReactorClientHttpConnector(
                                HttpClient.create().protocol(HttpProtocol.HTTP11)
                        )
                )
                .baseUrl(llmProperties.openAiBaseUrl)
                .defaultHeader("Authorization", "Bearer " + llmProperties.openAiKey)
                .build();
    }

    private final Map<String, ChatModel.ToolCallFunction> functionCallingInfoMap = new HashMap<>();

    @Override
    public Disposable chat(ChatModel.ChatRequest chatRequest, ChatCallback<ChatModel.ChatResponse> chatCallback) {
        List<OpenAIModel.ChatCompletionMessage> messagesContext = new ArrayList<>();
        for (ChatModel.Message chatMessage : chatRequest.getMessages()) {
            OpenAIModel.ChatCompletionMessage openAiMessage = new OpenAIModel.ChatCompletionMessage()
                    .setRawContent(chatMessage.getContent());
            switch (chatMessage.getRole()) {
                case SYSTEM:
                    openAiMessage.setRole(OpenAIModel.Role.SYSTEM);
                    break;
                case USER:
                    openAiMessage.setRole(OpenAIModel.Role.USER);
                    break;
                case ASSISTANT:
                    openAiMessage.setRole(OpenAIModel.Role.ASSISTANT);
                    if (!CollectionUtils.isEmpty(chatMessage.getToolCalls())) {
                        // 工具调用信息
                        openAiMessage.setToolCalls(new ArrayList<>());
                        for (int i = 0; i < chatMessage.getToolCalls().size(); i++) {
                            ChatModel.ToolCall toolCall = chatMessage.getToolCalls().get(i);
                            OpenAIModel.ToolCall openAiToolCall = new OpenAIModel.ToolCall();
                            openAiToolCall.setIndex(toolCall.getFunction().getIndex());
                            openAiToolCall.setId(toolCall.getFunction().getId());
                            if (ChatModel.Type.FUNCTION.equals(toolCall.getFunction().getType())) {
                                openAiToolCall.setType("function");
                            }
                            OpenAIModel.ChatCompletionFunction openAiFunction = new OpenAIModel.ChatCompletionFunction()
                                    .setName(toolCall.getFunction().getName())
                                    .setArguments(toolCall.getFunction().getArguments().toString());
                            openAiToolCall.setFunction(openAiFunction);
                            openAiMessage.getToolCalls().add(openAiToolCall);
                        }
                    }
                    break;
                case TOOL:
                    openAiMessage.setRole(OpenAIModel.Role.TOOL);
                    break;
            }
            messagesContext.add(openAiMessage);
        }
        OpenAIModel.ChatCompletionRequest request = new OpenAIModel.ChatCompletionRequest()
                .setMessages(messagesContext)
                .setModel(llmProperties.openAiModelName)
                .setStream(true)
                .setTemperature(llmProperties.temperature);
        if (!CollectionUtils.isEmpty(chatRequest.getTools())) {
            // 存在工具则传入
            List<OpenAIModel.FunctionTool> openAiTools = new ArrayList<>();
            for (FunctionCallingModel.Tool tool : chatRequest.getTools()) {
                OpenAIModel.FunctionTool functionTool = new OpenAIModel.FunctionTool();
                functionTool.setType(OpenAIModel.FunctionTool.Type.FUNCTION);
                OpenAIModel.FunctionTool.Function function = new OpenAIModel.FunctionTool.Function(
                        tool.getDescription(),
                        tool.getName(),
                        FunctionCallingModel.generateToolParameters(tool.getParameters()),
                        true
                );
                functionTool.setFunction(function);
                openAiTools.add(functionTool);
            }
            request.setTools(openAiTools);
        }
        // Debug
//        log.info(ModelOptionsUtils.toJsonString(request));
        log.info("context length:{}", ModelOptionsUtils.toJsonString(request).length());
        Flux<String> eventStream = webClient.post()
                .uri(llmProperties.completionsPath)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .accept(MediaType.ALL)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnError(t -> {
                    functionCallingInfoMap.remove(chatCallback.subscriptionId);
                    chatCallback.errorCall.accept(t);
                }).doOnComplete(() -> {
                    if (functionCallingInfoMap.remove(chatCallback.subscriptionId) == null) {
                        chatCallback.completeCall.run();
                    }
                });
        return eventStream.subscribe(chatCompletionChunk -> this.consumeResponse(chatCompletionChunk, chatCallback));
    }

    private void consumeResponse(String response, ChatCallback<ChatModel.ChatResponse> chatCallback) {
        try {
            ChatModel.ToolCallFunction toolCallFunction = functionCallingInfoMap.get(chatCallback.subscriptionId);
            if (response.trim().equals("[DONE]")) {
                if (toolCallFunction != null) {
                    String rawArgs = toolCallFunction.getArgumentsStream().toString();

                    // ============================================
                    // 核心修改：使用 SmartJsonFixer 进行终极修复
                    // ============================================
                    String finalArgumentsString = SmartJsonFixer.fix(rawArgs);

                    try {
                        // 此时 finalArgumentsString 已经被 SmartJsonFixer 修复为标准的 JSON 数组格式
                        // 无论是 {key=value}, {"key": 1+1}, 还是多重引号，都已被处理。
                        List<JSONObject> argumentJsons = JSONArray.parseArray(finalArgumentsString, JSONObject.class);
                        List<Map<String, Object>> argumentMaps = new ArrayList<>(argumentJsons);
                        toolCallFunction.setArguments(argumentMaps);

                        // ... 构建 ChatResponse 并回调 (与原逻辑一致) ...
                        ChatModel.ToolCall toolCall = new ChatModel.ToolCall().setFunction(toolCallFunction);
                        ChatModel.ChatResponse chatResponse = new ChatModel.ChatResponse()
                                .setMessage(new ChatModel.Message()
                                        .setRole(ChatModel.Role.ASSISTANT)
                                        .setContent("")
                                        .setToolCalls(List.of(toolCall)))
                                .setDone(true);
                        chatCallback.responseCall.accept(chatResponse);
                    } catch (Exception e) {
                        log.error("Final JSON parsing failed even after SmartFix. Raw: {}, Fixed: {}", rawArgs, finalArgumentsString, e);
                        chatCallback.errorCall.accept(e);
                    }
                } else {
                    // 非 Function Calling，内容已输出完毕
                    ChatModel.ChatResponse chatResponse = new ChatModel.ChatResponse()
                            .setMessage(
                                    new ChatModel.Message()
                                            .setRole(ChatModel.Role.ASSISTANT)
                                            .setContent("")
                            )
                            .setDone(true);
                    chatCallback.responseCall.accept(chatResponse);
                }
                chatCallback.completeCall.run();
            } else {
                // ============== 处理非 [DONE] 的流式数据块 ==============
                OpenAIModel.ChatCompletionChunk chatCompletionChunk = ModelOptionsUtils.jsonToObject(response, OpenAIModel.ChatCompletionChunk.class);
                List<ChatModel.ToolCall> toolCalls = null;
                StringBuilder content = new StringBuilder();
                OpenAIModel.ChatCompletionFinishReason finishReason = null;
                if (chatCompletionChunk.getChoices() != null) {
                    for (OpenAIModel.ChatCompletionChunk.ChunkChoice chunkChoice : chatCompletionChunk.getChoices()) {

                        if (!CollectionUtils.isEmpty(chunkChoice.getDelta().getToolCalls())) {
                            // 模型有 function calling 请求
                            List<OpenAIModel.ToolCall> openAiToolCalls = chunkChoice.getDelta().getToolCalls();
                            // 初始化或获取 toolCallFunction
                            if (toolCallFunction == null) {
                                toolCallFunction = new ChatModel.ToolCallFunction();
                                toolCallFunction.setArgumentsStream(new StringBuilder());
                                functionCallingInfoMap.put(chatCallback.subscriptionId, toolCallFunction);
                            }
                            for (OpenAIModel.ToolCall openAiToolCall : openAiToolCalls) {
                                if (StringUtils.isNotBlank(openAiToolCall.getFunction().getName())) {
                                    toolCallFunction.setName(openAiToolCall.getFunction().getName());
                                }
                                if (openAiToolCall.getFunction().getArguments() != null) {
                                    // 仅仅进行字符串拼接，不在此处解析
                                    toolCallFunction.getArgumentsStream().append(openAiToolCall.getFunction().getArguments());
                                }
                                if (openAiToolCall.getIndex() != null) {
                                    toolCallFunction.setIndex(openAiToolCall.getIndex());
                                }
                                if (openAiToolCall.getId() != null) {
                                    toolCallFunction.setId(openAiToolCall.getId());
                                }
                            }
                        } else {
                            // 模型返回文本内容
                            if (chunkChoice.getDelta().getRawContent() != null) {
                                content.append(chunkChoice.getDelta().getRawContent());
                            }
                            if (chunkChoice.getFinishReason() != null) {
                                finishReason = chunkChoice.getFinishReason();
                            }
                            // 发送当前内容的流式响应
                            ChatModel.ChatResponse chatResponse = new ChatModel.ChatResponse()
                                    .setMessage(
                                            new ChatModel.Message()
                                                    .setRole(ChatModel.Role.ASSISTANT)
                                                    .setContent(content.toString())
                                                    .setToolCalls(toolCalls)
                                    )
                                    .setDone(false)
                                    .setDoneReason(finishReason == null ? null : finishReason.toString());
                            chatCallback.responseCall.accept(chatResponse);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            log.error("Error processing chat completion chunk.", t);
            chatCallback.errorCall.accept(t);
        }
    }
}
