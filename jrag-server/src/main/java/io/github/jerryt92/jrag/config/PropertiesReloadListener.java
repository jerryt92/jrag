package io.github.jerryt92.jrag.config;

import io.github.jerryt92.jrag.event.PropertiesUpdatedEvent;
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
            embeddingService.reload();
            vectorDatabaseInit.init();
            log.info("AI properties reloaded: {}", event.getPropertyNames());
        } catch (Exception e) {
            log.warn("Reload AI properties failed.", e);
        }
    }
}
