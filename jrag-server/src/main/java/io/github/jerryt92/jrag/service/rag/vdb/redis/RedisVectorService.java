package io.github.jerryt92.jrag.service.rag.vdb.redis;

import io.github.jerryt92.jrag.mapper.mgb.EmbeddingsItemPoMapper;
import io.github.jerryt92.jrag.model.EmbeddingModel;
import io.github.jerryt92.jrag.po.mgb.EmbeddingsItemPoExample;
import io.github.jerryt92.jrag.po.mgb.EmbeddingsItemPoWithBLOBs;
import io.github.jerryt92.jrag.service.rag.vdb.VectorDatabaseService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.model.ModelOptionsUtils;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.IndexDefinition;
import redis.clients.jedis.search.IndexOptions;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.Schema;
import redis.clients.jedis.search.SearchResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class RedisVectorService implements VectorDatabaseService {
    private final EmbeddingsItemPoMapper embeddingsItemPoMapper;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String keyPrefix;
    private final String idxName;
    private final int dimension;
    private UnifiedJedis jedis;

    public RedisVectorService(
            EmbeddingsItemPoMapper embeddingsItemPoMapper,
            String host,
            int port,
            String username,
            String password,
            String keyPrefix,
            int dimension) {
        this.embeddingsItemPoMapper = embeddingsItemPoMapper;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.keyPrefix = keyPrefix;
        this.idxName = keyPrefix + "_idx";
        this.dimension = dimension;
    }

    @Override
    public void init() {
        try {
            JedisClientConfig jedisClientConfig;
            if (StringUtils.isNotBlank(password) && StringUtils.isEmpty(username)) {
                jedisClientConfig = DefaultJedisClientConfig
                        .builder()
                        .password(password)
                        .build();
            } else if (StringUtils.isNotBlank(password) && StringUtils.isNotBlank(username)) {
                jedisClientConfig = DefaultJedisClientConfig
                        .builder()
                        .user(username)
                        .password(password)
                        .build();
            } else {
                jedisClientConfig = DefaultJedisClientConfig.builder().build();
            }
            jedis = new UnifiedJedis(new HostAndPort(host, port), jedisClientConfig);
            createIndex();
            loadDataFromDatabase();
        } catch (Exception e) {
            log.error("Error initializing Redis connection", e);
        }
    }

    private byte[] floatArrayToByteArray(float[] input) {
        byte[] bytes = new byte[Float.BYTES * input.length];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().put(input);
        return bytes;
    }


    private void createIndex() {
        try {
            // 检查索引是否存在
            try {
                jedis.ftInfo(idxName);
                // 如果索引存在，则删除索引
                jedis.ftDropIndex(idxName);
                log.info("Existing index '" + idxName + "' dropped.");
            } catch (Exception e) {
                // 如果索引不存在，会抛出异常，无需处理
                log.info("No existing index '" + idxName + "' found, proceeding to create.");
            }
            // 定义向量字段属性
            Map<String, Object> vectorAttrs = new HashMap<>();
            vectorAttrs.put("TYPE", "FLOAT32");
            vectorAttrs.put("DIM", dimension);
            vectorAttrs.put("DISTANCE_METRIC", "COSINE");
            vectorAttrs.put("INITIAL_CAP", 1000);
            // 创建索引定义和模式
            IndexDefinition definition = new IndexDefinition().setPrefixes(new String[]{keyPrefix + ":"});
            Schema schema = new Schema()
                    .addTextField("text", 1.0)
                    .addTagField("embeddingModel")
                    .addTagField("embeddingProvider")
                    .addTagField("textChunkId")
                    .addHNSWVectorField("vector", vectorAttrs);
            // 创建新索引
            jedis.ftCreate(idxName, IndexOptions.defaultOptions().setDefinition(definition), schema);
            log.info("Index '" + idxName + "' created successfully.");
        } catch (Exception e) {
            log.error("Error creating index", e);
        }
    }

    private void loadDataFromDatabase() {
        List<EmbeddingsItemPoWithBLOBs> embeddingsItemPos = embeddingsItemPoMapper.selectByExampleWithBLOBs(new EmbeddingsItemPoExample());
        for (EmbeddingsItemPoWithBLOBs embeddingsItemPo : embeddingsItemPos) {
            storeEmbedding(embeddingsItemPo);
        }
    }

    private void storeEmbedding(EmbeddingsItemPoWithBLOBs embeddingsItemPo) {
        Map<String, Object> embeddingsItem = ModelOptionsUtils.objectToMap(embeddingsItemPo);
        Map<String, String> embeddingsItemMap = embeddingsItem.entrySet()
                .stream()
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().toString()));
        // 存储为Hash
        String key = keyPrefix + ":" + embeddingsItem.get("hash");
        jedis.hset(key, embeddingsItemMap);
        // 存储向量
        String[] embedding = embeddingsItemPo.getEmbedding().split(",");
        float[] vector = new float[embedding.length];
        for (int i = 0; i < embedding.length; i++) {
            vector[i] = Float.parseFloat(embedding[i]);
        }
        jedis.hset(key.getBytes(), "vector".getBytes(), floatArrayToByteArray(vector));
    }

    @Override
    public void putData(List<EmbeddingsItemPoWithBLOBs> embeddingsItems) {
        for (EmbeddingsItemPoWithBLOBs embeddingsItemPo : embeddingsItems) {
            storeEmbedding(embeddingsItemPo);
        }
    }

    @Override
    public List<EmbeddingModel.EmbeddingsQueryItem> knnSearchByCos(float[] queryVector, int topK, Float minCosScore) {
        byte[] queryBlob = floatArrayToByteArray(queryVector);
        Query query = new Query("*=>[KNN $K @vector $BLOB AS score]")
                .returnFields("hash", "text", "embeddingModel", "embeddingProvider", "textChunkId", "score")
                .addParam("K", topK)
                .addParam("BLOB", queryBlob)
                .dialect(2);
        try {
            SearchResult result = jedis.ftSearch(idxName, query);
            List<Document> documents = result.getDocuments();

            List<EmbeddingModel.EmbeddingsQueryItem> embeddingsQueryItems = new ArrayList<>();
            for (Document document : documents) {
                Object scoreObj = document.get("score");
                if (scoreObj == null) {
                    continue; // 跳过没有评分的结果
                }
                float score = 1.0f - Float.parseFloat(scoreObj.toString()); // 若 distance_metric == COSINE

                if (minCosScore == null || score >= minCosScore) {
                    EmbeddingModel.EmbeddingsQueryItem embeddingsQueryItem = new EmbeddingModel.EmbeddingsQueryItem()
                            .setHash((String) document.get("hash"))
                            .setScore(score)
                            .setEmbeddingModel((String) document.get("embeddingModel"))
                            .setEmbeddingProvider((String) document.get("embeddingProvider"))
                            .setText((String) document.get("text"))
                            .setTextChunkId((String) document.get("textChunkId"));
                    embeddingsQueryItems.add(embeddingsQueryItem);
                }
            }
            // 手动排序：按相似度降序排列
            embeddingsQueryItems.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
            return embeddingsQueryItems;
        } catch (Exception e) {
            log.error("Error performing vector search", e);
            return Collections.emptyList();
        }
    }
}