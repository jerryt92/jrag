package io.github.jerryt92.jrag.service.rag.vdb;

import io.github.jerryt92.jrag.model.EmbeddingModel;
import io.github.jerryt92.jrag.po.mgb.EmbeddingsItemPoWithBLOBs;

import java.util.List;

public interface VectorDatabaseService {
    void reBuildVectorDatabase(int dimension, String metricTypeStr);

    void initData(List<EmbeddingsItemPoWithBLOBs> embeddingsItemPos);

    /**
     * 根据余弦相似度近似近邻（ANN）搜索
     *
     * @param queryVector 查询向量
     * @param topK        表示返回最相似的K个向量
     */
    List<EmbeddingModel.EmbeddingsQueryItem> knnRetrieval(float[] queryVector, int topK);

    void putData(List<EmbeddingsItemPoWithBLOBs> embeddingsItems);

    void deleteData(List<String> ids);
}
