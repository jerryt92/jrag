package io.github.jerryt92.jrag.config;

import io.github.jerryt92.jrag.model.EmbeddingModel;
import io.github.jerryt92.jrag.po.mgb.EmbeddingsItemPoWithBLOBs;
import io.github.jerryt92.jrag.service.PropertiesService;
import io.github.jerryt92.jrag.service.embedding.EmbeddingService;
import io.github.jerryt92.jrag.service.rag.knowledge.KnowledgeService;
import io.github.jerryt92.jrag.service.rag.vdb.VectorDatabaseService;
import io.github.jerryt92.jrag.utils.HashUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class VectorDatabaseInit {
    private final LlmProperties llmProperties;
    private final EmbeddingService embeddingService;
    private final KnowledgeService knowledgeService;
    private final VectorDatabaseService vectorDatabaseService;
    private final PropertiesService propertiesService;
    private String metricType;

    public VectorDatabaseInit(LlmProperties llmProperties, EmbeddingService embeddingService, KnowledgeService knowledgeService, VectorDatabaseService vectorDatabaseService, PropertiesService propertiesService) {
        this.llmProperties = llmProperties;
        this.embeddingService = embeddingService;
        this.knowledgeService = knowledgeService;
        this.vectorDatabaseService = vectorDatabaseService;
        this.propertiesService = propertiesService;
    }

    @PostConstruct
    public void init() {
        metricType = propertiesService.getProperty(PropertiesService.RETRIEVE_METRIC_TYPE);
        if (llmProperties.useRag && embeddingService.getDimension() != null) {
            vectorDatabaseService.reBuildVectorDatabase(embeddingService.getDimension(), metricType);
            List<EmbeddingsItemPoWithBLOBs> embeddingsItemPoWithBLOBs = knowledgeService.checkAndGetEmbedData(embeddingService.getCheckEmbeddingHash());
            vectorDatabaseService.initData(embeddingsItemPoWithBLOBs);
        }
    }

    public void reload() {
        // 检查是否需要重建向量数据
        String newMetricType = propertiesService.getProperty(PropertiesService.RETRIEVE_METRIC_TYPE);
        boolean needRebuild = false;
        EmbeddingModel.EmbeddingsResponse response = embeddingService.embed(embeddingService.checkEmbeddingsRequest);
        if (response != null && !response.getData().isEmpty()) {
            EmbeddingModel.EmbeddingsItem testEmbed = response.getData().getFirst();
            try {
                String newCheckEmbeddingHash = HashUtil.getMessageDigest(Arrays.toString(testEmbed.getEmbeddings()).getBytes(), HashUtil.MdAlgorithm.SHA256);
                if (!newCheckEmbeddingHash.equals(embeddingService.getCheckEmbeddingHash())) {
                    needRebuild = true;
                }
                if (!newMetricType.equals(metricType)) {
                    needRebuild = true;
                }
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
            if (needRebuild) {
                embeddingService.init();
                init();
            }
        } else {
            log.warn("Init failed: Unable to fetch embedding for test input.");
        }
    }
}
