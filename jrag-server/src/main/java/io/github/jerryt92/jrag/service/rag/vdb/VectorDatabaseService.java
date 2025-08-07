package io.github.jerryt92.jrag.service.rag.vdb;

import io.github.jerryt92.jrag.model.EmbeddingModel;
import io.github.jerryt92.jrag.po.mgb.EmbeddingsItemPoWithBLOBs;

import java.util.List;

public interface VectorDatabaseService {
    void init();

    /**
     * 根据余弦相似度近似近邻（ANN）搜索
     *
     * @param queryVector 查询向量
     * @param topK        表示返回最相似的K个向量
     * @param minCosScore 表示返回最相似的向量中，最相似的向量与查询向量的余弦相似度评分，结果必须大于等于该值，范围[0, 1]
     */
    List<EmbeddingModel.EmbeddingsQueryItem> knnSearchByCos(float[] queryVector, int topK, Float minCosScore);

    void putData(List<EmbeddingsItemPoWithBLOBs> embeddingsItems);
}
