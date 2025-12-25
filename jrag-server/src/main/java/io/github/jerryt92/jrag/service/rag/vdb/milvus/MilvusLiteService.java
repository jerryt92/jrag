package io.github.jerryt92.jrag.service.rag.vdb.milvus;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.github.jerryt92.jrag.model.EmbeddingModel;
import io.github.jerryt92.jrag.model.Translator;
import io.github.jerryt92.jrag.po.mgb.EmbeddingsItemPoWithBLOBs;
import io.github.jerryt92.jrag.service.rag.vdb.VectorDatabaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class MilvusLiteService implements VectorDatabaseService {
    private final String collectionName;
    private String metricType;

    private final WebClient webClient;
    private final Gson gson; // 引入 Gson

    public MilvusLiteService(
            String clusterEndpoint,
            String collectionName,
            String token
    ) {
        this.collectionName = collectionName;
        this.gson = new Gson(); // 初始化 Gson

        String validEndpoint = clusterEndpoint.endsWith("/")
                ? clusterEndpoint.substring(0, clusterEndpoint.length() - 1)
                : clusterEndpoint;

        this.webClient = WebClient.builder()
                .baseUrl(validEndpoint)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }

    @Override
    public void reBuildVectorDatabase(int dimension, String metricTypeStr) {
        metricType = metricTypeStr;
        // 1. 构建 Schema
        Map<String, Object> schemaPayload = buildSchemaPayload(dimension);
        sendRequest("/collections/create", schemaPayload);
        log.info("Collection {} created/rebuilt via Python API", collectionName);
    }

    private Map<String, Object> buildSchemaPayload(int dimension) {
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(Map.of("field_name", "hash", "data_type", "VarChar", "max_length", 40, "is_primary", true, "description", "SHA-1 Hash"));
        fields.add(Map.of("field_name", "embedding_model", "data_type", "VarChar", "max_length", 128));
        fields.add(Map.of("field_name", "embedding_provider", "data_type", "VarChar", "max_length", 128));
        fields.add(Map.of("field_name", "text", "data_type", "VarChar", "max_length", 4096));
        fields.add(Map.of("field_name", "embedding", "data_type", "FloatVector", "dimension", dimension));
        fields.add(Map.of("field_name", "text_chunk_id", "data_type", "VarChar", "max_length", 40));
        List<Map<String, Object>> indexes = new ArrayList<>();
        // FLAT 是最基础的索引类型，Milvus Lite 绝对支持
        indexes.add(Map.of(
                "field_name", "embedding",
                "index_type", "FLAT",
                "metric_type", this.metricType
        ));

        // 再次确认：绝对不要给 hash 字段加索引，Milvus 会自动为主键创建索引

        Map<String, Object> payload = new HashMap<>();
        payload.put("collection_name", this.collectionName);
        payload.put("fields", fields);
        payload.put("indexes", indexes);

        return payload;
    }

    @Override
    public void initData(List<EmbeddingsItemPoWithBLOBs> embeddingsItemPos) {

        // 3. 写入数据
        if (!embeddingsItemPos.isEmpty()) {
            putData(embeddingsItemPos);
            log.info("Initialized collection {} with {} vectors", collectionName, embeddingsItemPos.size());
        }
    }

    @Override
    public void putData(List<EmbeddingsItemPoWithBLOBs> embeddingsItems) {
        // 这里 List<JsonObject> 直接兼容 Translator 返回的 Gson 对象
        List<JsonObject> milvusData = new ArrayList<>();
        for (EmbeddingsItemPoWithBLOBs item : embeddingsItems) {
            milvusData.add(Translator.translateToMilvusData(item));
        }

        if (!milvusData.isEmpty()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("collection_name", collectionName);
            payload.put("data", milvusData);

            // Gson 会自动正确序列化包含 Gson JsonObject 的 Map
            String response = sendRequest("/vectors/upsert", payload);
            log.debug("Upsert response: {}", response);
        }
    }

    @Override
    public List<EmbeddingModel.EmbeddingsQueryItem> knnRetrieval(float[] queryVector, int topK) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("collection_name", collectionName);
        payload.put("vector", queryVector);
        payload.put("top_k", topK);
        payload.put("output_fields", List.of("hash", "embedding_model", "embedding_provider", "text", "text_chunk_id"));
        payload.put("search_params", Map.of("metric_type", metricType));

        String jsonResponse = sendRequest("/vectors/search", payload);

        // 使用 Gson 反序列化为 List<JsonObject>
        Type listType = new TypeToken<List<JsonObject>>() {
        }.getType();
        List<JsonObject> searchResults = gson.fromJson(jsonResponse, listType);

        List<EmbeddingModel.EmbeddingsQueryItem> resultItems = new ArrayList<>();
        if (searchResults != null) {
            for (JsonObject hit : searchResults) {
                // 使用 Gson 的便捷方法获取类型安全的值，避免 Double 转换问题
                EmbeddingModel.EmbeddingsQueryItem item = new EmbeddingModel.EmbeddingsQueryItem()
                        .setHash(getJsonString(hit, "hash"))
                        .setScore(hit.has("score") ? hit.get("score").getAsFloat() : 0.0f)
                        .setEmbeddingModel(getJsonString(hit, "embedding_model"))
                        .setEmbeddingProvider(getJsonString(hit, "embedding_provider"))
                        .setText(getJsonString(hit, "text"))
                        .setTextChunkId(getJsonString(hit, "text_chunk_id"));

                resultItems.add(item);
            }
        }
        return resultItems;
    }

    // 辅助方法：安全获取 String，防止字段不存在或为 null
    private String getJsonString(JsonObject json, String memberName) {
        return (json.has(memberName) && !json.get(memberName).isJsonNull())
                ? json.get(memberName).getAsString()
                : null;
    }

    @Override
    public void deleteData(List<String> ids) {
        if (ids == null || ids.isEmpty()) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("collection_name", collectionName);
        payload.put("ids", ids);

        String response = sendRequest("/vectors/delete", payload);
        log.info("Delete response: {}", response);
    }

    private String sendRequest(String path, Object requestBody) {
        try {
            // 使用 Gson 进行序列化
            String jsonBody = gson.toJson(requestBody);

            return webClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(jsonBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class)
                                    .flatMap(errorBody -> Mono.error(new RuntimeException("Milvus Python Service Error [" + response.statusCode() + "]: " + errorBody)))
                    )
                    .bodyToMono(String.class)
                    .block();

        } catch (Exception e) {
            log.error("Failed to connect to Milvus Python Service at {}", path, e);
            throw new RuntimeException("Vector DB Connection Failed", e);
        }
    }
}