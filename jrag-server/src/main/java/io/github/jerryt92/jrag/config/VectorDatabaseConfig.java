package io.github.jerryt92.jrag.config;

import io.github.jerryt92.jrag.service.embedding.EmbeddingService;
import io.github.jerryt92.jrag.service.rag.vdb.VectorDatabaseService;
import io.github.jerryt92.jrag.service.rag.vdb.milvus.MilvusLiteService;
import io.github.jerryt92.jrag.service.rag.vdb.milvus.MilvusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class VectorDatabaseConfig {
    @Value("${jrag.vector-database.provider}")
    public String vectorDatabase;
    @Value("${jrag.vector-database.milvus.cluster-endpoint}")
    private String milvusClusterEndpoint;
    @Value("${jrag.vector-database.milvus.collection-name}")
    private String milvusCollectionName;
    @Value("${jrag.vector-database.milvus.token}")
    private String milvusToken;

    @Bean
    public VectorDatabaseService vectorDatabaseService(EmbeddingService embeddingService) {
        VectorDatabaseService vectorDatabaseService;
        embeddingService.init();
        switch (vectorDatabase) {
            case "milvus":
                vectorDatabaseService = new MilvusService(
                        milvusClusterEndpoint,
                        milvusCollectionName,
                        milvusToken
                );
                break;
            case "milvus-lite":
                vectorDatabaseService = new MilvusLiteService(
                        "http://localhost:29530",
                        milvusCollectionName,
                        milvusToken
                );
                break;
            default:
                throw new RuntimeException("Unknown vector database: " + vectorDatabase);
        }
        return vectorDatabaseService;
    }
}
