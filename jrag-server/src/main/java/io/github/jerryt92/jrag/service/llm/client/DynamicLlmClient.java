package io.github.jerryt92.jrag.service.llm.client;

import io.github.jerryt92.jrag.config.LlmProperties;
import io.github.jerryt92.jrag.model.ChatCallback;
import io.github.jerryt92.jrag.model.ChatModel;
import io.github.jerryt92.jrag.utils.LlmBaseUtils;
import reactor.core.Disposable;

import java.util.concurrent.atomic.AtomicReference;

public class DynamicLlmClient extends LlmClient {
    private final AtomicReference<LlmClient> delegate = new AtomicReference<>();

    public DynamicLlmClient(LlmProperties llmProperties) {
        super(llmProperties);
        this.delegate.set(buildClient());
        LlmBaseUtils.llmClient = this;
    }

    public void reload() {
        LlmBaseUtils.llmClient = this;
        this.delegate.set(buildClient());
    }

    @Override
    public Disposable chat(ChatModel.ChatRequest chatRequest, ChatCallback<ChatModel.ChatResponse> chatCallback) {
        return delegate.get().chat(chatRequest, chatCallback);
    }

    private LlmClient buildClient() {
        return switch (llmProperties.llmProvider) {
            case "ollama" -> new OllamaClient(llmProperties);
            case "open-ai" -> new OpenAiClient(llmProperties);
            default -> throw new RuntimeException("Unknown LLM provider: " + llmProperties.llmProvider);
        };
    }
}
