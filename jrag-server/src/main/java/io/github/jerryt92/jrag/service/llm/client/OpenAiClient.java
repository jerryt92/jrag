package io.github.jerryt92.jrag.service.llm.client;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.jerryt92.jrag.config.LlmProperties;
import io.github.jerryt92.jrag.model.ChatModel;
import io.github.jerryt92.jrag.model.FunctionCallingModel;
import io.netty.channel.ChannelOption;
import io.netty.channel.WriteBufferWaterMark;
import org.springframework.ai.model.ModelOptionsUtils;
import io.github.jerryt92.jrag.model.openai.OpenAIModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
public class OpenAiClient extends LlmClient {

    private final WebClient webClient;

    public OpenAiClient(LlmProperties llmProperties) {
        super(llmProperties);
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().protocol(HttpProtocol.HTTP11)))
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(1024 * 1024, 1024 * 1024 * 50))))
                .baseUrl(llmProperties.openAiBaseUrl)
                .defaultHeader("Authorization", "Bearer " + llmProperties.openAiKey)
                .build();
    }

    private final Map<SseEmitter, ChatModel.ToolCallFunction> functionCallingInfoMap = new HashMap<>();

    @Override
    public Disposable chat(ChatModel.ChatRequest chatRequest, SseEmitter sseEmitter, Consumer<ChatModel.ChatResponse> onResponse, Consumer<? super Throwable> onError, Runnable onComplete) {
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
                                    .setArguments(toolCall.getFunction().getArgumentsStream().toString());
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
        Flux<String> eventStream = webClient.post()
                .uri(llmProperties.completionsPath)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .accept(MediaType.ALL)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnError(t -> {
                    functionCallingInfoMap.remove(sseEmitter);
                    if (onError != null) {
                        onError.accept(t);
                    }
                }).doOnComplete(() -> {
                    if (functionCallingInfoMap.remove(sseEmitter) == null) {
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    }
                });
        return eventStream.subscribe(chatCompletionChunk -> this.consumeResponse(chatCompletionChunk, onResponse, sseEmitter, onError));
    }

    private void consumeResponse(String response, Consumer<ChatModel.ChatResponse> onResponse, SseEmitter sseEmitter, Consumer<? super Throwable> onError) {
        try {
            ChatModel.ToolCallFunction toolCallFunction = functionCallingInfoMap.get(sseEmitter);
            if (response.trim().equals("[DONE]")) {
                if (toolCallFunction != null) {
                    // Function calling输出完成
                    String argumentsString = "[" + toolCallFunction.getArgumentsStream().toString().replace("}{", "},{") + "]";
                    List<JSONObject> argumentJsons = JSONArray.parseArray(argumentsString, JSONObject.class);
                    List<Map<String, Object>> argumentMaps = new ArrayList<>(argumentJsons);
                    toolCallFunction.setArguments(argumentMaps);
                    ChatModel.ToolCall toolCall = new ChatModel.ToolCall()
                            .setFunction(toolCallFunction);
                    ChatModel.ChatResponse chatResponse = new ChatModel.ChatResponse()
                            .setMessage(
                                    new ChatModel.Message()
                                            .setRole(ChatModel.Role.ASSISTANT)
                                            .setContent("")
                                            .setToolCalls(List.of(toolCall))
                            )
                            .setDone(true);
                    onResponse.accept(chatResponse);
                } else {
                    ChatModel.ChatResponse chatResponse = new ChatModel.ChatResponse()
                            .setMessage(
                                    new ChatModel.Message()
                                            .setRole(ChatModel.Role.ASSISTANT)
                                            .setContent("")
                            )
                            .setDone(true);
                    onResponse.accept(chatResponse);
                }
            } else {
                OpenAIModel.ChatCompletionChunk chatCompletionChunk = ModelOptionsUtils.jsonToObject(response, OpenAIModel.ChatCompletionChunk.class);
                List<ChatModel.ToolCall> toolCalls = null;
                StringBuilder content = new StringBuilder();
                OpenAIModel.ChatCompletionFinishReason finishReason = null;
                if (chatCompletionChunk.getChoices() != null) {
                    for (OpenAIModel.ChatCompletionChunk.ChunkChoice chunkChoice : chatCompletionChunk.getChoices()) {
                        if (!CollectionUtils.isEmpty(chunkChoice.getDelta().getToolCalls())) {
                            // 模型有function calling请求
                            List<OpenAIModel.ToolCall> openAiToolCalls = chunkChoice.getDelta().getToolCalls();
                            if (toolCallFunction == null) {
                                toolCallFunction = new ChatModel.ToolCallFunction();
                                toolCallFunction.setArgumentsStream(new StringBuilder());
                                functionCallingInfoMap.put(sseEmitter, toolCallFunction);
                            }
                            for (OpenAIModel.ToolCall openAiToolCall : openAiToolCalls) {
                                if (openAiToolCall.getFunction().getName() != null) {
                                    toolCallFunction.setName(openAiToolCall.getFunction().getName());
                                }
                                if (openAiToolCall.getFunction().getArguments() != null) {
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
                            if (chunkChoice.getDelta().getRawContent() != null) {
                                content.append(chunkChoice.getDelta().getRawContent());
                            }
                            if (chunkChoice.getFinishReason() != null) {
                                finishReason = chunkChoice.getFinishReason();
                            }
                            ChatModel.ChatResponse chatResponse = new ChatModel.ChatResponse()
                                    .setMessage(
                                            new ChatModel.Message()
                                                    .setRole(ChatModel.Role.ASSISTANT)
                                                    .setContent(content.toString())
                                                    .setToolCalls(toolCalls)
                                    )
                                    .setDone(false)
                                    .setDoneReason(finishReason == null ? null : finishReason.toString());
                            onResponse.accept(chatResponse);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            log.error("", t);
            onError.accept(t);
        }
    }
}
