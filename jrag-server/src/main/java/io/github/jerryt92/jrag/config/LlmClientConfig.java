package io.github.jerryt92.jrag.config;

import io.github.jerryt92.jrag.service.llm.client.LlmClient;
import io.github.jerryt92.jrag.service.llm.client.OllamaClient;
import io.github.jerryt92.jrag.service.llm.client.OpenAiClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmClientConfig {

    private final LlmProperties llmProperties;

    public LlmClientConfig(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    @Bean
    public LlmClient llmClient() {
        switch (llmProperties.llmProvider) {
            case "ollama":
                return new OllamaClient(llmProperties);
            case "open-ai":
                return new OpenAiClient(llmProperties);
            default:
                throw new RuntimeException("Unknown LLM provider: " + llmProperties.llmProvider);
        }
    }
}
