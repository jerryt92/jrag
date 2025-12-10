package io.github.jerryt92.jrag.service.llm.client;

import io.github.jerryt92.jrag.config.LlmProperties;
import io.github.jerryt92.jrag.model.ChatCallback;
import io.github.jerryt92.jrag.model.ChatModel;
import io.github.jerryt92.jrag.model.FunctionCallingModel;
import io.github.jerryt92.jrag.model.ollama.OllamaModel;
import io.github.jerryt92.jrag.model.ollama.OllamaOptions;
import lombok.extern.slf4j.Slf4j;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class OllamaClient extends LlmClient {

    private final WebClient webClient;

    public OllamaClient(LlmProperties llmProperties) {
        super(llmProperties);
        this.webClient = WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.create().protocol(HttpProtocol.HTTP11))).build();
    }

    private final Set<String> functionCallingSet = new HashSet<>();

    @Override
    public Disposable chat(ChatModel.ChatRequest chatRequest, ChatCallback<ChatModel.ChatResponse> chatCallback) {
        List<OllamaModel.Message> messagesContext = new ArrayList<>();
        for (ChatModel.Message chatMessage : chatRequest.getMessages()) {
            OllamaModel.Message ollamaMessage = new OllamaModel.Message()
                    .setContent(chatMessage.getContent());
            switch (chatMessage.getRole()) {
                case SYSTEM:
                    ollamaMessage.setRole(OllamaModel.Role.SYSTEM);
                    break;
                case USER:
                    ollamaMessage.setRole(OllamaModel.Role.USER);
                    break;
                case ASSISTANT:
                    ollamaMessage.setRole(OllamaModel.Role.ASSISTANT);
                    if (!CollectionUtils.isEmpty(chatMessage.getToolCalls())) {
                        // 工具调用信息
                        ollamaMessage.setToolCalls(new ArrayList<>());
                        for (int i = 0; i < chatMessage.getToolCalls().size(); i++) {
                            ChatModel.ToolCall toolCall = chatMessage.getToolCalls().get(i);
                            OllamaModel.ToolCall ollamaToolCall = new OllamaModel.ToolCall();
                            ollamaToolCall.setFunction(new OllamaModel.ToolCallFunction()
                                    .setName(toolCall.getFunction().getName())
                                    .setArguments(toolCall.getFunction().getArguments().getFirst()));
                            ollamaMessage.getToolCalls().add(ollamaToolCall);
                        }
                    }
                    break;
                case TOOL:
                    ollamaMessage.setRole(OllamaModel.Role.TOOL);
                    break;
            }
            messagesContext.add(ollamaMessage);
        }
        Map<String, Object> options = new HashMap<>(OllamaOptions.builder()
                .numCtx(llmProperties.ollamaContextLength)
                .temperature(llmProperties.temperature)
                .build().toMap());
        if (!CollectionUtils.isEmpty(chatRequest.getOptions())) {
            options.putAll(chatRequest.getOptions());
        }
        OllamaModel.ChatRequest request = new OllamaModel.ChatRequest()
                .setModel(llmProperties.ollamaModelName)
                .setKeepAlive(String.valueOf(llmProperties.ollamaKeepAliveSeconds))
                .setMessages(messagesContext)
                .setStream(true)
                .setOptions(options);
        if (!CollectionUtils.isEmpty(chatRequest.getTools())) {
            List<OllamaModel.Tool> ollamaTools = new ArrayList<>();
            for (FunctionCallingModel.Tool tool : chatRequest.getTools()) {
                OllamaModel.Function function = new OllamaModel.Function()
                        .setName(tool.getName())
                        .setDescription(tool.getDescription());
                function.setParameters(FunctionCallingModel.generateToolParameters(tool.getParameters()));
                ollamaTools.add(new OllamaModel.Tool()
                        .setFunction(function)
                        .setType(OllamaModel.Type.FUNCTION)
                );
            }
            request.setTools(ollamaTools);
        }
        log.info("context length:{}", ModelOptionsUtils.toJsonString(request).length());
        Flux<OllamaModel.ChatResponse> eventStream = webClient.post()
                .uri(llmProperties.ollamaBaseUrl + "/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .accept(MediaType.ALL)
                .retrieve()
                .bodyToFlux(OllamaModel.ChatResponse.class)
                .doOnError(t -> {
                    functionCallingSet.remove(chatCallback.subscriptionId);
                    chatCallback.errorCall.accept(t);
                }).doOnComplete(() -> {
                    if (!functionCallingSet.remove(chatCallback.subscriptionId)) {
                        chatCallback.completeCall.run();
                    }
                });
        return eventStream.subscribe(ollamaResponse -> this.consumeResponse(ollamaResponse, chatCallback));
    }

    private void consumeResponse(OllamaModel.ChatResponse ollamaResponse, ChatCallback<ChatModel.ChatResponse> chatCallback) {
        List<ChatModel.ToolCall> toolCalls = null;
        if (ollamaResponse == null || (ollamaResponse.getDone() == null && ollamaResponse.getModel() == null)) {
            chatCallback.completeCall.run();
        } else {
            if (ollamaResponse.getMessage() != null && !CollectionUtils.isEmpty(ollamaResponse.getMessage().getToolCalls())) {
                // 模型有function calling请求
                functionCallingSet.add(chatCallback.subscriptionId);
                toolCalls = new ArrayList<>();
                for (OllamaModel.ToolCall ollamaToolCall : ollamaResponse.getMessage().getToolCalls()) {
                    if (ollamaToolCall.getFunction() != null) {
                        ChatModel.ToolCall toolCall = new ChatModel.ToolCall()
                                .setFunction(
                                        new ChatModel.ToolCallFunction()
                                                .setName(ollamaToolCall.getFunction().getName())
                                                .setArguments(List.of(ollamaToolCall.getFunction().getArguments()))
                                );
                        toolCalls.add(toolCall);
                    }
                }
            }
            if (Boolean.TRUE.equals(ollamaResponse.getDone()) && functionCallingSet.contains(chatCallback.subscriptionId)) {
                return;
            }
            ChatModel.ChatResponse chatResponse = new ChatModel.ChatResponse()
                    .setMessage(
                            new ChatModel.Message()
                                    .setRole(ChatModel.Role.ASSISTANT)
                                    .setContent(ollamaResponse.getMessage().getContent())
                                    .setToolCalls(toolCalls)
                    )
                    .setDone(ollamaResponse.getDone())
                    .setDoneReason(ollamaResponse.getDoneReason());
            chatCallback.responseCall.accept(chatResponse);
        }
    }
}
