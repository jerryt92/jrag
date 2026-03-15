package io.github.jerryt92.jrag.service.llm;

import io.github.jerryt92.jrag.config.LlmProperties;
import io.github.jerryt92.jrag.model.ChatCallback;
import io.github.jerryt92.jrag.model.ChatModel;
import io.github.jerryt92.jrag.model.ChatRequestDto;
import io.github.jerryt92.jrag.model.ChatResponseDto;
import io.github.jerryt92.jrag.model.FunctionCallingModel;
import io.github.jerryt92.jrag.model.RagInfoDto;
import io.github.jerryt92.jrag.model.Translator;
import io.github.jerryt92.jrag.service.llm.client.LlmClient;
import io.github.jerryt92.jrag.service.llm.tools.FunctionCallingService;
import io.github.jerryt92.jrag.service.llm.tools.ToolInterface;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private boolean isWaitingFunction = false;

    private Disposable eventStreamDisposable;

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

    private final AtomicBoolean responsePersisted = new AtomicBoolean(false);

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

    public void chat(ChatRequestDto chatRequestDto, ChatCallback<ChatResponseDto> chatChatCallback) {
        try {
            ChatModel.ChatRequest chatRequest = Translator.translateToChatRequest(chatRequestDto);
            messages = chatRequest.getMessages();
            index = chatRequest.getMessages().size();
            lastRequestTime = System.currentTimeMillis();
            responsePersisted.set(false);
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
                        case TOOL:
                            message.setRole(ChatModel.Role.TOOL);
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
                ChatCallback<ChatModel.ChatResponse> chatCallback = new ChatCallback<>(
                        chatChatCallback.subscriptionId,
                        chatResponse -> consumeResponse(chatResponse, chatChatCallback),
                        () -> onComplete(chatChatCallback),
                        (t) -> onError(t, chatChatCallback),
                        chatChatCallback.timeoutCall
                );
                eventStreamDisposable = llmClient.chat(request, chatCallback);
                chatChatCallback.onWebsocketClose = () -> {
                    persistInterruptedResponse();
                    if (eventStreamDisposable != null && !eventStreamDisposable.isDisposed()) {
                        eventStreamDisposable.dispose();
                    }
                    ChatService.contextChatCallbackMap.remove(contextId);
                };
            }
        } catch (Throwable t) {
            log.error("", t);
            onError(t, chatChatCallback);
        }
    }

    protected void toolCallResponse(Collection<FunctionCallingModel.ToolResponse> toolResponses, String toolCallId, ChatCallback<ChatResponseDto> chatChatCallback) {
        lastRequest.getMessages().add(lastFunctionCallingMassage);
        lastRequest.getMessages().add(FunctionCallingModel.buildToolResponseMessage(toolResponses, toolCallId));
        try {
            ChatCallback<ChatModel.ChatResponse> chatCallback = new ChatCallback<>(
                    chatChatCallback.subscriptionId,
                    chatResponse -> consumeResponse(chatResponse, chatChatCallback),
                    () -> onComplete(chatChatCallback),
                    (t) -> onError(t, chatChatCallback),
                    chatChatCallback.timeoutCall
            );
            eventStreamDisposable = llmClient.chat(lastRequest, chatCallback);
        } catch (Throwable t) {
            log.error("", t);
            onError(t, chatChatCallback);
        }
    }

    private void consumeResponse(ChatModel.ChatResponse response, ChatCallback<ChatResponseDto> chatChatCallback) {
        if (Objects.nonNull(response.getMessage())) {
            if (!CollectionUtils.isEmpty(response.getMessage().getToolCalls())) {
                // 模型有function calling请求
                lastFunctionCallingMassage = response.getMessage();
                for (ChatModel.ToolCall toolCall : response.getMessage().getToolCalls()) {
                    if (toolCall.getFunction() != null) {
                        try {
                            Future<ChatModel.ToolCallResult> stringFuture = functionCallingService.functionCalling(toolCall);
                            functionCallingFutures.put(stringFuture, stringFuture);
                            isWaitingFunction = true;
                            ChatModel.ToolCallResult toolCallResult = stringFuture.get();
                            log.info("FunctionCalling: {}", toolCall.getFunction().getName());
                            log.info("FunctionCalling result: {}", toolCallResult.getResults());
                            if (toolCallResult.getResults() != null) {
                                toolCallResponse(Collections.singletonList(
                                        new FunctionCallingModel.ToolResponse()
                                                .setName(toolCall.getFunction().getName())
                                                .setResponseData(toolCallResult.getResults())
                                ), toolCallResult.getId(), chatChatCallback);
                            }
                            chatChatCallback.onWebsocketClose = () -> {
                                persistInterruptedResponse();
                                stringFuture.cancel(true);
                                if (eventStreamDisposable != null && !eventStreamDisposable.isDisposed()) {
                                    eventStreamDisposable.dispose();
                                }
                                ChatService.contextChatCallbackMap.remove(contextId);
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
            chatChatCallback.responseCall.accept(chatResponseDto);
        }
        if (eventStreamDisposable.isDisposed()) {
            chatChatCallback.completeCall.run();
        }
    }

    private void onComplete(ChatCallback<ChatResponseDto> chatChatCallback) {
        // 流结束
        log.info("回答" + ": " + this.lastAssistantMassage.getContent());
        if (!isWaitingFunction) {
            persistResponse(false);
            chatChatCallback.completeCall.run();
        }
        isWaitingFunction = false;
        functionCallingFutures.forEach((future, future1) -> future.cancel(true));
    }

    private void onError(Throwable t, ChatCallback<ChatResponseDto> chatChatCallback) {
        if (t instanceof org.springframework.web.reactive.function.client.WebClientResponseException ex) {
            log.error("LLMClient api error, errorCode: {}, errorMessage: {}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } else {
            log.error("", t);
        }
        isWaitingFunction = false;
        functionCallingFutures.forEach((future, future1) -> future.cancel(true));
        chatChatCallback.errorCall.accept(t);
    }

    private void persistInterruptedResponse() {
        persistResponse(true);
    }

    private void persistResponse(boolean interrupted) {
        if (!responsePersisted.compareAndSet(false, true)) {
            return;
        }
        if (StringUtils.isNotBlank(this.lastAssistantMassage.getContent())) {
            if (interrupted) {
                this.lastAssistantMassage.setContent(appendEllipsis(this.lastAssistantMassage.getContent()));
            }
            this.lastAssistantMassage.setRagInfos(this.lastRagInfos);
            this.messages.add(this.lastAssistantMassage);
        }
        this.lastAssistantMassage = new ChatModel.Message()
                .setRole(ChatModel.Role.ASSISTANT)
                .setContent("");
        this.lastRagInfos = null;
        chatContextStorageService.storageChatContextToDb(this);
    }

    // 对中断保存的回答补全省略号，并避免重复追加。
    private String appendEllipsis(String content) {
        if (StringUtils.endsWithAny(content, "...", "…", "......", "……")) {
            return content;
        }
        return content + "...";
    }
}
