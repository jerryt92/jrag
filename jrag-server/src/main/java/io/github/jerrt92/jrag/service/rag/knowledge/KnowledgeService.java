package io.github.jerrt92.jrag.service.rag.knowledge;

import io.github.jerrt92.jrag.mapper.mgb.EmbeddingsItemPoMapper;
import io.github.jerrt92.jrag.mapper.mgb.TextChunkPoMapper;
import io.github.jerrt92.jrag.model.EmbeddingModel;
import io.github.jerrt92.jrag.model.KnowledgeAddDto;
import io.github.jerrt92.jrag.model.Translator;
import io.github.jerrt92.jrag.po.mgb.EmbeddingsItemPo;
import io.github.jerrt92.jrag.po.mgb.EmbeddingsItemPoExample;
import io.github.jerrt92.jrag.po.mgb.EmbeddingsItemPoWithBLOBs;
import io.github.jerrt92.jrag.po.mgb.TextChunkPo;
import io.github.jerrt92.jrag.po.mgb.TextChunkPoExample;
import io.github.jerrt92.jrag.service.embedding.EmbeddingService;
import io.github.jerrt92.jrag.service.rag.vdb.VectorDatabaseService;
import io.github.jerrt92.jrag.utils.MDUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {
    private final EmbeddingService embeddingService;
    private final TextChunkPoMapper textChunkPoMapper;
    private final EmbeddingsItemPoMapper embeddingsItemPoMapper;
    private final VectorDatabaseService vectorDatabaseService;

    public KnowledgeService(EmbeddingService embeddingService, TextChunkPoMapper textChunkPoMapper, EmbeddingsItemPoMapper embeddingsItemPoMapper, VectorDatabaseService vectorDatabaseService) {
        this.embeddingService = embeddingService;
        this.textChunkPoMapper = textChunkPoMapper;
        this.embeddingsItemPoMapper = embeddingsItemPoMapper;
        this.vectorDatabaseService = vectorDatabaseService;
    }

    public void putKnowledge(List<KnowledgeAddDto> knowledgeAddDtoList) {
        Map<String, String> outlineMap = new HashMap<>();
        for (KnowledgeAddDto knowledgeAddDto : knowledgeAddDtoList) {
            for (String outline : knowledgeAddDto.getOutline()) {
                try {
                    outlineMap.put(MDUtil.getMessageDigest(outline.getBytes(StandardCharsets.UTF_8), MDUtil.MdAlgorithm.SHA1), outline);
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        TextChunkPoExample textChunkPoExample = new TextChunkPoExample();
        textChunkPoExample.createCriteria().andIdIn(knowledgeAddDtoList.stream().map(KnowledgeAddDto::getId).collect(Collectors.toList()));
        HashSet<String> existingTextChunkIds = new HashSet<>();
        List<TextChunkPo> textChunkPoList = textChunkPoMapper.selectByExampleWithBLOBs(textChunkPoExample);
        if (!CollectionUtils.isEmpty(textChunkPoList)) {
            textChunkPoList.forEach(textChunkPo -> existingTextChunkIds.add(textChunkPo.getId()));
        }
        // 嵌入数据
        EmbeddingModel.EmbeddingsResponse embed = embeddingService.embed(new EmbeddingModel.EmbeddingsRequest().setInput(new ArrayList<>(outlineMap.values())));
        List<String> allEmbedHashcode = new ArrayList<>();
        HashMap<String, EmbeddingModel.EmbeddingsItem> outlineToEmbedMap = new HashMap<>();
        for (EmbeddingModel.EmbeddingsItem embeddingsItem : embed.getData()) {
            try {
                allEmbedHashcode.add(MDUtil.getMessageDigest(embeddingsItem.getText().getBytes(StandardCharsets.UTF_8), MDUtil.MdAlgorithm.SHA1));
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
            outlineToEmbedMap.put(embeddingsItem.getText(), embeddingsItem);
        }
        EmbeddingsItemPoExample embeddingsItemPoExample = new EmbeddingsItemPoExample();
        embeddingsItemPoExample.createCriteria().andHashIn(allEmbedHashcode);
        HashSet<String> existingEmbedHashcode = new HashSet<>();
        List<EmbeddingsItemPo> embeddingsItemPos = embeddingsItemPoMapper.selectByExample(embeddingsItemPoExample);
        if (!CollectionUtils.isEmpty(embeddingsItemPos)) {
            embeddingsItemPos.forEach(embeddingsItemPo -> existingEmbedHashcode.add(embeddingsItemPo.getHash()));
        }
        List<EmbeddingsItemPoWithBLOBs> insertEmbeddingsItemPoList = new ArrayList<>();
        List<EmbeddingsItemPoWithBLOBs> updateEmbeddingsItemPoList = new ArrayList<>();
        List<TextChunkPo> insertTextChunkPoList = new ArrayList<>();
        List<TextChunkPo> updateTextChunkPoList = new ArrayList<>();
        for (KnowledgeAddDto knowledgeAddDto : knowledgeAddDtoList) {
            TextChunkPo textChunkPo = new TextChunkPo();
            textChunkPo.setId(knowledgeAddDto.getId());
            textChunkPo.setTextChunk(knowledgeAddDto.getTextChunk());
            textChunkPo.setSrcFileId(knowledgeAddDto.getFileId());
            textChunkPo.setDescription(knowledgeAddDto.getOutline().get(0));
            textChunkPo.setCreateTime(System.currentTimeMillis());
            textChunkPo.setUpdateTime(System.currentTimeMillis());
            for (String outline : knowledgeAddDto.getOutline()) {
                EmbeddingsItemPoWithBLOBs embeddingsItemPo = Translator.translateToEmbeddingsItemPo(outlineToEmbedMap.get(outline), textChunkPo.getId(), knowledgeAddDto.getDescription());
                if (existingEmbedHashcode.contains(embeddingsItemPo.getHash())) {
                    updateEmbeddingsItemPoList.add(embeddingsItemPo);
                } else {
                    insertEmbeddingsItemPoList.add(embeddingsItemPo);
                }
            }
            if (existingTextChunkIds.contains(textChunkPo.getId())) {
                updateTextChunkPoList.add(textChunkPo);
            } else {
                insertTextChunkPoList.add(textChunkPo);
            }
        }
        if (!insertEmbeddingsItemPoList.isEmpty()) {
            embeddingsItemPoMapper.batchInsert(insertEmbeddingsItemPoList);
            vectorDatabaseService.putData(insertEmbeddingsItemPoList);
        }
        if (!updateEmbeddingsItemPoList.isEmpty()) {
            for (EmbeddingsItemPoWithBLOBs embeddingsItemPo : updateEmbeddingsItemPoList) {
                embeddingsItemPoMapper.updateByPrimaryKey(embeddingsItemPo);
            }
            vectorDatabaseService.putData(updateEmbeddingsItemPoList);
        }
        if (!insertTextChunkPoList.isEmpty()) {
            textChunkPoMapper.batchInsert(insertTextChunkPoList);
        }
        for (TextChunkPo textChunkPo : updateTextChunkPoList) {
            textChunkPoMapper.updateByPrimaryKeyWithBLOBs(textChunkPo);
        }
    }
}
