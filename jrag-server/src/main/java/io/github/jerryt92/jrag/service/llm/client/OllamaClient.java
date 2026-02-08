package io.github.jerryt92.jrag.service.llm.client;

import io.github.jerryt92.jrag.config.LlmProperties;
import io.github.jerryt92.jrag.model.ChatCallback;
import io.github.jerryt92.jrag.model.ChatModel;
import io.github.jerryt92.jrag.model.FunctionCallingModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.model.ModelOptionsUtils;
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

    private final OllamaApi ollamaApi;

    private static String secondsToDurationString(int seconds) {
        // Ollama expects a Go-style duration string with a unit, e.g. "3600s" or "5m".
        // Our config is in seconds, so normalize to "<n>s".
        return Math.max(seconds, 0) + "s";
    }

    public OllamaClient(LlmProperties llmProperties) {
        super(llmProperties);
        WebClient.Builder webClientBuilder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().protocol(HttpProtocol.HTTP11)));
        if (StringUtils.isNotBlank(llmProperties.ollamaKey)) {
            webClientBuilder.defaultHeader("Authorization", "Bearer " + llmProperties.ollamaKey);
        }
        this.ollamaApi = OllamaApi.builder()
                .baseUrl(llmProperties.ollamaBaseUrl)
                .webClientBuilder(webClientBuilder)
                .build();
    }

    private final Set<String> functionCallingSet = new HashSet<>();

    @Override
    public Disposable chat(ChatModel.ChatRequest chatRequest, ChatCallback<ChatModel.ChatResponse> chatCallback) {
        List<OllamaApi.Message> messagesContext = new ArrayList<>();
        for (ChatModel.Message chatMessage : chatRequest.getMessages()) {
            switch (chatMessage.getRole()) {
                case SYSTEM:
                    messagesContext.add(new OllamaApi.Message(OllamaApi.Message.Role.SYSTEM, chatMessage.getContent(), null, null, null, null));
                    break;
                case USER:
                    messagesContext.add(new OllamaApi.Message(OllamaApi.Message.Role.USER, chatMessage.getContent(), null, null, null, null));
                    break;
                case ASSISTANT:
                    if (!CollectionUtils.isEmpty(chatMessage.getToolCalls())) {
                        List<OllamaApi.Message.ToolCall> toolCalls = new ArrayList<>();
                        for (ChatModel.ToolCall toolCall : chatMessage.getToolCalls()) {
                            ChatModel.ToolCallFunction fn = toolCall.getFunction();
                            Map<String, Object> args = (fn != null && !CollectionUtils.isEmpty(fn.getArguments()))
                                    ? fn.getArguments().getFirst()
                                    : Map.of();
                            toolCalls.add(new OllamaApi.Message.ToolCall(
                                    new OllamaApi.Message.ToolCallFunction(
                                            fn == null ? null : fn.getName(),
                                            args,
                                            fn == null ? null : fn.getIndex()
                                    )
                            ));
                        }
                        messagesContext.add(new OllamaApi.Message(OllamaApi.Message.Role.ASSISTANT, chatMessage.getContent(), null, toolCalls, null, null));
                    } else {
                        messagesContext.add(new OllamaApi.Message(OllamaApi.Message.Role.ASSISTANT, chatMessage.getContent(), null, null, null, null));
                    }
                    break;
                case TOOL:
                    // Legacy behavior: send tool result content as-is.
                    messagesContext.add(new OllamaApi.Message(OllamaApi.Message.Role.TOOL, chatMessage.getContent(), null, null, null, null));
                    break;
            }
        }
        Map<String, Object> options = new HashMap<>(OllamaChatOptions.builder()
                .numCtx(llmProperties.ollamaContextLength)
                .temperature(llmProperties.temperature)
                .build().toMap());
        if (!CollectionUtils.isEmpty(chatRequest.getOptions())) {
            options.putAll(chatRequest.getOptions());
        }
        List<OllamaApi.ChatRequest.Tool> ollamaTools = null;
        if (!CollectionUtils.isEmpty(chatRequest.getTools())) {
            ollamaTools = new ArrayList<>();
            for (FunctionCallingModel.Tool tool : chatRequest.getTools()) {
                OllamaApi.ChatRequest.Tool.Function function = new OllamaApi.ChatRequest.Tool.Function(
                        tool.getName(),
                        tool.getDescription(),
                        FunctionCallingModel.generateToolParameters(tool.getParameters())
                );
                ollamaTools.add(new OllamaApi.ChatRequest.Tool(OllamaApi.ChatRequest.Tool.Type.FUNCTION, function));
            }
        }
        OllamaApi.ChatRequest request = OllamaApi.ChatRequest.builder(llmProperties.ollamaModelName)
                .messages(messagesContext)
                .stream(true)
                .keepAlive(secondsToDurationString(llmProperties.ollamaKeepAliveSeconds))
                .tools(ollamaTools)
                .options(options)
                .build();
        log.info("context length:{}", ModelOptionsUtils.toJsonString(request).length());
        Flux<OllamaApi.ChatResponse> eventStream = ollamaApi.streamingChat(request)
                .doOnError(t -> {
                    functionCallingSet.remove(chatCallback.subscriptionId);
                    chatCallback.errorCall.accept(t);
                }).doOnComplete(() -> {
                    if (!functionCallingSet.remove(chatCallback.subscriptionId)) {
                        chatCallback.completeCall.run();
                    }
                });
        return eventStream.subscribe(
                ollamaResponse -> this.consumeResponse(ollamaResponse, chatCallback),
                t -> {
                    // error already forwarded via doOnError; prevent ErrorCallbackNotImplemented
                }
        );
    }

    private void consumeResponse(OllamaApi.ChatResponse ollamaResponse, ChatCallback<ChatModel.ChatResponse> chatCallback) {
        List<ChatModel.ToolCall> toolCalls = null;
        if (ollamaResponse == null || (ollamaResponse.done() == null && ollamaResponse.model() == null)) {
            chatCallback.completeCall.run();
        } else {
            if (ollamaResponse.message() != null && !CollectionUtils.isEmpty(ollamaResponse.message().toolCalls())) {
                // 模型有function calling请求
                functionCallingSet.add(chatCallback.subscriptionId);
                toolCalls = new ArrayList<>();
                for (OllamaApi.Message.ToolCall ollamaToolCall : ollamaResponse.message().toolCalls()) {
                    if (ollamaToolCall.function() != null) {
                        ChatModel.ToolCall toolCall = new ChatModel.ToolCall()
                                .setFunction(
                                        new ChatModel.ToolCallFunction()
                                                .setName(ollamaToolCall.function().name())
                                                .setIndex(ollamaToolCall.function().index())
                                                .setArguments(List.of(ollamaToolCall.function().arguments()))
                                );
                        toolCalls.add(toolCall);
                    }
                }
            }
            if (Boolean.TRUE.equals(ollamaResponse.done()) && functionCallingSet.contains(chatCallback.subscriptionId)) {
                return;
            }
            ChatModel.ChatResponse chatResponse = new ChatModel.ChatResponse()
                    .setMessage(
                            new ChatModel.Message()
                                    .setRole(ChatModel.Role.ASSISTANT)
                                    .setContent(ollamaResponse.message() == null ? "" : ollamaResponse.message().content())
                                    .setToolCalls(toolCalls)
                    )
                    .setDone(ollamaResponse.done())
                    .setDoneReason(ollamaResponse.doneReason());
            chatCallback.responseCall.accept(chatResponse);
        }
    }
}
