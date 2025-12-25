package io.github.jerryt92.jrag.service.rag.knowledge;

import io.github.jerryt92.jrag.mapper.MyTextChunkPoMapper;
import io.github.jerryt92.jrag.mapper.mgb.EmbeddingsItemPoMapper;
import io.github.jerryt92.jrag.mapper.mgb.FilePoMapper;
import io.github.jerryt92.jrag.mapper.mgb.UserPoMapper;
import io.github.jerryt92.jrag.model.EmbeddingModel;
import io.github.jerryt92.jrag.model.KnowledgeAddDto;
import io.github.jerryt92.jrag.model.KnowledgeDto;
import io.github.jerryt92.jrag.model.KnowledgeGetListDto;
import io.github.jerryt92.jrag.model.Translator;
import io.github.jerryt92.jrag.model.security.SessionBo;
import io.github.jerryt92.jrag.po.mgb.EmbeddingsItemPo;
import io.github.jerryt92.jrag.po.mgb.EmbeddingsItemPoExample;
import io.github.jerryt92.jrag.po.mgb.EmbeddingsItemPoWithBLOBs;
import io.github.jerryt92.jrag.po.mgb.FilePo;
import io.github.jerryt92.jrag.po.mgb.FilePoExample;
import io.github.jerryt92.jrag.po.mgb.TextChunkPo;
import io.github.jerryt92.jrag.po.mgb.TextChunkPoExample;
import io.github.jerryt92.jrag.po.mgb.UserPo;
import io.github.jerryt92.jrag.po.mgb.UserPoExample;
import io.github.jerryt92.jrag.service.embedding.EmbeddingService;
import io.github.jerryt92.jrag.service.rag.vdb.VectorDatabaseService;
import io.github.jerryt92.jrag.utils.HashUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeService {
    private final EmbeddingService embeddingService;
    private final MyTextChunkPoMapper myTextChunkPoMapper;
    private final EmbeddingsItemPoMapper embeddingsItemPoMapper;
    private final FilePoMapper filePoMapper;
    private final UserPoMapper userPoMapper;
    private final TransactionTemplate transactionTemplate;
    private final VectorDatabaseService vectorDatabaseService;
    private final SqlSessionFactory sqlSessionFactory;

    public KnowledgeService(EmbeddingService embeddingService, MyTextChunkPoMapper myTextChunkPoMapper, EmbeddingsItemPoMapper embeddingsItemPoMapper, FilePoMapper filePoMapper, UserPoMapper userPoMapper, TransactionTemplate transactionTemplate, VectorDatabaseService vectorDatabaseService, SqlSessionFactory sqlSessionFactory) {
        this.embeddingService = embeddingService;
        this.myTextChunkPoMapper = myTextChunkPoMapper;
        this.embeddingsItemPoMapper = embeddingsItemPoMapper;
        this.filePoMapper = filePoMapper;
        this.userPoMapper = userPoMapper;
        this.transactionTemplate = transactionTemplate;
        this.vectorDatabaseService = vectorDatabaseService;
        this.sqlSessionFactory = sqlSessionFactory;
    }

    public KnowledgeGetListDto getKnowledge(Integer offset, Integer limit, String search) {
        KnowledgeGetListDto knowledgeGetListDto = new KnowledgeGetListDto();
        TextChunkPoExample textChunkPoExample = new TextChunkPoExample();
        textChunkPoExample.setOffset(offset);
        textChunkPoExample.setRows(limit);
        textChunkPoExample.setOrderByClause("update_time DESC");
        // 获取所有textChunk
        List<TextChunkPo> textChunkPos = myTextChunkPoMapper.selectByExampleWithBLOBsWithSearch(textChunkPoExample, search);
        if (CollectionUtils.isEmpty(textChunkPos)) {
            return knowledgeGetListDto;
        }
        // 获取所有embeddingsItem
        List<String> textId = new ArrayList<>(new HashSet<>(textChunkPos.stream().map(TextChunkPo::getId).collect(Collectors.toList())));
        EmbeddingsItemPoExample embeddingsItemPoExample = new EmbeddingsItemPoExample();
        embeddingsItemPoExample.createCriteria().andTextChunkIdIn(textId);
        List<EmbeddingsItemPoWithBLOBs> embeddingsItemPos = embeddingsItemPoMapper.selectByExampleWithBLOBs(embeddingsItemPoExample);
        Map<String, List<EmbeddingsItemPoWithBLOBs>> textChunkHashToEmbeddingsItemPo = new HashMap<>();
        for (EmbeddingsItemPoWithBLOBs embeddingsItemPo : embeddingsItemPos) {
            textChunkHashToEmbeddingsItemPo.computeIfAbsent(embeddingsItemPo.getTextChunkId(), k -> new ArrayList<>())
                    .add(embeddingsItemPo);
        }
        // 获取所有file
        List<Integer> fileId = new ArrayList<>(new HashSet<>(textChunkPos.stream().map(TextChunkPo::getSrcFileId).collect(Collectors.toList())));
        FilePoExample filePoExample = new FilePoExample();
        filePoExample.createCriteria().andIdIn(fileId);
        List<FilePo> filePos = filePoMapper.selectByExample(filePoExample);
        Map<Integer, FilePo> fileIdToFilePo = new HashMap<>();
        for (FilePo filePo : filePos) {
            fileIdToFilePo.put(filePo.getId(), filePo);
        }
        List<KnowledgeDto> knowledgeDtoList = new ArrayList<>();
        List<String> userIdList = textChunkPos.stream().map(TextChunkPo::getCreateUserId).distinct().toList();
        UserPoExample userPoExample = new UserPoExample();
        userPoExample.createCriteria().andIdIn(userIdList);
        List<UserPo> userPos = userPoMapper.selectByExample(userPoExample);
        Map<String, UserPo> userIdToUserPo = userPos.stream().collect(Collectors.toMap(UserPo::getId, k -> k, (k1, k2) -> k1));
        for (TextChunkPo textChunkPo : textChunkPos) {
            knowledgeDtoList.add(Translator.translateToKnowledgeDto(
                    textChunkPo,
                    textChunkHashToEmbeddingsItemPo.get(textChunkPo.getId()),
                    fileIdToFilePo.get(textChunkPo.getSrcFileId()),
                    embeddingService.getDimension(),
                    userIdToUserPo.getOrDefault(textChunkPo.getCreateUserId(), new UserPo()).getUsername()
            ));
        }
        knowledgeGetListDto.setData(knowledgeDtoList);
        return knowledgeGetListDto;
    }

    @Transactional(rollbackFor = Throwable.class)
    public void putKnowledge(List<KnowledgeAddDto> knowledgeAddDtoList, SessionBo sessionBo) {
        Map<String, String> outlineMap = new HashMap<>();
        for (KnowledgeAddDto knowledgeAddDto : knowledgeAddDtoList) {
            for (String outline : knowledgeAddDto.getOutline()) {
                try {
                    if (StringUtils.isBlank(knowledgeAddDto.getId())) {
                        knowledgeAddDto.setId(HashUtil.getMessageDigest(outline.getBytes(StandardCharsets.UTF_8), HashUtil.MdAlgorithm.SHA1));
                    }
                    outlineMap.put(HashUtil.getMessageDigest(outline.getBytes(StandardCharsets.UTF_8), HashUtil.MdAlgorithm.SHA1), outline);
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        TextChunkPoExample textChunkPoExample = new TextChunkPoExample();
        textChunkPoExample.createCriteria().andIdIn(knowledgeAddDtoList.stream().map(KnowledgeAddDto::getId).collect(Collectors.toList()));
        HashSet<String> existingTextChunkIds = new HashSet<>();
        List<TextChunkPo> textChunkPoList = myTextChunkPoMapper.selectByExampleWithBLOBs(textChunkPoExample);
        // 已存在的文本块
        if (!CollectionUtils.isEmpty(textChunkPoList)) {
            for (TextChunkPo textChunkPo : textChunkPoList) {
                existingTextChunkIds.add(textChunkPo.getId());
            }
        }
        // 嵌入数据
        EmbeddingModel.EmbeddingsResponse embed = embeddingService.embed(new EmbeddingModel.EmbeddingsRequest().setInput(new ArrayList<>(outlineMap.values())));
        List<String> allEmbedHashcode = new ArrayList<>();
        HashMap<String, EmbeddingModel.EmbeddingsItem> outlineToEmbedMap = new HashMap<>();
        for (EmbeddingModel.EmbeddingsItem embeddingsItem : embed.getData()) {
            try {
                allEmbedHashcode.add(HashUtil.getMessageDigest(embeddingsItem.getText().getBytes(StandardCharsets.UTF_8), HashUtil.MdAlgorithm.SHA1));
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
            textChunkPo.setDescription(knowledgeAddDto.getDescription());
            textChunkPo.setCreateTime(System.currentTimeMillis());
            textChunkPo.setUpdateTime(System.currentTimeMillis());
            textChunkPo.setCreateUserId(sessionBo.getUserId());
            for (String outline : knowledgeAddDto.getOutline()) {
                EmbeddingsItemPoWithBLOBs embeddingsItemPo = Translator.translateToEmbeddingsItemPo(outlineToEmbedMap.get(outline), textChunkPo.getId(), knowledgeAddDto.getDescription(), sessionBo.getUserId());
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
            myTextChunkPoMapper.batchInsert(insertTextChunkPoList);
        }
        for (TextChunkPo textChunkPo : updateTextChunkPoList) {
            myTextChunkPoMapper.updateByPrimaryKeyWithBLOBs(textChunkPo);
        }
    }

    @Transactional(rollbackFor = Throwable.class)
    public void deleteKnowledge(List<String> textChunkIds, SessionBo sessionBo) {
        try {
            TextChunkPoExample textChunkPoExample = new TextChunkPoExample();
            TextChunkPoExample.Criteria criteria = textChunkPoExample.createCriteria();
            criteria.andIdIn(textChunkIds);
            if (!sessionBo.getRole().equals(SessionBo.RoleEnum.ADMIN)) {
                // 非管理员只能删除自己创建的
                criteria.andCreateUserIdEqualTo(sessionBo.getUserId());
            }
            myTextChunkPoMapper.deleteByExample(textChunkPoExample);
            EmbeddingsItemPoExample embeddingsItemPoExample = new EmbeddingsItemPoExample();
            EmbeddingsItemPoExample.Criteria embeddingsItemPoExampleCriteria = embeddingsItemPoExample.createCriteria();
            embeddingsItemPoExampleCriteria.andTextChunkIdIn(textChunkIds);
            List<EmbeddingsItemPo> embeddingsItemPos = embeddingsItemPoMapper.selectByExample(embeddingsItemPoExample);
            embeddingsItemPoMapper.deleteByExample(embeddingsItemPoExample);
            List<String> embedTextHashes = embeddingsItemPos.stream().map(EmbeddingsItemPo::getHash).distinct().toList();
            vectorDatabaseService.deleteData(embedTextHashes);
            log.warn("Delete knowledge success, textChunkIds: {}, userId: {}", textChunkIds, sessionBo.getUserId());
        } catch (Throwable t) {
            log.error("", t);
        }
    }

    public List<EmbeddingsItemPoWithBLOBs> checkAndGetEmbedData(String checkEmbeddingHash) {
        // 查询是否存在checkEmbeddingHash不一致的数据
        EmbeddingsItemPoExample unSavedEmbeddingsItemPoExample = new EmbeddingsItemPoExample();
        unSavedEmbeddingsItemPoExample.limit(1);
        unSavedEmbeddingsItemPoExample.createCriteria().andCheckEmbeddingHashNotEqualTo(checkEmbeddingHash);
        List<EmbeddingsItemPoWithBLOBs> embeddingsItemPoWithBLOBs = embeddingsItemPoMapper.selectByExampleWithBLOBs(unSavedEmbeddingsItemPoExample);
        if (!CollectionUtils.isEmpty(embeddingsItemPoWithBLOBs)) {
            log.warn("存在不一致的embedding数据，正在重新建立向量数据");
            Thread.startVirtualThread(() -> reEmbedData(checkEmbeddingHash));
        }
        EmbeddingsItemPoExample embeddingsItemPoExample = new EmbeddingsItemPoExample();
        embeddingsItemPoExample.createCriteria().andCheckEmbeddingHashEqualTo(checkEmbeddingHash);
        embeddingsItemPoWithBLOBs = embeddingsItemPoMapper.selectByExampleWithBLOBs(embeddingsItemPoExample);
        return embeddingsItemPoWithBLOBs;
    }

    /**
     * 重新embed不一致的数据
     *
     * @param checkEmbeddingHash
     */
    private void reEmbedData(String checkEmbeddingHash) {
        AtomicBoolean hasMore = new AtomicBoolean(true);
        while (hasMore.get()) {
            EmbeddingsItemPoExample unSavedEmbeddingsItemPoExample = new EmbeddingsItemPoExample();
            unSavedEmbeddingsItemPoExample.limit(100);
            unSavedEmbeddingsItemPoExample.createCriteria().andCheckEmbeddingHashNotEqualTo(checkEmbeddingHash);
            List<EmbeddingsItemPoWithBLOBs> oldEmbeddingsItemPoWithBLOBs = embeddingsItemPoMapper.selectByExampleWithBLOBs(unSavedEmbeddingsItemPoExample);
            List<String> oldEmbeddingHashes = new ArrayList<>();
            if (!CollectionUtils.isEmpty(oldEmbeddingsItemPoWithBLOBs)) {
                EmbeddingModel.EmbeddingsRequest embeddingsRequest = new EmbeddingModel.EmbeddingsRequest();
                embeddingsRequest.setInput(new ArrayList<>());
                for (EmbeddingsItemPoWithBLOBs oldEmbeddingsItem : oldEmbeddingsItemPoWithBLOBs) {
                    embeddingsRequest.getInput().add(oldEmbeddingsItem.getText());
                    oldEmbeddingHashes.add(oldEmbeddingsItem.getHash());
                }
                // 获取新embedding数据
                EmbeddingModel.EmbeddingsResponse embeddingsResponse = embeddingService.embed(embeddingsRequest);
                Map<String, EmbeddingModel.EmbeddingsItem> outline2newEmbedding = embeddingsResponse.getData().stream().map(o -> o).collect(Collectors.toMap(EmbeddingModel.EmbeddingsItem::getText, o -> o, (o1, o2) -> o1));
                List<EmbeddingsItemPoWithBLOBs> newEmbeddingsItemPoList = new ArrayList<>();
                for (EmbeddingsItemPoWithBLOBs oldEmbeddingsItem : oldEmbeddingsItemPoWithBLOBs) {
                    EmbeddingModel.EmbeddingsItem newEmbeddingsItem = outline2newEmbedding.get(oldEmbeddingsItem.getText());
                    if (newEmbeddingsItem.getCheckEmbeddingHash() == null || !newEmbeddingsItem.getCheckEmbeddingHash().equals(checkEmbeddingHash)) {
                        log.error("checkEmbeddingHash 不一致，请检查embedding模型是否正确");
                        hasMore.set(false);
                        return;
                    }
                    EmbeddingsItemPoWithBLOBs embeddingsItemPo = Translator.translateToEmbeddingsItemPo(
                            newEmbeddingsItem,
                            oldEmbeddingsItem.getTextChunkId(),
                            oldEmbeddingsItem.getDescription(),
                            oldEmbeddingsItem.getCreateUserId()
                    );
                    newEmbeddingsItemPoList.add(embeddingsItemPo);
                }
                transactionTemplate.executeWithoutResult(status -> {
                    try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
                        EmbeddingsItemPoMapper embeddingsItemPoMapper = sqlSession.getMapper(EmbeddingsItemPoMapper.class);
                        for (EmbeddingsItemPoWithBLOBs embeddingsItemPo : newEmbeddingsItemPoList) {
                            embeddingsItemPoMapper.updateByPrimaryKeyWithBLOBs(embeddingsItemPo);
                        }
                        sqlSession.commit();
                    }
                });
                try {
                    // 向量数据库删除旧数据
                    vectorDatabaseService.deleteData(oldEmbeddingHashes);
                    // 向量数据库保存新数据
                    vectorDatabaseService.putData(newEmbeddingsItemPoList);
                } catch (Throwable t) {
                    log.error("", t);
                }
            } else {
                hasMore.set(false);
            }
        }
    }
}
