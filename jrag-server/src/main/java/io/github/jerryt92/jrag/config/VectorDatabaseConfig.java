package io.github.jerryt92.jrag.config;

import io.github.jerryt92.jrag.mapper.mgb.EmbeddingsItemPoMapper;
import io.github.jerryt92.jrag.service.rag.vdb.VectorDatabaseService;
import io.github.jerryt92.jrag.service.rag.vdb.milvus.MilvusLiteService;
import io.github.jerryt92.jrag.service.rag.vdb.milvus.MilvusService;
import io.milvus.v2.common.IndexParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorDatabaseConfig {

    @Value("${jrag.vector-database.provider}")
    public String vectorDatabase;
    @Value("${jrag.embedding.dimension}")
    public int dimension;
    @Value("${jrag.vector-database.milvus.cluster-endpoint}")
    private String milvusClusterEndpoint;
    @Value("${jrag.vector-database.milvus.collection-name}")
    private String milvusCollectionName;
    @Value("${jrag.vector-database.milvus.token}")
    private String milvusToken;

    @Bean
    public VectorDatabaseService vectorDatabaseService(EmbeddingsItemPoMapper embeddingsItemPoMapper, LlmProperties llmProperties) {
        VectorDatabaseService vectorDatabaseService;
        switch (vectorDatabase) {
            case "milvus":
                vectorDatabaseService = new MilvusService(
                        embeddingsItemPoMapper,
                        milvusClusterEndpoint,
                        milvusCollectionName,
                        milvusToken,
                        dimension,
                        IndexParam.MetricType.valueOf("COSINE")
                );
                break;
            case "milvus-lite":
                vectorDatabaseService = new MilvusLiteService(
                        embeddingsItemPoMapper,
                        "http://localhost:29530",
                        milvusCollectionName,
                        milvusToken,
                        dimension,
                        "COSINE"
                );
                break;
            default:
                throw new RuntimeException("Unknown vector database: " + vectorDatabase);
        }
        if (llmProperties.useRag) {
            vectorDatabaseService.init();
        }
        return vectorDatabaseService;
    }
}
