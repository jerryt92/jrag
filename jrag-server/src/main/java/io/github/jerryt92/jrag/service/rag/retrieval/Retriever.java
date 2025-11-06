package io.github.jerryt92.jrag.service.rag.retrieval;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.jerryt92.jrag.mapper.mgb.FilePoMapper;
import io.github.jerryt92.jrag.mapper.mgb.TextChunkPoMapper;
import io.github.jerryt92.jrag.model.ChatRequestDto;
import io.github.jerryt92.jrag.model.EmbeddingModel;
import io.github.jerryt92.jrag.model.MessageDto;
import io.github.jerryt92.jrag.model.RagInfoDto;
import io.github.jerryt92.jrag.model.Translator;
import io.github.jerryt92.jrag.po.mgb.FilePo;
import io.github.jerryt92.jrag.po.mgb.FilePoExample;
import io.github.jerryt92.jrag.po.mgb.TextChunkPo;
import io.github.jerryt92.jrag.po.mgb.TextChunkPoExample;
import io.github.jerryt92.jrag.service.embedding.EmbeddingService;
import io.github.jerryt92.jrag.service.rag.vdb.VectorDatabaseService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    public Retriever(EmbeddingService embeddingService, VectorDatabaseService vectorDatabaseService, TextChunkPoMapper textChunkPoMapper, FilePoMapper filePoMapper) {
        this.embeddingService = embeddingService;
        this.vectorDatabaseService = vectorDatabaseService;
        this.textChunkPoMapper = textChunkPoMapper;
        this.filePoMapper = filePoMapper;
    }

    /**
     * 根据用户输入检索数据，生成一个系统提示词，放入上下文中，并返回引用文件
     *
     * @param chatRequest
     * @return
     */
    public List<RagInfoDto> retrieveQuery(ChatRequestDto chatRequest) {
        // 相似度匹配
        String queryContent = chatRequest.getMessages().get(chatRequest.getMessages().size() - 1).getContent();
        List<EmbeddingModel.EmbeddingsQueryItem> embeddingsQueryItems = similarityRetrieval(
                queryContent,
                5,
                0.7f
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
            List<String> srcFileIds = new ArrayList<>();
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
            for (EmbeddingModel.EmbeddingsQueryItem embeddingsQueryItem : embeddingsQueryItems) {
                JSONObject ragData = new JSONObject();
                ragData.put("title", embeddingsQueryItem.getText());
                ragData.put("content", textChunkMap.get(embeddingsQueryItem.getTextChunkId()).getTextChunk());
                ragDataArray.add(ragData);
            }
            Map<String, FilePo> fileMap = new HashMap<>();
            if (!srcFileIds.isEmpty()) {
                FilePoExample filePoExample = new FilePoExample();
                filePoExample.createCriteria().andIdIn(srcFileIds);
                fileMap = filePoMapper.selectByExample(filePoExample)
                        .stream().collect(Collectors.toMap(FilePo::getId, filePo -> filePo, (v1, v2) -> v1));
            }
            MessageDto systemPromptMessage = new MessageDto();
            systemPromptMessage.setRole(MessageDto.RoleEnum.SYSTEM);
            systemPromptMessage.setContent(
                    "The following is the relevant information retrieved for the user's question: \"" + queryContent + "\". Please answer the user's question based on this information. The details are as follows:"
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

    public List<EmbeddingModel.EmbeddingsQueryItem> similarityRetrieval(String queryText, int topK, Float minCosScore) {
        // 向量化
        EmbeddingModel.EmbeddingsRequest embeddingsRequest = new EmbeddingModel.EmbeddingsRequest()
                .setInput(Collections.singletonList(queryText));
        EmbeddingModel.EmbeddingsResponse embed = embeddingService.embed(embeddingsRequest);
        if (embed.getData().isEmpty()) {
            return Collections.emptyList();
        }
        // 相似度匹配
        return vectorDatabaseService.knnSearchByCos(embed.getData().get(0).getEmbeddings(), topK, minCosScore);
    }
}
