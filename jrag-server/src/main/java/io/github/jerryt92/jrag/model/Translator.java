package io.github.jerryt92.jrag.model;

import com.alibaba.fastjson2.JSONArray;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.jerryt92.jrag.constants.CommonConstants;
import io.github.jerryt92.jrag.po.mgb.ChatContextItemWithBLOBs;
import io.github.jerryt92.jrag.po.mgb.ChatContextRecord;
import io.github.jerryt92.jrag.po.mgb.EmbeddingsItemPoWithBLOBs;
import io.github.jerryt92.jrag.po.mgb.FilePo;
import io.github.jerryt92.jrag.service.llm.ChatContextBo;
import io.github.jerryt92.jrag.utils.MDUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Translator {
    public static EmbeddingModel.EmbeddingsRequest translateToEmbeddingsRequest(EmbeddingsRequestDto requestDto) {
        return new EmbeddingModel.EmbeddingsRequest()
                .setInput(requestDto.getInput());
    }

    public static EmbeddingsResponseDto translateToEmbeddingsResponseDto(EmbeddingModel.EmbeddingsResponse embeddingsResponse) {
        EmbeddingsResponseDto embeddingsResponseDto = new EmbeddingsResponseDto();
        List<EmbeddingsDtoItem> embeddingsDtoItems = new ArrayList<>();
        for (EmbeddingModel.EmbeddingsItem item : embeddingsResponse.getData()) {
            EmbeddingsDtoItem embeddingsItem = new EmbeddingsDtoItem();
            try {
                embeddingsItem.setHash(MDUtil.getMessageDigest(item.getText().getBytes(StandardCharsets.UTF_8), MDUtil.MdAlgorithm.SHA1));
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
            embeddingsItem.setEmbeddingModel(item.getEmbeddingModel());
            embeddingsItem.setEmbeddingProvider(item.getEmbeddingProvider());
            embeddingsItem.setText(item.getText());
            List<Float> embeddingsList = new ArrayList<>();
            for (float embedding : item.getEmbeddings()) {
                embeddingsList.add(embedding);
            }
            embeddingsItem.setEmbeddings(embeddingsList);
            embeddingsDtoItems.add(embeddingsItem);
        }
        embeddingsResponseDto.setData(embeddingsDtoItems);
        return embeddingsResponseDto;
    }

    public static JsonObject translateToMilvusData(EmbeddingsItemPoWithBLOBs embeddingsItemPo) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("hash", embeddingsItemPo.getHash());
        jsonObject.addProperty("embedding_model", embeddingsItemPo.getEmbeddingModel());
        jsonObject.addProperty("embedding_provider", embeddingsItemPo.getEmbeddingProvider());
        jsonObject.addProperty("text", embeddingsItemPo.getText());
        String embedding = embeddingsItemPo.getEmbedding();
        JsonArray embeddingsList = new JsonArray();
        for (String str : embedding.split(",")) {
            embeddingsList.add(Float.parseFloat(str));
        }
        jsonObject.add("embedding", embeddingsList);
        jsonObject.addProperty("text_chunk_id", embeddingsItemPo.getTextChunkId());
        return jsonObject;
    }

    public static EmbeddingsItemPoWithBLOBs translateToEmbeddingsItemPo(EmbeddingModel.EmbeddingsItem embeddingsItem, String textChunkId, String description) {
        EmbeddingsItemPoWithBLOBs embeddingsItemPo = new EmbeddingsItemPoWithBLOBs();
        try {
            embeddingsItemPo.setHash(MDUtil.getMessageDigest(embeddingsItem.getText().getBytes(StandardCharsets.UTF_8), MDUtil.MdAlgorithm.SHA1));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        embeddingsItemPo.setEmbeddingModel(embeddingsItem.getEmbeddingModel());
        embeddingsItemPo.setEmbeddingProvider(embeddingsItem.getEmbeddingProvider());
        embeddingsItemPo.setText(embeddingsItem.getText());
        embeddingsItemPo.setEmbedding(Arrays.toString(embeddingsItem.getEmbeddings()).replace("[", "").replace("]", ""));
        embeddingsItemPo.setTextChunkId(String.valueOf(textChunkId));
        embeddingsItemPo.setDescription(description);
        embeddingsItemPo.setCreateTime(System.currentTimeMillis());
        embeddingsItemPo.setUpdateTime(System.currentTimeMillis());
        return embeddingsItemPo;
    }

    public static FileBo translateToFileBo(FilePo filePo) {
        FileBo fileBo = new FileBo();
        fileBo.setId(filePo.getId());
        fileBo.setFullFileName(filePo.getFullFileName());
        fileBo.setSuffix(filePo.getSuffix());
        fileBo.setPath(filePo.getPath());
        fileBo.setSize(filePo.getSize());
        fileBo.setMd5(filePo.getMd5());
        fileBo.setSha1(filePo.getSha1());
        fileBo.setUploadTime(filePo.getUploadTime());
        return fileBo;
    }

    public static FilePo translateToFilePo(FileBo fileBo) {
        FilePo filePo = new FilePo();
        filePo.setId(fileBo.getId());
        filePo.setFullFileName(fileBo.getFullFileName());
        filePo.setSuffix(fileBo.getSuffix());
        filePo.setPath(fileBo.getPath());
        filePo.setSize(fileBo.getSize());
        filePo.setMd5(fileBo.getMd5());
        filePo.setSha1(fileBo.getSha1());
        filePo.setUploadTime(fileBo.getUploadTime());
        return filePo;
    }

    public static FileDto translateToFileDto(FileBo fileBo) {
        FileDto fileDto = new FileDto();
        fileDto.setId(fileBo.getId());
        fileDto.setFullFileName(fileBo.getFullFileName());
        fileDto.setUrl(CommonConstants.FILE_URL + fileBo.getId());
        return fileDto;
    }

    public static EmbeddingsQueryItemDto translateToEmbeddingsQueryItemDto(EmbeddingModel.EmbeddingsQueryItem embeddingsQueryItem) {
        EmbeddingsQueryItemDto embeddingsQueryItemDto = new EmbeddingsQueryItemDto();
        embeddingsQueryItemDto.setHash(embeddingsQueryItem.getHash());
        embeddingsQueryItemDto.setScore(embeddingsQueryItem.getScore());
        embeddingsQueryItemDto.setEmbeddingModel(embeddingsQueryItem.getEmbeddingModel());
        embeddingsQueryItemDto.setEmbeddingProvider(embeddingsQueryItem.getEmbeddingProvider());
        embeddingsQueryItemDto.setText(embeddingsQueryItem.getText());
        return embeddingsQueryItemDto;
    }

    public static ChatModel.ChatRequest translateToChatRequest(ChatRequestDto request) {
        ChatModel.ChatRequest chatRequest = new ChatModel.ChatRequest();
        List<ChatModel.Message> messages = new ArrayList<>();
        for (MessageDto messageDto : request.getMessages()) {
            messages.add(translateToChatMessage(messageDto));
        }
        chatRequest.setMessages(messages);
        return chatRequest;
    }

    public static ChatModel.Message translateToChatMessage(MessageDto messageDto) {
        ChatModel.Message chatMessage = new ChatModel.Message();
        switch (messageDto.getRole()) {
            case SYSTEM:
                chatMessage.setRole(ChatModel.Role.SYSTEM);
                break;
            case USER:
                chatMessage.setRole(ChatModel.Role.USER);
                break;
            case ASSISTANT:
                chatMessage.setRole(ChatModel.Role.ASSISTANT);
                break;
            case TOOL:
                chatMessage.setRole(ChatModel.Role.TOOL);
                break;
            default:
                throw new IllegalArgumentException("Invalid role: " + messageDto.getRole());
        }
        chatMessage.setContent(messageDto.getContent());
        chatMessage.setFeedback(messageDto.getFeedback() == null ? ChatModel.Feedback.NONE : ChatModel.Feedback.fromValue(messageDto.getFeedback().getValue()));
        chatMessage.setToolCalls(null);
        return chatMessage;
    }

    public static ChatModel.Message translateToChatMessage(ChatContextItemWithBLOBs chatContextItemWithBLOBs) {
        ChatModel.Message chatMessage = new ChatModel.Message();
        switch (chatContextItemWithBLOBs.getChatRole()) {
            case 0:
                chatMessage.setRole(ChatModel.Role.SYSTEM);
                break;
            case 1:
                chatMessage.setRole(ChatModel.Role.USER);
                break;
            case 2:
                chatMessage.setRole(ChatModel.Role.ASSISTANT);
                break;
            default:
                throw new IllegalArgumentException("Invalid role: " + chatContextItemWithBLOBs.getChatRole());
        }
        chatMessage.setContent(chatContextItemWithBLOBs.getContent());
        chatMessage.setFeedback(chatContextItemWithBLOBs.getFeedback() == null ? ChatModel.Feedback.NONE : ChatModel.Feedback.fromValue(chatContextItemWithBLOBs.getFeedback()));
        chatMessage.setToolCalls(null);
        chatMessage.setRagInfos(JSONArray.parseArray(chatContextItemWithBLOBs.getRagInfos(), RagInfoDto.class));
        return chatMessage;
    }

    public static ChatResponseDto translateToChatResponseDto(ChatModel.ChatResponse response, int index) {
        ChatResponseDto chatResponseDto = new ChatResponseDto();
        chatResponseDto.setMessage(translateToChatMessageDto(response.getMessage(), index));
        chatResponseDto.setDone(response.getDone());
        return chatResponseDto;
    }

    public static MessageDto translateToChatMessageDto(ChatModel.Message message, int index) {
        if (message == null) {
            return null;
        }
        MessageDto messageDto = new MessageDto();
        messageDto.setIndex(index);
        switch (message.getRole()) {
            case SYSTEM:
                messageDto.setRole(MessageDto.RoleEnum.SYSTEM);
                break;
            case USER:
                messageDto.setRole(MessageDto.RoleEnum.USER);
                break;
            case ASSISTANT:
                messageDto.setRole(MessageDto.RoleEnum.ASSISTANT);
                break;
            case TOOL:
                messageDto.setRole(MessageDto.RoleEnum.TOOL);
                break;
            default:
                throw new IllegalArgumentException("Invalid role: " + message.getRole());
        }
        messageDto.setFeedback(MessageDto.FeedbackEnum.fromValue(message.getFeedback().getValue()));
        messageDto.setContent(message.getContent());
        if (!CollectionUtils.isEmpty(message.getRagInfos())) {
            Map<Integer, FileDto> fileDtoMap = new HashMap<>();
            for (RagInfoDto ragInfoDto : message.getRagInfos()) {
                if (ragInfoDto.getSrcFile() != null) {
                    fileDtoMap.put(ragInfoDto.getSrcFile().getId(), ragInfoDto.getSrcFile());
                }
            }
            if (!CollectionUtils.isEmpty(fileDtoMap)) {
                messageDto.setSrcFile(new ArrayList<>(fileDtoMap.values()));
            }
        }
        return messageDto;
    }

    public static Message translateToChatMessageDto(ChatContextItemWithBLOBs chatContextItem) {
        Message wsMessage = new Message();
        wsMessage.setIndex(chatContextItem.getMessageIndex());
        switch (chatContextItem.getChatRole()) {
            case 0:
                wsMessage.setRole(Message.RoleEnum.SYSTEM);
                break;
            case 1:
                wsMessage.setRole(Message.RoleEnum.USER);
                break;
            case 2:
                wsMessage.setRole(Message.RoleEnum.ASSISTANT);
                break;
            default:
                throw new IllegalArgumentException("Invalid role: " + chatContextItem.getChatRole());
        }
        wsMessage.setContent(chatContextItem.getContent());
        wsMessage.setFeedback(Message.FeedbackEnum.fromValue(chatContextItem.getFeedback()));
        if (StringUtils.isNotEmpty(chatContextItem.getRagInfos())) {
            List<RagInfoDto> ragInfoDtos = JSONArray.parseArray(chatContextItem.getRagInfos(), RagInfoDto.class);
            List<FileDto> fileDtos = new ArrayList<>();
            for (RagInfoDto ragInfoDto : ragInfoDtos) {
                fileDtos.add(ragInfoDto.getSrcFile());
            }
            if (!CollectionUtils.isEmpty(fileDtos)) {
                wsMessage.setSrcFile(fileDtos);
            }
        }
        return wsMessage;
    }

    public static HistoryContextItem translateToHistoryContextItem(ChatContextRecord chatContextRecord) {
        HistoryContextItem historyContextItem = new HistoryContextItem();
        historyContextItem.setContextId(chatContextRecord.getContextId());
        historyContextItem.setTitle(chatContextRecord.getTitle());
        historyContextItem.setLastUpdateTime(chatContextRecord.getUpdateTime());
        return historyContextItem;
    }

    public static ChatContextDto translateToChatContextDto(ChatContextBo chatContextBo) {
        ChatContextDto chatContextDto = new ChatContextDto();
        chatContextDto.setContextId(chatContextBo.getContextId());
        List<MessageDto> messageDtoList = new ArrayList<>();
        for (int i = 0; i < chatContextBo.getMessages().size(); i++) {
            messageDtoList.add(translateToChatMessageDto(chatContextBo.getMessages().get(i), i));
        }
        chatContextDto.setMessages(messageDtoList);
        return chatContextDto;
    }

    public static ChatContextRecord translateToChatContextRecord(ChatContextBo chatContextBo) {
        ChatContextRecord chatContextRecord = new ChatContextRecord();
        chatContextRecord.setContextId(chatContextBo.getContextId());
        for (ChatModel.Message message : chatContextBo.getMessages()) {
            if (message.getRole().equals(ChatModel.Role.USER)) {
                chatContextRecord.setTitle(message.getContent());
                break;
            }
        }
        chatContextRecord.setUserId(chatContextBo.getUserId());
        chatContextRecord.setUpdateTime(chatContextBo.getLastRequestTime());
        return chatContextRecord;
    }

    public static List<ChatContextItemWithBLOBs> translateToChatContextItemWithBLOBs(ChatContextBo chatContextBo) {
        List<ChatContextItemWithBLOBs> chatContextItemWithBLOBs = new ArrayList<>();
        for (int i = 0; i < chatContextBo.getMessages().size(); i++) {
            ChatModel.Message message = chatContextBo.getMessages().get(i);
            ChatContextItemWithBLOBs chatContextItem = new ChatContextItemWithBLOBs();
            chatContextItem.setContextId(chatContextBo.getContextId());
            chatContextItem.setMessageIndex(i);
            switch (message.getRole()) {
                case SYSTEM:
                    chatContextItem.setChatRole(0);
                    break;
                case USER:
                    chatContextItem.setChatRole(1);
                    break;
                case ASSISTANT:
                    chatContextItem.setChatRole(2);
                    break;
            }
            chatContextItem.setContent(message.getContent());
            if (!CollectionUtils.isEmpty(message.getRagInfos())) {
                chatContextItem.setRagInfos(JSONArray.toJSONString(message.getRagInfos()));
            }
            chatContextItem.setFeedback(message.getFeedback().getValue());
            chatContextItem.setAddTime(System.currentTimeMillis());
            chatContextItemWithBLOBs.add(chatContextItem);
        }
        return chatContextItemWithBLOBs;
    }
}
