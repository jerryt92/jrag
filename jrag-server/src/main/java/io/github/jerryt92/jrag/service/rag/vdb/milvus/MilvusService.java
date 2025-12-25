package io.github.jerryt92.jrag.service.rag.vdb.milvus;

import com.google.gson.JsonObject;
import io.github.jerryt92.jrag.model.EmbeddingModel;
import io.github.jerryt92.jrag.model.Translator;
import io.github.jerryt92.jrag.po.mgb.EmbeddingsItemPoWithBLOBs;
import io.github.jerryt92.jrag.service.rag.vdb.VectorDatabaseService;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.GetLoadStateReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.SearchResp;
import io.milvus.v2.service.vector.response.UpsertResp;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public class MilvusService implements VectorDatabaseService {
    private final String clusterEndpoint;
    private final String collectionName;
    private final String token;
    private IndexParam.MetricType metricType;
    private MilvusClientV2 client;

    public MilvusService(
            String clusterEndpoint,
            String collectionName,
            String token
    ) {
        this.clusterEndpoint = clusterEndpoint;
        this.collectionName = collectionName;
        this.token = token;
    }

    @Override
    public void reBuildVectorDatabase(int dimension, String metricTypeStr) {
        metricType = IndexParam.MetricType.valueOf(metricTypeStr);
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri(clusterEndpoint)
                .token(token)
                .build();
        this.client = new MilvusClientV2(connectConfig);
        // 检查Collection是否存在
        HasCollectionReq hasCollectionReq = HasCollectionReq.builder()
                .collectionName(collectionName)
                .build();
        if (client.hasCollection(hasCollectionReq)) {
            // 删除Collection
            DropCollectionReq dropCollectionReq = DropCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            client.dropCollection(dropCollectionReq);
        }
        // Create schema
        CreateCollectionReq.CollectionSchema schema = client.createSchema();
        // 3.2 Add fields to schema
        schema.addField(AddFieldReq.builder()
                .fieldName("hash")
                .dataType(DataType.VarChar)
                .maxLength(40)
                .isPrimaryKey(true)
                .autoID(false)
                .description("嵌入数据的哈希值（SHA-1），同时也作为唯一标识符")
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("embedding_model")
                .dataType(DataType.VarChar)
                .maxLength(128)
                .description("嵌入模型名称")
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("embedding_provider")
                .dataType(DataType.VarChar)
                .maxLength(128)
                .description("嵌入模型提供商名称")
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("text")
                .dataType(DataType.VarChar)
                .maxLength(4096)
                .description("嵌入文本")
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("embedding")
                .dataType(DataType.FloatVector)
                .dimension(dimension)
                .description("嵌入向量")
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("text_chunk_id")
                .dataType(DataType.VarChar)
                .maxLength(40)
                .description("文本块ID")
                .build());
        // Prepare index parameters
        IndexParam indexParamForIdField = IndexParam.builder()
                .fieldName("hash")
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .build();

        IndexParam indexParamForVectorField = IndexParam.builder()
                .fieldName("embedding")
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(metricType)
                .build();

        List<IndexParam> indexParams = new ArrayList<>();
        indexParams.add(indexParamForIdField);
        indexParams.add(indexParamForVectorField);
        // Create a collection with schema and index parameters
        CreateCollectionReq customizedSetupReq = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .indexParams(indexParams)
                .build();

        client.createCollection(customizedSetupReq);

        // Get load state of the collection
        GetLoadStateReq customSetupLoadStateReq = GetLoadStateReq.builder()
                .collectionName(collectionName)
                .build();

        Boolean loaded = client.getLoadState(customSetupLoadStateReq);
        log.info("Collection {} is loaded: {}", collectionName, loaded);
    }

    @Override
    public void initData(List<EmbeddingsItemPoWithBLOBs> embeddingsItemPos) {
        // 从关系型数据库中查询全部嵌入数据
        List<JsonObject> data = new ArrayList<>();
        for (EmbeddingsItemPoWithBLOBs embeddingsItemPo : embeddingsItemPos) {
            data.add(Translator.translateToMilvusData(embeddingsItemPo));
        }
        if (!data.isEmpty()) {
            InsertReq insertReq = InsertReq.builder()
                    .collectionName(collectionName)
                    .data(data)
                    .build();
            InsertResp insertResp = client.insert(insertReq);
            log.info("Inserted {} vectors into collection {}", insertResp.getInsertCnt(), collectionName);
        }
    }

    @Override
    public void putData(List<EmbeddingsItemPoWithBLOBs> embeddingsItems) {
        List<JsonObject> milvusData = new ArrayList<>();
        for (EmbeddingsItemPoWithBLOBs embeddingsItemPo : embeddingsItems) {
            milvusData.add(Translator.translateToMilvusData(embeddingsItemPo));
        }
        if (!milvusData.isEmpty()) {
            UpsertReq upsertReq = UpsertReq.builder()
                    .collectionName(collectionName)
                    .data(milvusData)
                    .build();
            UpsertResp upsertResp = client.upsert(upsertReq);
            log.info("Upserted {} vectors into collection {}", upsertResp.getUpsertCnt(), collectionName);
        }
    }

    @Override
    public List<EmbeddingModel.EmbeddingsQueryItem> knnRetrieval(float[] queryVector, int topK) {
        FloatVec floatVec = new FloatVec(queryVector);
        SearchReq searchReq = SearchReq.builder()
                .collectionName(collectionName)
                .data(List.of(floatVec))
                .topK(topK)
                .searchParams(Map.of("metric_type", metricType.toString()))
                .outputFields(List.of("hash", "embedding_model", "embedding_provider", "text", "text_chunk_id"))
                .build();
        SearchResp searchResp = client.search(searchReq);
        List<List<SearchResp.SearchResult>> results = searchResp.getSearchResults();
        List<SearchResp.SearchResult> searchResults = results.isEmpty() ? Collections.emptyList() : results.get(0);
        List<EmbeddingModel.EmbeddingsQueryItem> embeddingsQueryItems = new ArrayList<>();
        for (SearchResp.SearchResult searchResult : searchResults) {
            EmbeddingModel.EmbeddingsQueryItem embeddingsQueryItem = new EmbeddingModel.EmbeddingsQueryItem()
                    .setHash(((String) searchResult.getEntity().get("hash")))
                    .setScore(searchResult.getScore())
                    .setEmbeddingModel((String) searchResult.getEntity().get("embedding_model"))
                    .setEmbeddingProvider((String) searchResult.getEntity().get("embedding_provider"))
                    .setText((String) searchResult.getEntity().get("text"))
                    .setTextChunkId(searchResult.getEntity().get("text_chunk_id").toString());
            embeddingsQueryItems.add(embeddingsQueryItem);
        }
        return embeddingsQueryItems;
    }

    @Override
    public void deleteData(List<String> ids) {
        DeleteResp deleteResp = client.delete(DeleteReq.builder()
                .collectionName(collectionName)
                .ids(new ArrayList<>(ids))
                .build());
        log.info("Deleted {} vectors from collection {}", deleteResp.getDeleteCnt(), collectionName);
    }
}
