package io.github.jerryt92.jrag.config;

import io.github.jerryt92.jrag.po.mgb.EmbeddingsItemPoWithBLOBs;
import io.github.jerryt92.jrag.service.PropertiesService;
import io.github.jerryt92.jrag.service.embedding.EmbeddingService;
import io.github.jerryt92.jrag.service.rag.knowledge.KnowledgeService;
import io.github.jerryt92.jrag.service.rag.vdb.VectorDatabaseService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
public class VectorDatabaseInitConfig {
    private final LlmProperties llmProperties;
    private final EmbeddingService embeddingService;
    private final KnowledgeService knowledgeService;
    private final VectorDatabaseService vectorDatabaseService;
    private final PropertiesService propertiesService;

    public VectorDatabaseInitConfig(LlmProperties llmProperties, EmbeddingService embeddingService, KnowledgeService knowledgeService, VectorDatabaseService vectorDatabaseService, PropertiesService propertiesService) {
        this.llmProperties = llmProperties;
        this.embeddingService = embeddingService;
        this.knowledgeService = knowledgeService;
        this.vectorDatabaseService = vectorDatabaseService;
        this.propertiesService = propertiesService;
    }

    @PostConstruct
    public void init() {
        String metricType = propertiesService.getProperty(PropertiesService.RETRIEVE_METRIC_TYPE);
        if (llmProperties.useRag) {
            vectorDatabaseService.reBuildVectorDatabase(embeddingService.getDimension(), metricType);
            List<EmbeddingsItemPoWithBLOBs> embeddingsItemPoWithBLOBs = knowledgeService.checkAndGetEmbedData(embeddingService.getCheckEmbeddingHash());
            vectorDatabaseService.initData(embeddingsItemPoWithBLOBs);
        }
    }
}
