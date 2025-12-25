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
        // 向量化
        EmbeddingModel.EmbeddingsRequest embeddingsRequest = new EmbeddingModel.EmbeddingsRequest()
                .setInput(Collections.singletonList(queryText));
        EmbeddingModel.EmbeddingsResponse embed = embeddingService.embed(embeddingsRequest);
        if (embed.getData().isEmpty()) {
            return Collections.emptyList();
        }
        List<EmbeddingModel.EmbeddingsQueryItem> embeddingsQueryItems = vectorDatabaseService.knnRetrieval(embed.getData().getFirst().getEmbeddings(), topK);
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
        // 向量化
        EmbeddingModel.EmbeddingsRequest embeddingsRequest = new EmbeddingModel.EmbeddingsRequest()
                .setInput(Collections.singletonList(queryText));
        EmbeddingModel.EmbeddingsResponse embed = embeddingService.embed(embeddingsRequest);
        if (embed.getData().isEmpty()) {
            return retrieveResult;
        }
        List<EmbeddingModel.EmbeddingsQueryItem> embeddingsQueryItems = vectorDatabaseService.knnRetrieval(embed.getData().getFirst().getEmbeddings(), topK);
        List<String> textChunkIds = embeddingsQueryItems.stream().map(EmbeddingModel.EmbeddingsQueryItem::getTextChunkId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        TextChunkPoExample textChunkPoExample = new TextChunkPoExample();
        textChunkPoExample.createCriteria().andIdIn(textChunkIds);
        List<TextChunkPo> textChunkPos = textChunkPoMapper.selectByExampleWithBLOBs(textChunkPoExample);
        Map<String, TextChunkPo> textChunkMap = textChunkPos.stream().collect(Collectors.toMap(TextChunkPo::getId, textChunkPo -> textChunkPo));
        for (EmbeddingModel.EmbeddingsQueryItem embeddingsQueryItem : embeddingsQueryItems) {
            String calculateExpressionResult = MathCalculatorUtil.calculateExpression(embeddingsQueryItem.getScore() + propertiesService.getProperty(PropertiesService.RETRIEVE_METRIC_SCORE_COMPARE_EXPR));
            retrieveResult.add(Translator.translateToEmbeddingsQueryItemDto(embeddingsQueryItem, textChunkMap.get(embeddingsQueryItem.getTextChunkId()), !"true".equals(calculateExpressionResult), KnowledgeRetrieveItemDto.MetricTypeEnum.valueOf(propertiesService.getProperty(PropertiesService.RETRIEVE_METRIC_TYPE)), embeddingService.getDimension()));
        }
        return retrieveResult;
    }
}
