package io.github.jerryt92.jrag.service.llm;

import io.github.jerryt92.jrag.config.LlmProperties;
import io.github.jerryt92.jrag.model.ChatCallback;
import io.github.jerryt92.jrag.model.ChatRequestDto;
import io.github.jerryt92.jrag.model.ChatResponseDto;
import io.github.jerryt92.jrag.model.FileDto;
import io.github.jerryt92.jrag.model.MessageDto;
import io.github.jerryt92.jrag.model.RagInfoDto;
import io.github.jerryt92.jrag.model.Translator;
import io.github.jerryt92.jrag.service.llm.client.LlmClient;
import io.github.jerryt92.jrag.service.llm.tools.FunctionCallingService;
import io.github.jerryt92.jrag.service.rag.retrieval.Retriever;
import io.github.jerryt92.jrag.utils.UUIDUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ChatService {
    private final LlmClient llmClient;
    private final FunctionCallingService functionCallingService;
    private final ChatContextService chatContextService;
    private final ChatContextStorageService chatContextStorageService;
    private final LlmProperties llmProperties;
    private final Retriever retriever;
    static Map<String, ChatCallback<ChatResponseDto>> contextChatCallbackMap = new HashMap<>();

    public ChatService(LlmClient llmClient, FunctionCallingService functionCallingService, ChatContextService chatContextService, ChatContextStorageService chatContextStorageService, LlmProperties llmProperties, Retriever retriever) {
        this.llmClient = llmClient;
        this.functionCallingService = functionCallingService;
        this.chatContextService = chatContextService;
        this.chatContextStorageService = chatContextStorageService;
        this.llmProperties = llmProperties;
        this.retriever = retriever;
    }

    public void handleChat(ChatCallback<ChatResponseDto> chatChatCallback, ChatRequestDto request, String userId) {
        try {
            String contextId = request.getContextId();
            if (contextId == null) {
                contextId = UUIDUtil.randomUUID();
            }
            contextChatCallbackMap.put(contextId, chatChatCallback);
            // 从src/main/resources/system_prompt.txt中获取
            String systemPrompt = null;
            try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("system_prompt.txt")) {
                if (inputStream != null) {
                    systemPrompt = new String(inputStream.readAllBytes());
                }
            } catch (IOException e) {
                log.error("", e);
            }
            if (!CollectionUtils.isEmpty(request.getMessages())) {
                ChatContextBo chatContextBo = chatContextService.getChatContext(contextId, userId);
                if (chatContextBo == null) {
                    chatContextBo = new ChatContextBo(contextId, userId, llmClient, functionCallingService, chatContextStorageService, llmProperties);
                    chatContextBo.setMessages(Translator.translateToChatRequest(request).getMessages());
                    chatContextService.addChatContext(contextId, chatContextBo);
                }
                if (systemPrompt != null) {
                    MessageDto systemPromptMessageDto = new MessageDto();
                    systemPromptMessageDto.setRole(MessageDto.RoleEnum.SYSTEM);
                    systemPromptMessageDto.setContent(systemPrompt);
                    request.getMessages().add(request.getMessages().size() - 1, systemPromptMessageDto);
                }
                if (llmProperties.useRag) {
                    List<RagInfoDto> ragInfoDtos = retriever.retrieveQuery(request);
                    chatContextBo.setLastRagInfos(ragInfoDtos);
                    if (!CollectionUtils.isEmpty(ragInfoDtos)) {
                        ChatResponseDto srcFileChatResponse = new ChatResponseDto();
                        MessageDto messageDto = new MessageDto();
                        messageDto.setRole(MessageDto.RoleEnum.ASSISTANT);
                        Map<String, FileDto> fileDtoList = new LinkedHashMap<>();
                        for (RagInfoDto ragInfoDto : ragInfoDtos) {
                            if (ragInfoDto.getSrcFile() != null && !fileDtoList.containsKey(ragInfoDto.getSrcFile().getId())) {
                                fileDtoList.put(ragInfoDto.getSrcFile().getId(), ragInfoDto.getSrcFile());
                            }
                        }
                        messageDto.setSrcFile(new ArrayList<>(fileDtoList.values()));
                        srcFileChatResponse.setMessage(messageDto);
                        chatChatCallback.responseCall.accept(srcFileChatResponse);
                    }
                }
                chatContextBo.chat(request, chatChatCallback);
                log.info("问: " + request.getMessages().getLast().getContent());
            }
        } catch (Throwable t) {
            chatChatCallback.errorCall.accept(t);
        }
    }
}
