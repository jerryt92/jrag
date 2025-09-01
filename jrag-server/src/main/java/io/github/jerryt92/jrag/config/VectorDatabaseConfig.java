package io.github.jerryt92.jrag.config;

import io.github.jerryt92.jrag.mapper.mgb.EmbeddingsItemPoMapper;
import io.github.jerryt92.jrag.service.rag.vdb.VectorDatabaseService;
import io.github.jerryt92.jrag.service.rag.vdb.milvus.MilvusService;
import io.github.jerryt92.jrag.service.rag.vdb.redis.RedisVectorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VectorDatabaseConfig {

    @Value("${jrag.vector-database.provider}")
    public String vectorDatabase;
    @Value("${jrag.embedding.dimension}")
    private int dimension;
    @Value("${jrag.vector-database.milvus.cluster-endpoint}")
    private String milvusClusterEndpoint;
    @Value("${jrag.vector-database.milvus.collection-name}")
    private String milvusCollectionName;
    @Value("${jrag.vector-database.milvus.token}")
    private String milvusToken;
    @Value("${jrag.vector-database.redis.host}")
    private String redisHost;
    @Value("${jrag.vector-database.redis.port}")
    private int redisPort;
    @Value("${jrag.vector-database.redis.username}")
    private String redisUsername;
    @Value("${jrag.vector-database.redis.password}")
    private String redisPassword;
    @Value("${jrag.vector-database.redis.key-prefix}")
    private String redisKeyPrefix;

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
                        dimension
                );
                break;
            case "redis":
                vectorDatabaseService = new RedisVectorService(
                        embeddingsItemPoMapper,
                        redisHost,
                        redisPort,
                        redisUsername,
                        redisPassword,
                        redisKeyPrefix,
                        dimension
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
