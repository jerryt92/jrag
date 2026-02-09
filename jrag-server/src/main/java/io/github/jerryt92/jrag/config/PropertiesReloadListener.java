package io.github.jerryt92.jrag.config;

import io.github.jerryt92.jrag.event.PropertiesUpdatedEvent;
import io.github.jerryt92.jrag.service.PropertiesService;
import io.github.jerryt92.jrag.service.embedding.EmbeddingService;
import io.github.jerryt92.jrag.service.llm.client.DynamicLlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PropertiesReloadListener {
    private final LlmProperties llmProperties;
    private final EmbeddingProperties embeddingProperties;
    private final DynamicLlmClient dynamicLlmClient;
    private final EmbeddingService embeddingService;
    private final VectorDatabaseInit vectorDatabaseInit;

    public PropertiesReloadListener(LlmProperties llmProperties,
                                    EmbeddingProperties embeddingProperties,
                                    DynamicLlmClient dynamicLlmClient,
                                    EmbeddingService embeddingService,
                                    VectorDatabaseInit vectorDatabaseInit) {
        this.llmProperties = llmProperties;
        this.embeddingProperties = embeddingProperties;
        this.dynamicLlmClient = dynamicLlmClient;
        this.embeddingService = embeddingService;
        this.vectorDatabaseInit = vectorDatabaseInit;
    }

    @EventListener
    public void handlePropertiesUpdated(PropertiesUpdatedEvent event) {
        try {
            llmProperties.reloadFromDb();
            embeddingProperties.reloadFromDb();
            dynamicLlmClient.reload();
            embeddingService.rebuildClient();
            if (event.getPropertyNames().stream().anyMatch(name ->
                    name.equals(PropertiesService.RETRIEVE_METRIC_TYPE) ||
                            name.equals(EmbeddingProperties.KEY_PROVIDER) ||
                            name.equals(EmbeddingProperties.KEY_OLLAMA_MODEL_NAME) ||
                            name.equals(EmbeddingProperties.KEY_OLLAMA_BASE_URL) ||
                            name.equals(EmbeddingProperties.KEY_OLLAMA_KEEP_ALIVE_SECONDS) ||
                            name.equals(EmbeddingProperties.KEY_OLLAMA_KEY) ||
                            name.equals(EmbeddingProperties.KEY_OPEN_AI_MODEL_NAME) ||
                            name.equals(EmbeddingProperties.KEY_OPEN_AI_BASE_URL) ||
                            name.equals(EmbeddingProperties.KEY_OPEN_AI_EMBEDDINGS_PATH) ||
                            name.equals(EmbeddingProperties.KEY_OPEN_AI_KEY)
            )) {
                vectorDatabaseInit.reload();
            }
            log.info("AI properties reloaded: {}", event.getPropertyNames());
        } catch (Exception e) {
            log.warn("Reload AI properties failed.", e);
        }
    }
}
