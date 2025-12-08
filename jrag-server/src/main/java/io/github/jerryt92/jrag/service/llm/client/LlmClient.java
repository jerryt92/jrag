package io.github.jerryt92.jrag.service.llm.client;

import io.github.jerryt92.jrag.config.LlmProperties;
import io.github.jerryt92.jrag.model.ChatCallback;
import io.github.jerryt92.jrag.model.ChatModel;
import reactor.core.Disposable;

public abstract class LlmClient {
    protected final LlmProperties llmProperties;

    protected LlmClient(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    /**
     * @param chatRequest
     * @param chatCallback
     * @return Disposable 将订阅关系返回，以便在需要时取消订阅
     */
    public abstract Disposable chat(ChatModel.ChatRequest chatRequest, ChatCallback<ChatModel.ChatResponse> chatCallback);
}
