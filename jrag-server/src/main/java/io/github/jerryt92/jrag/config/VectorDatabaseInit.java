package io.github.jerryt92.jrag.config;

import io.github.jerryt92.jrag.model.EmbeddingModel;
import io.github.jerryt92.jrag.po.mgb.EmbeddingsItemPoWithBLOBs;
import io.github.jerryt92.jrag.service.PropertiesService;
import io.github.jerryt92.jrag.service.embedding.EmbeddingService;
import io.github.jerryt92.jrag.service.rag.knowledge.KnowledgeService;
import io.github.jerryt92.jrag.service.rag.vdb.VectorDatabaseService;
import io.github.jerryt92.jrag.utils.HashUtil;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VectorDatabaseInit {
    private final LlmProperties llmProperties;
    private final EmbeddingService embeddingService;
    private final KnowledgeService knowledgeService;
    private final VectorDatabaseService vectorDatabaseService;
    private final PropertiesService propertiesService;
    private volatile String metricType;
    private final ExecutorService initExecutor = Executors.newSingleThreadExecutor();
    private Future<?> currentTask;

    public VectorDatabaseInit(LlmProperties llmProperties, EmbeddingService embeddingService, KnowledgeService knowledgeService, VectorDatabaseService vectorDatabaseService, PropertiesService propertiesService) {
        this.llmProperties = llmProperties;
        this.embeddingService = embeddingService;
        this.knowledgeService = knowledgeService;
        this.vectorDatabaseService = vectorDatabaseService;
        this.propertiesService = propertiesService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        init();
    }

    /**
     * 加锁 synchronized，防止 reload 和启动同时触发导致产生多个任务
     */
    public synchronized void init() {
        // 1. 如果有正在运行的任务，尝试取消它
        if (currentTask != null && !currentTask.isDone()) {
            log.info("Stopping previous initialization task...");
            // 发送中断信号
            currentTask.cancel(true);
        }
        // 2. 提交新任务
        currentTask = initExecutor.submit(this::doInitTask);
    }

    private void doInitTask() {
        try {
            metricType = propertiesService.getProperty(PropertiesService.RETRIEVE_METRIC_TYPE);
            if (llmProperties.useRag && embeddingService.getDimension() != null) {
                int retryCount = 0;
                boolean isHealthy = false;
                while (!isHealthy && retryCount < 50 && !Thread.currentThread().isInterrupted()) {
                    retryCount++;
                    try {
                        vectorDatabaseService.reBuildVectorDatabase(embeddingService.getDimension(), metricType);
                        isHealthy = true;
                    } catch (RuntimeException t) {
                        log.warn("Init failed: Failed to rebuild vector database (Attempt {}/50). Retrying...", retryCount);
                        try {
                            TimeUnit.SECONDS.sleep(5);
                        } catch (InterruptedException e) {
                            // 重新设置中断状态，并跳出循环
                            Thread.currentThread().interrupt();
                            log.info("Initialization task interrupted during sleep.");
                            return;
                        }
                    }
                }
                if (Thread.currentThread().isInterrupted()) {
                    log.info("Initialization task stopped by interrupt.");
                    return;
                }
                if (!isHealthy) {
                    log.error("Failed to initialize Vector Database after 50 attempts.");
                    return;
                }
                log.info("Loading vector data...");
                List<EmbeddingsItemPoWithBLOBs> embeddingsItemPoWithBLOBs = knowledgeService.checkAndGetEmbedData(embeddingService.getCheckEmbeddingHash());
                vectorDatabaseService.initData(embeddingsItemPoWithBLOBs);
                log.info("Vector Database initialized successfully.");
            }
        } catch (Exception e) {
            log.error("Unexpected error during Vector Database initialization", e);
        }
    }

    public synchronized void reload() {
        // 检查是否需要重建向量数据
        String newMetricType = propertiesService.getProperty(PropertiesService.RETRIEVE_METRIC_TYPE);
        boolean needRebuild = false;
        try {
            EmbeddingModel.EmbeddingsResponse response = embeddingService.embed(embeddingService.checkEmbeddingsRequest);
            if (response != null && !response.getData().isEmpty()) {
                EmbeddingModel.EmbeddingsItem testEmbed = response.getData().getFirst();
                String newCheckEmbeddingHash = HashUtil.getMessageDigest(Arrays.toString(testEmbed.getEmbeddings()).getBytes(), HashUtil.MdAlgorithm.SHA256);
                if (!newCheckEmbeddingHash.equals(embeddingService.getCheckEmbeddingHash())) {
                    needRebuild = true;
                }
                if (!newMetricType.equals(metricType)) {
                    needRebuild = true;
                }
                if (needRebuild) {
                    log.info("Configuration change detected. Reloading...");
                    embeddingService.init();
                    init();
                } else {
                    log.info("No configuration changes detected.");
                }
            } else {
                log.warn("Init failed: Unable to fetch embedding for test input.");
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hash algorithm not found", e);
        }
    }

    // 容器销毁时关闭线程池
    @PreDestroy
    public void destroy() {
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        initExecutor.shutdownNow();
    }
}