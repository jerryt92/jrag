package io.github.jerryt92.jrag.service.llm.client;

import io.github.jerryt92.jrag.config.LlmProperties;
import io.github.jerryt92.jrag.model.ChatModel;
import io.github.jerryt92.jrag.model.SseCallback;
import reactor.core.Disposable;

import java.util.function.Consumer;

public abstract class LlmClient {
    protected final LlmProperties llmProperties;

    protected LlmClient(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    /**
     * @param chatRequest
     * @param sseCallback
     * @param onResponse
     * @param onError
     * @param onComplete
     * @return Disposable 将订阅关系返回，以便在需要时取消订阅
     */
    public abstract Disposable chat(ChatModel.ChatRequest chatRequest, SseCallback sseCallback, Consumer<ChatModel.ChatResponse> onResponse, Consumer<? super Throwable> onError, Runnable onComplete);
}
