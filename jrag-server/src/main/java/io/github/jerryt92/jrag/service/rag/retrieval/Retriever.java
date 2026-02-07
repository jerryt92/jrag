package io.github.jerryt92.jrag.service.rag.retrieval;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.jerryt92.jrag.mapper.mgb.FilePoMapper;
import io.github.jerryt92.jrag.mapper.mgb.TextChunkPoMapper;
import io.github.jerryt92.jrag.model.ChatModel;
import io.github.jerryt92.jrag.model.EmbeddingModel;
import io.github.jerryt92.jrag.model.KnowledgeRetrieveItemDto;
import io.github.jerryt92.jrag.model.RagInfoDto;
import io.github.jerryt92.jrag.model.Translator;
import io.github.jerryt92.jrag.po.mgb.FilePo;
import io.github.jerryt92.jrag.po.mgb.FilePoExample;
import io.github.jerryt92.jrag.po.mgb.TextChunkPo;
import io.github.jerryt92.jrag.po.mgb.TextChunkPoExample;
import io.github.jerryt92.jrag.service.PropertiesService;
import io.github.jerryt92.jrag.service.embedding.EmbeddingService;
import io.github.jerryt92.jrag.service.rag.vdb.VectorDatabaseService;
import io.github.jerryt92.jrag.utils.MathCalculatorUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 检索器
 */
@Service
public class Retriever {
    private final EmbeddingService embeddingService;
    private final VectorDatabaseService vectorDatabaseService;
    private final TextChunkPoMapper textChunkPoMapper;
    private final FilePoMapper filePoMapper;
    private final PropertiesService propertiesService;

    public Retriever(EmbeddingService embeddingService, VectorDatabaseService vectorDatabaseService, TextChunkPoMapper textChunkPoMapper, FilePoMapper filePoMapper, PropertiesService propertiesService) {
        this.embeddingService = embeddingService;
        this.vectorDatabaseService = vectorDatabaseService;
        this.textChunkPoMapper = textChunkPoMapper;
        this.filePoMapper = filePoMapper;
        this.propertiesService = propertiesService;
    }

    /**
     * 根据用户输入检索数据，生成一个系统提示词，放入上下文中，并返回引用文件
     *
     * @param chatRequest
     * @return
     */
    public List<RagInfoDto> retrieveQuery(ChatModel.ChatRequest chatRequest) {
        // 相似度匹配
        // 找到最后一个来自USER的内容
        String queryContent = null;
        for (int i = chatRequest.getMessages().size() - 1; i >= 0; i--) {
            ChatModel.Message message = chatRequest.getMessages().get(i);
            if (ChatModel.Role.USER.equals(message.getRole())) {
                queryContent = message.getContent();
                break;
            }
        }
        List<EmbeddingModel.EmbeddingsQueryItem> embeddingsQueryItems = similarityRetrieval(
                queryContent,
                KnowledgeRetrieveItemDto.MetricTypeEnum.valueOf(propertiesService.getProperty(PropertiesService.RETRIEVE_METRIC_TYPE)),
                Integer.parseInt(propertiesService.getProperty(PropertiesService.RETRIEVE_TOP_K)),
                propertiesService.getProperty(PropertiesService.RETRIEVE_METRIC_SCORE_COMPARE_EXPR)
        );
        List<RagInfoDto> retrieveResult = new ArrayList<>();
        if (!embeddingsQueryItems.isEmpty()) {
            // 查询文本块
            HashSet<String> textChunkIds = new HashSet<>();
            for (EmbeddingModel.EmbeddingsQueryItem embeddingsQueryItem : embeddingsQueryItems) {
                textChunkIds.add(embeddingsQueryItem.getTextChunkId());
            }
            TextChunkPoExample textChunkPoExample = new TextChunkPoExample();
            textChunkPoExample.createCriteria().andIdIn(new ArrayList<>(textChunkIds));
            Map<String, TextChunkPo> textChunkMap = new HashMap<>();
            List<Integer> srcFileIds = new ArrayList<>();
            List<TextChunkPo> textChunkPoList = textChunkPoMapper.selectByExampleWithBLOBs(textChunkPoExample);
            int totalChar = 0;
            for (TextChunkPo textChunkPo : textChunkPoList) {
                textChunkMap.put(textChunkPo.getId(), textChunkPo);
                if (textChunkPo.getSrcFileId() != null && !srcFileIds.contains(textChunkPo.getSrcFileId())) {
                    srcFileIds.add(textChunkPo.getSrcFileId());
                }
                totalChar += textChunkPo.getTextChunk().length();
                if (totalChar > 8192) {
                    break;
                }
            }
            JSONArray ragDataArray = new JSONArray();
            int num = 1;
            for (EmbeddingModel.EmbeddingsQueryItem embeddingsQueryItem : embeddingsQueryItems) {
                TextChunkPo textChunkPo = textChunkMap.get(embeddingsQueryItem.getTextChunkId());
                if (textChunkPo != null) {
                    JSONObject ragData = new JSONObject();
                    ragData.put("content-" + num, textChunkMap.get(embeddingsQueryItem.getTextChunkId()).getTextChunk());
                    ragDataArray.add(ragData);
                    num++;
                }
            }
            Map<Integer, FilePo> fileMap = new HashMap<>();
            if (!srcFileIds.isEmpty()) {
                FilePoExample filePoExample = new FilePoExample();
                filePoExample.createCriteria().andIdIn(srcFileIds);
                fileMap = filePoMapper.selectByExample(filePoExample)
                        .stream().collect(Collectors.toMap(FilePo::getId, filePo -> filePo, (v1, v2) -> v1));
            }
            ChatModel.Message systemPromptMessage = new ChatModel.Message()
                    .setRole(ChatModel.Role.SYSTEM)
                    .setContent(
                            "The user's question is : \"" + queryContent + "\".\nThe contents (each part of \"content-x\" must be complete) :"
                                    + ragDataArray
                    );
            chatRequest.getMessages().add(systemPromptMessage);
            for (TextChunkPo textChunkPo : textChunkPoList) {
                RagInfoDto ragInfoDto = new RagInfoDto();
                ragInfoDto.setTextChunkId(textChunkPo.getId());
                ragInfoDto.setTextChunk(textChunkPo.getTextChunk());
                FilePo filePo = fileMap.get(textChunkPo.getSrcFileId());
                if (filePo != null) {
                    ragInfoDto.setSrcFile(Translator.translateToFileDto(Translator.translateToFileBo(filePo)));
                }
                retrieveResult.add(ragInfoDto);
            }
        }
        return retrieveResult;
    }

    public List<EmbeddingModel.EmbeddingsQueryItem> similarityRetrieval(String queryText, KnowledgeRetrieveItemDto.MetricTypeEnum metricType, int topK, String metricScoreCompareExpr) {
        if (StringUtils.isBlank(queryText)) {
            return Collections.emptyList();
        }
        // 向量化
        EmbeddingModel.EmbeddingsRequest embeddingsRequest = new EmbeddingModel.EmbeddingsRequest()
                .setInput(Collections.singletonList(queryText));
        EmbeddingModel.EmbeddingsResponse embed = embeddingService.embed(embeddingsRequest);
        if (embed.getData().isEmpty()) {
            return Collections.emptyList();
        }
        float[] weights = resolveRetrieveWeights();
        List<EmbeddingModel.EmbeddingsQueryItem> embeddingsQueryItems = vectorDatabaseService.hybridRetrieval(
                queryText,
                embed.getData().getFirst().getEmbeddings(),
                topK,
                metricType == null ? null : metricType.name(),
                weights[0],
                weights[1]
        );
        String referenceText = resolveReferenceText(embeddingsQueryItems, Collections.emptyMap(), queryText);
        Float[] referenceMaxScores = resolveReferenceMaxScores(referenceText, metricType == null ? null : metricType.name(), weights);
        Float denseScoreMax = referenceMaxScores[0];
        Float sparseScoreMax = resolveMaxSparseScore(embeddingsQueryItems);
        Float l2MaxScore = metricType == KnowledgeRetrieveItemDto.MetricTypeEnum.L2
                ? resolveMaxDenseScore(embeddingsQueryItems)
                : null;
        for (EmbeddingModel.EmbeddingsQueryItem embeddingsQueryItem : embeddingsQueryItems) {
            float densePercent = normalizeDenseScore(embeddingsQueryItem.getDenseScore(), metricType, denseScoreMax, l2MaxScore);
            float sparsePercent = normalizeSparseScore(embeddingsQueryItem.getSparseScore(), sparseScoreMax);
            float hybridPercent = clampPercent(densePercent * weights[0] + sparsePercent * weights[1]);
            embeddingsQueryItem.setDenseScore(densePercent)
                    .setSparseScore(sparsePercent)
                    .setHybridScore(hybridPercent)
                    .setScore(hybridPercent);
        }
        List<EmbeddingModel.EmbeddingsQueryItem> result = new ArrayList<>();
        for (EmbeddingModel.EmbeddingsQueryItem embeddingsQueryItem : embeddingsQueryItems) {
            String calculateExpressionResult = MathCalculatorUtil.calculateExpression(embeddingsQueryItem.getScore() + metricScoreCompareExpr);
            if (StringUtils.isBlank(metricScoreCompareExpr) || "true".equals(calculateExpressionResult)) {
                result.add(embeddingsQueryItem);
            }
        }
        return result;
    }

    public List<KnowledgeRetrieveItemDto> retrieveKnowledge(String queryText, Integer topK) {
        List<KnowledgeRetrieveItemDto> retrieveResult = new ArrayList<>();
        if (StringUtils.isBlank(queryText)) {
            return retrieveResult;
        }
        String metricTypeStr = propertiesService.getProperty(PropertiesService.RETRIEVE_METRIC_TYPE);
        KnowledgeRetrieveItemDto.MetricTypeEnum metricType = KnowledgeRetrieveItemDto.MetricTypeEnum.valueOf(metricTypeStr);
        // 向量化
        EmbeddingModel.EmbeddingsRequest embeddingsRequest = new EmbeddingModel.EmbeddingsRequest()
                .setInput(Collections.singletonList(queryText));
        EmbeddingModel.EmbeddingsResponse embed = embeddingService.embed(embeddingsRequest);
        if (embed.getData().isEmpty()) {
            return retrieveResult;
        }
        float[] weights = resolveRetrieveWeights();
        List<EmbeddingModel.EmbeddingsQueryItem> embeddingsQueryItems = vectorDatabaseService.hybridRetrieval(
                queryText,
                embed.getData().getFirst().getEmbeddings(),
                topK,
                metricTypeStr,
                weights[0],
                weights[1]
        );
        List<String> textChunkIds = embeddingsQueryItems.stream().map(EmbeddingModel.EmbeddingsQueryItem::getTextChunkId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        TextChunkPoExample textChunkPoExample = new TextChunkPoExample();
        textChunkPoExample.createCriteria().andIdIn(textChunkIds);
        List<TextChunkPo> textChunkPos = textChunkIds.isEmpty() ? Collections.emptyList() : textChunkPoMapper.selectByExampleWithBLOBs(textChunkPoExample);
        Map<String, TextChunkPo> textChunkMap = textChunkPos.stream().collect(Collectors.toMap(TextChunkPo::getId, textChunkPo -> textChunkPo));
        String referenceText = resolveReferenceText(embeddingsQueryItems, textChunkMap, queryText);
        Float[] referenceMaxScores = resolveReferenceMaxScores(referenceText, metricTypeStr, weights);
        Float denseScoreMax = referenceMaxScores[0];
        Float sparseScoreMax = resolveMaxSparseScore(embeddingsQueryItems);
        Float l2MaxScore = metricType == null
                ? null
                : (metricType == KnowledgeRetrieveItemDto.MetricTypeEnum.L2 ? resolveMaxDenseScore(embeddingsQueryItems) : null);
        for (EmbeddingModel.EmbeddingsQueryItem embeddingsQueryItem : embeddingsQueryItems) {
            float densePercent = normalizeDenseScore(embeddingsQueryItem.getDenseScore(), metricType, denseScoreMax, l2MaxScore);
            float sparsePercent = normalizeSparseScore(embeddingsQueryItem.getSparseScore(), sparseScoreMax);
            float hybridPercent = clampPercent(densePercent * weights[0] + sparsePercent * weights[1]);
            embeddingsQueryItem.setDenseScore(densePercent)
                    .setSparseScore(sparsePercent)
                    .setHybridScore(hybridPercent)
                    .setScore(hybridPercent);
            String calculateExpressionResult = MathCalculatorUtil.calculateExpression(embeddingsQueryItem.getScore() + propertiesService.getProperty(PropertiesService.RETRIEVE_METRIC_SCORE_COMPARE_EXPR));
            retrieveResult.add(Translator.translateToEmbeddingsQueryItemDto(embeddingsQueryItem, textChunkMap.get(embeddingsQueryItem.getTextChunkId()), !"true".equals(calculateExpressionResult), metricType, embeddingService.getDimension()));
        }
        return retrieveResult;
    }

    private float[] resolveRetrieveWeights() {
        float denseWeight = parseWeight(propertiesService.getProperty(PropertiesService.RETRIEVE_DENSE_WEIGHT), 0.5f);
        float sparseWeight = parseWeight(propertiesService.getProperty(PropertiesService.RETRIEVE_SPARSE_WEIGHT), 0.5f);
        denseWeight = clampWeight(denseWeight);
        sparseWeight = clampWeight(sparseWeight);
        float total = denseWeight + sparseWeight;
        if (total <= 0f) {
            return new float[]{1f, 0f};
        }
        return new float[]{denseWeight / total, sparseWeight / total};
    }

    private float parseWeight(String value, float fallback) {
        if (StringUtils.isBlank(value)) {
            return fallback;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private float clampWeight(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }

    private String resolveReferenceText(List<EmbeddingModel.EmbeddingsQueryItem> embeddingsQueryItems,
                                        Map<String, TextChunkPo> textChunkMap,
                                        String fallbackText) {
        if (embeddingsQueryItems == null || embeddingsQueryItems.isEmpty()) {
            return fallbackText;
        }
        EmbeddingModel.EmbeddingsQueryItem firstItem = embeddingsQueryItems.get(0);
        if (textChunkMap != null && firstItem != null && StringUtils.isNotBlank(firstItem.getTextChunkId())) {
            TextChunkPo textChunkPo = textChunkMap.get(firstItem.getTextChunkId());
            if (textChunkPo != null && StringUtils.isNotBlank(textChunkPo.getTextChunk())) {
                return textChunkPo.getTextChunk();
            }
        }
        if (firstItem != null && StringUtils.isNotBlank(firstItem.getText())) {
            return firstItem.getText();
        }
        return fallbackText;
    }

    private Float[] resolveReferenceMaxScores(String referenceText, String metricType, float[] weights) {
        if (StringUtils.isBlank(referenceText)) {
            return new Float[]{null, null};
        }
        EmbeddingModel.EmbeddingsRequest embeddingsRequest = new EmbeddingModel.EmbeddingsRequest()
                .setInput(Collections.singletonList(referenceText));
        EmbeddingModel.EmbeddingsResponse embed = embeddingService.embed(embeddingsRequest);
        if (embed.getData().isEmpty()) {
            return new Float[]{null, null};
        }
        List<EmbeddingModel.EmbeddingsQueryItem> referenceItems = vectorDatabaseService.hybridRetrieval(
                referenceText,
                embed.getData().getFirst().getEmbeddings(),
                1,
                metricType,
                weights[0],
                weights[1]
        );
        if (referenceItems.isEmpty()) {
            return new Float[]{null, null};
        }
        EmbeddingModel.EmbeddingsQueryItem referenceItem = referenceItems.get(0);
        return new Float[]{referenceItem.getDenseScore(), referenceItem.getSparseScore()};
    }

    private float normalizeDenseScore(Float value, KnowledgeRetrieveItemDto.MetricTypeEnum metricType, Float denseMax, Float l2Max) {
        if (value == null) {
            return 0f;
        }
        if (metricType == null) {
            // 未知指标时，按最大值线性归一化到 0-100
            return normalizeByMax(value, denseMax);
        }
        switch (metricType) {
            case IP:
            case COSINE:
                // IP/COSINE 原始范围 [-1, 1]，线性映射到 [0, 100]
                return clampPercent(((value + 1f) / 2f) * 100f);
            case JACCARD:
                // JACCARD 原始范围 [0, 1]，线性映射到 [0, 100]
                return clampPercent(value * 100f);
            case L2:
            default:
                // L2 距离越小越相似，使用指数衰减并确保最远距离归一为 0
                return normalizeL2Score(value, l2Max);
        }
    }

    private float normalizeSparseScore(Float value, Float sparseMax) {
        // 稀疏向量（BM25）以当前结果集中最大分数为基准，线性归一化到 0-100
        return normalizeByMax(value, sparseMax);
    }

    private float normalizeByMax(Float value, Float maxValue) {
        // 线性归一化：value / max * 100，max 为空或 <=0 时返回 0
        if (value == null) {
            return 0f;
        }
        if (maxValue == null || maxValue <= 0f) {
            return 0f;
        }
        return clampPercent((value / maxValue) * 100f);
    }

    private float normalizeL2Score(Float value, Float maxValue) {
        // 指数衰减归一化：最远距离为 0 分，距离越小分数越高
        if (value == null) {
            return 0f;
        }
        if (maxValue == null || maxValue <= 0f) {
            return value <= 0f ? 100f : 0f;
        }
        float ratio = value / maxValue;
        if (ratio < 0f) {
            ratio = 0f;
        }
        float expMax = (float) Math.exp(-1f);
        float expValue = (float) Math.exp(-ratio);
        float normalized = (expValue - expMax) / (1f - expMax);
        return clampPercent(normalized * 100f);
    }

    private Float resolveMaxDenseScore(List<EmbeddingModel.EmbeddingsQueryItem> embeddingsQueryItems) {
        if (embeddingsQueryItems == null || embeddingsQueryItems.isEmpty()) {
            return null;
        }
        Float maxScore = null;
        for (EmbeddingModel.EmbeddingsQueryItem item : embeddingsQueryItems) {
            Float score = item == null ? null : item.getDenseScore();
            if (score != null) {
                maxScore = maxScore == null ? score : Math.max(maxScore, score);
            }
        }
        return maxScore;
    }

    private Float resolveMaxSparseScore(List<EmbeddingModel.EmbeddingsQueryItem> embeddingsQueryItems) {
        if (embeddingsQueryItems == null || embeddingsQueryItems.isEmpty()) {
            return null;
        }
        Float maxScore = null;
        for (EmbeddingModel.EmbeddingsQueryItem item : embeddingsQueryItems) {
            Float score = item == null ? null : item.getSparseScore();
            if (score != null) {
                maxScore = maxScore == null ? score : Math.max(maxScore, score);
            }
        }
        return maxScore;
    }

    private float clampPercent(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 100f) {
            return 100f;
        }
        return value;
    }
}
