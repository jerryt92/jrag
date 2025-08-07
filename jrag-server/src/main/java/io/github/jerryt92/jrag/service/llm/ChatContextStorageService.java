package io.github.jerryt92.jrag.service.llm;


import io.github.jerryt92.jrag.mapper.mgb.ChatContextItemMapper;
import io.github.jerryt92.jrag.model.Translator;
import io.github.jerryt92.jrag.po.mgb.ChatContextItem;
import io.github.jerryt92.jrag.po.mgb.ChatContextItemExample;
import io.github.jerryt92.jrag.po.mgb.ChatContextItemWithBLOBs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话上下文存储服务
 */
@Slf4j
@Service
public class ChatContextStorageService {
    private final ChatContextItemMapper chatContextItemMapper;

    public ChatContextStorageService(ChatContextItemMapper chatContextItemMapper) {
        this.chatContextItemMapper = chatContextItemMapper;
    }

    @Transactional(rollbackFor = Throwable.class)
    public void storageChatContextToDb(ChatContextBo chatContextBo, ConcurrentHashMap<String, ChatContextBo> chatContextMap) {
        // 如果5分钟没有请求，则存储对话上下文到数据库
        List<ChatContextItemWithBLOBs> insertChatContextItemList = new ArrayList<>();
        List<ChatContextItemWithBLOBs> chatContextItemWithBLOBs = Translator.translateToChatContextItemWithBLOBs(chatContextBo);
        ChatContextItemExample chatContextItemExample = new ChatContextItemExample();
        chatContextItemExample.createCriteria().andContextIdEqualTo(chatContextBo.getContextId());
        // 查询数据库中已有的对话上下文
        HashSet<Integer> existMessageIndexSet = new HashSet<>();
        for (ChatContextItem chatContextItem : chatContextItemMapper.selectByExample(chatContextItemExample)) {
            existMessageIndexSet.add(chatContextItem.getMessageIndex());
        }
        for (ChatContextItemWithBLOBs insertChatContextItem : chatContextItemWithBLOBs) {
            if (!existMessageIndexSet.contains(insertChatContextItem.getMessageIndex())) {
                insertChatContextItemList.add(insertChatContextItem);
            }
        }
        if (!CollectionUtils.isEmpty(insertChatContextItemList)) {
            chatContextItemMapper.batchInsert(insertChatContextItemList);
        }
        if (chatContextMap != null) {
            chatContextMap.remove(chatContextBo.getContextId());
        }
    }

    @Transactional(rollbackFor = Throwable.class)
    public void storageChatContextToDb(ChatContextBo chatContextBo) {
        storageChatContextToDb(chatContextBo, null);
    }
}
