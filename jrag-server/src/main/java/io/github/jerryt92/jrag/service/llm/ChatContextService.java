package io.github.jerryt92.jrag.service.llm;

import io.github.jerryt92.jrag.config.BackendTaskConfig;
import io.github.jerryt92.jrag.config.CommonProperties;
import io.github.jerryt92.jrag.config.LlmProperties;
import io.github.jerryt92.jrag.mapper.mgb.ChatContextItemMapper;
import io.github.jerryt92.jrag.mapper.mgb.ChatContextRecordMapper;
import io.github.jerryt92.jrag.model.ChatModel;
import io.github.jerryt92.jrag.model.HistoryContextItem;
import io.github.jerryt92.jrag.model.HistoryContextList;
import io.github.jerryt92.jrag.model.MessageFeedbackRequest;
import io.github.jerryt92.jrag.model.Translator;
import io.github.jerryt92.jrag.model.security.SessionBo;
import io.github.jerryt92.jrag.po.mgb.ChatContextItemExample;
import io.github.jerryt92.jrag.po.mgb.ChatContextItemKey;
import io.github.jerryt92.jrag.po.mgb.ChatContextItemWithBLOBs;
import io.github.jerryt92.jrag.po.mgb.ChatContextRecord;
import io.github.jerryt92.jrag.po.mgb.ChatContextRecordExample;
import io.github.jerryt92.jrag.service.llm.client.LlmClient;
import io.github.jerryt92.jrag.service.llm.tools.FunctionCallingService;
import io.github.jerryt92.jrag.service.security.LoginService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 对话上下文服务
 */
@Slf4j
@Service
@EnableScheduling
public class ChatContextService {
    private final ChatContextRecordMapper chatContextRecordMapper;
    private final ChatContextItemMapper chatContextItemMapper;
    private final FunctionCallingService functionCallingService;
    private final ChatContextStorageService chatContextStorageService;
    private final LlmClient llmClient;
    /**
     * 对话上下文实例缓存
     */
    private final ConcurrentHashMap<String, ChatContextBo> chatContextMap = new ConcurrentHashMap<>();
    private final LoginService loginService;
    private final LlmProperties llmProperties;
    private final CommonProperties commonProperties;

    public ChatContextService(ChatContextRecordMapper chatContextRecordMapper,
                              ChatContextItemMapper chatContextItemMapper,
                              FunctionCallingService functionCallingService,
                              @Qualifier(BackendTaskConfig.BACKEND_TASK_EXECUTOR) TaskExecutor topoConcurrentQueryExecutor,
                              ChatContextStorageService chatContextStorageService,
                              LlmClient llmClient, LoginService loginService, LlmProperties llmProperties, CommonProperties commonProperties) {
        this.chatContextRecordMapper = chatContextRecordMapper;
        this.chatContextItemMapper = chatContextItemMapper;
        this.functionCallingService = functionCallingService;
        this.chatContextStorageService = chatContextStorageService;
        this.llmClient = llmClient;
        this.loginService = loginService;
        this.llmProperties = llmProperties;
        this.commonProperties = commonProperties;
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void checkChatContextToDbTask() {
        try {
            checkChatContextToDb();
        } catch (Throwable t) {
            log.error("", t);
        }
    }

    /**
     * 新增对话
     *
     * @param contextId
     * @param chatContextBo
     */
    public void addChatContext(String contextId, ChatContextBo chatContextBo) {
        if (!commonProperties.publicMode) {
            ChatContextRecord chatContextRecord = chatContextRecordMapper.selectByPrimaryKey(contextId);
            if (chatContextRecord != null) {
                throw new RuntimeException("contextId '" + contextId + "' was already exists");
            }
            ChatContextRecord insertChatContextRecord = Translator.translateToChatContextRecord(chatContextBo);
            chatContextRecordMapper.insert(insertChatContextRecord);
        }
        chatContextMap.put(contextId, chatContextBo);
    }

    /**
     * 判断对话上下文是否存在
     *
     * @param contextId
     * @return
     */
    public boolean containsChatContext(String contextId) {
        boolean contains = chatContextMap.containsKey(contextId);
        if (!contains) {
            // 从数据库中加载对话上下文
            ChatContextRecord chatContextRecord = chatContextRecordMapper.selectByPrimaryKey(contextId);
            if (chatContextRecord != null) {
                contains = true;
            }
        }
        return contains;
    }

    /**
     * 获取对话上下文
     *
     * @param contextId
     * @return
     */
    public ChatContextBo getChatContext(String contextId, String userId) {
        ChatContextBo chatContextBo = null;
        chatContextBo = chatContextMap.get(contextId);
        if (!commonProperties.publicMode) {
            if (chatContextBo == null) {
                // 从数据库中加载对话上下文
                ChatContextRecordExample chatContextRecordExample = new ChatContextRecordExample();
                ChatContextRecordExample.Criteria criteria = chatContextRecordExample.createCriteria();
                criteria.andContextIdEqualTo(contextId);
                if (userId != null) {
                    criteria.andUserIdEqualTo(userId);
                }
                List<ChatContextRecord> chatContextRecords = chatContextRecordMapper.selectByExample(chatContextRecordExample);
                if (!chatContextRecords.isEmpty()) {
                    ChatContextRecord chatContextRecord = chatContextRecords.get(0);
                    ChatContextItemExample chatContextItemExample = new ChatContextItemExample();
                    chatContextItemExample.createCriteria()
                            .andContextIdEqualTo(contextId);
                    // 根据index升序排序
                    chatContextItemExample.setOrderByClause("message_index asc");
                    List<ChatContextItemWithBLOBs> chatContextItemList = chatContextItemMapper.selectByExampleWithBLOBs(chatContextItemExample);
                    List<ChatModel.Message> chatModelMessages = new ArrayList<>();
                    for (ChatContextItemWithBLOBs chatContextItem : chatContextItemList) {
                        chatModelMessages.add(Translator.translateToChatMessage(chatContextItem));
                    }
                    chatContextBo = new ChatContextBo(contextId, chatContextRecord.getUserId(), llmClient, functionCallingService, chatContextStorageService, llmProperties);
                    chatContextMap.put(contextId, chatContextBo);
                    chatContextBo.setMessages(chatModelMessages);
                }
            }
        }
        return chatContextBo;
    }

    /**
     * 移除对话上下文
     *
     * @param contextIds
     * @return
     */
    @Transactional(rollbackFor = Throwable.class)
    public void deleteHistoryContext(List<String> contextIds) {
        SessionBo session = loginService.getSession();
        if (session != null) {
            for (String contextId : contextIds) {
                ChatContextBo chatContextBo = chatContextMap.get(contextId);
                if (chatContextBo != null) {
                    if (chatContextBo.getUserId().equals(session.getUserId())) {
                        chatContextMap.remove(contextId);
                    }
                }
            }
            ChatContextRecordExample chatContextRecordExample = new ChatContextRecordExample();
            chatContextRecordExample.createCriteria()
                    .andContextIdIn(contextIds)
                    .andUserIdEqualTo(session.getUserId());
            List<ChatContextRecord> needDeleteChatContextRecordList = chatContextRecordMapper.selectByExample(chatContextRecordExample);
            if (!needDeleteChatContextRecordList.isEmpty()) {
                List<String> needDeleteContextIds = needDeleteChatContextRecordList.stream().map(ChatContextRecord::getContextId).collect(Collectors.toList());
                chatContextRecordExample = new ChatContextRecordExample();
                chatContextRecordExample.createCriteria()
                        .andContextIdIn(needDeleteContextIds);
                chatContextRecordMapper.deleteByExample(chatContextRecordExample);
                ChatContextItemExample chatContextItemExample = new ChatContextItemExample();
                chatContextItemExample.createCriteria()
                        .andContextIdIn(needDeleteContextIds);
                chatContextItemMapper.deleteByExample(chatContextItemExample);
            }
        }
    }

    public HistoryContextList getHistoryContextList(Integer offset, Integer limit) {
        if (commonProperties.publicMode) {
            return new HistoryContextList().data(new ArrayList<>());
        }
        SessionBo session = loginService.getSession();
        if (session == null) {
            return new HistoryContextList().data(new ArrayList<>());
        }
        ChatContextRecordExample chatContextRecordExample = new ChatContextRecordExample();
        chatContextRecordExample.setOrderByClause("update_time desc");
        chatContextRecordExample.limit(offset, limit);
        chatContextRecordExample.createCriteria().andUserIdEqualTo(session.getUserId());
        List<ChatContextRecord> chatContextRecordList = chatContextRecordMapper.selectByExample(chatContextRecordExample);
        List<HistoryContextItem> historyContextItemList = new ArrayList<>();
        for (ChatContextRecord chatContextRecord : chatContextRecordList) {
            historyContextItemList.add(Translator.translateToHistoryContextItem(chatContextRecord));
        }
        return new HistoryContextList().data(historyContextItemList);
    }

    private void checkChatContextToDb() {
        for (ChatContextBo chatContextBo : chatContextMap.values()) {
            if (System.currentTimeMillis() - chatContextBo.getLastRequestTime() > 1000 * 60 * 5) {
                chatContextStorageService.storageChatContextToDb(chatContextBo, chatContextMap);
            }
        }
    }

    public void addMessageFeedback(MessageFeedbackRequest messageFeedbackRequest) {
        if (!commonProperties.publicMode) {
            SessionBo session = loginService.getSession();
            if (session != null) {
                ChatContextBo chatContextBo = getChatContext(messageFeedbackRequest.getContextId(), session.getUserId());
                if (chatContextBo != null) {
                    ChatContextItemKey chatContextItemKey = new ChatContextItemKey();
                    chatContextItemKey.setContextId(messageFeedbackRequest.getContextId());
                    chatContextItemKey.setMessageIndex(messageFeedbackRequest.getIndex());
                    ChatContextItemWithBLOBs chatContextItem = new ChatContextItemWithBLOBs();
                    chatContextItem.setContextId(messageFeedbackRequest.getContextId());
                    chatContextItem.setMessageIndex(messageFeedbackRequest.getIndex());
                    chatContextItem.setFeedback(messageFeedbackRequest.getFeedback().getValue());
                    chatContextItemMapper.updateByPrimaryKeySelective(chatContextItem);
                    List<ChatModel.Message> messages = chatContextBo.getMessages();
                    if (messageFeedbackRequest.getIndex() < messages.size()) {
                        ChatModel.Message message = messages.get(messageFeedbackRequest.getIndex());
                        message.setFeedback(ChatModel.Feedback.fromValue(messageFeedbackRequest.getFeedback().getValue()));
                    }
                }
            }
        }
    }
}
