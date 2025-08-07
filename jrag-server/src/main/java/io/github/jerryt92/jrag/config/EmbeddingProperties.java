package io.github.jerryt92.jrag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingProperties {
    @Value("${jrag.embedding.embedding-provider}")
    public String embeddingProvider;
    @Value("${jrag.embedding.ollama.model-name}")
    public String ollamaModelName;
    @Value("${jrag.embedding.ollama.base-url}")
    public String ollamaBaseUrl;
    @Value("${jrag.embedding.ollama.keep_alive_seconds}")
    public int keepAliveSeconds;
    @Value("${jrag.embedding.open-ai.model-name}")
    public String openAiModelName;
    @Value("${jrag.embedding.open-ai.base-url}")
    public String openAiBaseUrl;
    @Value("${jrag.embedding.open-ai.embeddings-path}")
    public String embeddingsPath;
    @Value("${jrag.embedding.open-ai.key}")
    public String openAiKey;
}
