package io.github.jerryt92.jrag.service.llm;

import io.github.jerryt92.jrag.config.LlmProperties;
import io.github.jerryt92.jrag.model.ChatModel;
import io.github.jerryt92.jrag.model.ChatRequestDto;
import io.github.jerryt92.jrag.model.ChatResponseDto;
import io.github.jerryt92.jrag.model.FunctionCallingModel;
import io.github.jerryt92.jrag.model.RagInfoDto;
import io.github.jerryt92.jrag.model.SseCallback;
import io.github.jerryt92.jrag.model.Translator;
import io.github.jerryt92.jrag.service.llm.client.LlmClient;
import io.github.jerryt92.jrag.service.llm.tools.FunctionCallingService;
import io.github.jerryt92.jrag.service.llm.tools.ToolInterface;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * LLM对话上下文实例
 */
@Slf4j
public class ChatContextBo {
    @Getter
    private final String contextId;
    @Getter
    private final String userId;
    @Getter
    @Setter
    private List<ChatModel.Message> messages;

    private int index;

    private final LlmClient llmClient;

    private final FunctionCallingService functionCallingService;

    private Disposable eventStreamDisposable;

    private boolean isWaitingFunction = false;

    private List<FunctionCallingModel.Tool> tools;

    private ChatContextStorageService chatContextStorageService;

    private LlmProperties llmProperties;

    private ChatModel.ChatRequest lastRequest;

    private ChatModel.Message lastAssistantMassage = new ChatModel.Message()
            .setRole(ChatModel.Role.ASSISTANT)
            .setContent("");

    @Setter
    private List<RagInfoDto> lastRagInfos;

    private ChatModel.Message lastFunctionCallingMassage = new ChatModel.Message()
            .setRole(ChatModel.Role.ASSISTANT)
            .setContent("");

    @Getter
    private Long lastRequestTime;

    private ConcurrentHashMap<Future, Future> functionCallingFutures = new ConcurrentHashMap<>();

    public ChatContextBo(String contextId, String userId, LlmClient llmClient, FunctionCallingService functionCallingService, ChatContextStorageService chatContextStorageService, LlmProperties llmProperties) {
        if (!CollectionUtils.isEmpty(functionCallingService.getTools())) {
            this.tools = new ArrayList<>();
            for (ToolInterface tool : functionCallingService.getTools().values()) {
                this.tools.add(tool.toolInfo);
            }
        }
        this.contextId = contextId;
        this.userId = userId;
        this.llmClient = llmClient;
        this.functionCallingService = functionCallingService;
        this.chatContextStorageService = chatContextStorageService;
        this.llmProperties = llmProperties;
        lastRequestTime = System.currentTimeMillis();
    }

    public void chat(ChatRequestDto chatRequestDto, SseCallback sseCallback) {
        try {
            ChatModel.ChatRequest chatRequest = Translator.translateToChatRequest(chatRequestDto);
            messages = chatRequest.getMessages();
            index = chatRequest.getMessages().size();
            lastRequestTime = System.currentTimeMillis();
            if (eventStreamDisposable != null && !eventStreamDisposable.isDisposed()) {
                // 如果存在未完成的对话，则忽略
                log.info("Event stream disposed");
            } else {
                List<ChatModel.Message> messagesContext = new ArrayList<>();
                for (ChatModel.Message wsMessage : chatRequest.getMessages()) {
                    ChatModel.Message message = new ChatModel.Message()
                            .setContent(wsMessage.getContent());
                    switch (wsMessage.getRole()) {
                        case SYSTEM:
                            message.setRole(ChatModel.Role.SYSTEM);
                            break;
                        case USER:
                            message.setRole(ChatModel.Role.USER);
                            break;
                        case ASSISTANT:
                            message.setRole(ChatModel.Role.ASSISTANT);
                            break;
                    }
                    messagesContext.add(message);
                }
                ChatModel.ChatRequest request = new ChatModel.ChatRequest()
                        .setMessages(messagesContext);
                lastRequest = request;
                if (llmProperties.useTools) {
                    request.setTools(tools);
                }
                eventStreamDisposable = llmClient.chat(request,
                        sseCallback,
                        chatResponse -> consumeResponse(chatResponse, sseCallback),
                        e -> onError(e, sseCallback),
                        () -> onComplete(sseCallback));
                sseCallback.onSseCompletion =
                        () -> {
                            if (eventStreamDisposable != null && !eventStreamDisposable.isDisposed()) {
                                eventStreamDisposable.dispose();
                            }
                            ChatService.contextSseCallbackMap.remove(contextId);
                        };
                sseCallback.onSseTimeout =
                        () -> {
                            if (eventStreamDisposable != null && !eventStreamDisposable.isDisposed()) {
                                eventStreamDisposable.dispose();
                            }
                            ChatService.contextSseCallbackMap.remove(contextId);
                        };
                sseCallback.onSseError = (e) -> {
                    if (eventStreamDisposable != null && !eventStreamDisposable.isDisposed()) {
                        eventStreamDisposable.dispose();
                    }
                    ChatService.contextSseCallbackMap.remove(contextId);
                };
            }
        } catch (Throwable t) {
            log.error("", t);
            onError(t, sseCallback);
        }
    }

    protected void toolCallResponse(Collection<FunctionCallingModel.ToolResponse> toolResponses, SseCallback sseCallback) {
        lastRequest.getMessages().add(lastFunctionCallingMassage);
        lastRequest.getMessages().add(FunctionCallingModel.buildToolResponseMessage(toolResponses));
        try {
            eventStreamDisposable = llmClient.chat(lastRequest,
                    sseCallback,
                    chatResponse -> consumeResponse(chatResponse, sseCallback),
                    e -> onError(e, sseCallback),
                    () -> onComplete(sseCallback));
        } catch (Throwable t) {
            log.error("", t);
            onError(t, sseCallback);
        }
    }

    private void consumeResponse(ChatModel.ChatResponse response, SseCallback sseCallback) {
        try {
            if (Objects.nonNull(response.getMessage())) {
                if (!CollectionUtils.isEmpty(response.getMessage().getToolCalls())) {
                    // 模型有function calling请求
                    lastFunctionCallingMassage = response.getMessage();
                    for (ChatModel.ToolCall toolCall : response.getMessage().getToolCalls()) {
                        if (toolCall.getFunction() != null) {
                            try {
                                Future<List<String>> stringFuture = functionCallingService.functionCalling(toolCall);
                                functionCallingFutures.put(stringFuture, stringFuture);
                                isWaitingFunction = true;
                                List<String> result = stringFuture.get();
                                log.info("FunctionCalling: {}", toolCall.getFunction().getName());
                                log.info("FunctionCalling result: {}", result);
                                if (result != null) {
                                    toolCallResponse(Collections.singletonList(
                                            new FunctionCallingModel.ToolResponse()
                                                    .setName(toolCall.getFunction().getName())
                                                    .setResponseData(result)
                                    ), sseCallback);
                                }
                                sseCallback.onSseCompletion = () -> {
                                    stringFuture.cancel(true);
                                    if (eventStreamDisposable != null && !eventStreamDisposable.isDisposed()) {
                                        eventStreamDisposable.dispose();
                                    }
                                    ChatService.contextSseCallbackMap.remove(contextId);
                                };
                                sseCallback.onSseTimeout =
                                        () -> {
                                            stringFuture.cancel(true);
                                            if (eventStreamDisposable != null && !eventStreamDisposable.isDisposed()) {
                                                eventStreamDisposable.dispose();
                                            }
                                            ChatService.contextSseCallbackMap.remove(contextId);
                                        };
                                sseCallback.onSseError =
                                        (e) -> {
                                            stringFuture.cancel(true);
                                            if (eventStreamDisposable != null && !eventStreamDisposable.isDisposed()) {
                                                eventStreamDisposable.dispose();
                                            }
                                            ChatService.contextSseCallbackMap.remove(contextId);
                                        };
                            } catch (Exception e) {
                                log.error("Function calling error", e);
                            }
                        }
                    }
                    return;
                }
                if (response.getMessage().getRole().equals(ChatModel.Role.SYSTEM)) {
                    messages.add(response.getMessage());
                } else if (response.getMessage().getRole().equals(ChatModel.Role.ASSISTANT)) {
                    lastAssistantMassage.setContent(lastAssistantMassage.getContent() + response.getMessage().getContent());
                    if (!CollectionUtils.isEmpty(response.getMessage().getRagInfos())) {
                        lastAssistantMassage.setRagInfos(response.getMessage().getRagInfos());
                    }
                }
            }
            // 不发送系统Prompt给前端
            if (!response.getMessage().getRole().equals(ChatModel.Role.SYSTEM)) {
                ChatResponseDto chatResponseDto = Translator.translateToChatResponseDto(response, index);
                sseCallback.responseCall.accept(chatResponseDto);
            }
            if (eventStreamDisposable.isDisposed()) {
                sseCallback.completeCall.run();
            }
        } finally {
            isWaitingFunction = false;
        }
    }

    private void onComplete(SseCallback sseCallback) {
        // 流结束
        log.info("回答" + ": " + this.lastAssistantMassage.getContent());
        this.lastAssistantMassage.setRagInfos(this.lastRagInfos);
        this.messages.add(this.lastAssistantMassage);
        this.lastAssistantMassage = new ChatModel.Message()
                .setRole(ChatModel.Role.ASSISTANT)
                .setContent("");
        this.lastRagInfos = null;
        sseCallback.completeCall.run();
        functionCallingFutures.forEach((future, future1) -> future.cancel(true));
        isWaitingFunction = false;
        chatContextStorageService.storageChatContextToDb(this);
    }

    private void onError(Throwable t, SseCallback sseCallback) {
        if (t instanceof org.springframework.web.reactive.function.client.WebClientResponseException ex) {
            log.error("LLMClient api error, errorCode: {}, errorMessage: {}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } else {
            log.error("", t);
        }
        functionCallingFutures.forEach((future, future1) -> future.cancel(true));
        isWaitingFunction = false;
        sseCallback.errorCall.accept(t);
    }
}
