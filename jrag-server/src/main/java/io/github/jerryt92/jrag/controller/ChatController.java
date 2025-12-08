package io.github.jerryt92.jrag.controller;

import com.alibaba.fastjson2.JSONObject;
import io.github.jerryt92.jrag.config.CommonProperties;
import io.github.jerryt92.jrag.config.annotation.AutoRegisterWebSocketHandler;
import io.github.jerryt92.jrag.model.ChatCallback;
import io.github.jerryt92.jrag.model.ChatContextDto;
import io.github.jerryt92.jrag.model.ChatRequestDto;
import io.github.jerryt92.jrag.model.ChatResponseDto;
import io.github.jerryt92.jrag.model.ContextIdDto;
import io.github.jerryt92.jrag.model.HistoryContextList;
import io.github.jerryt92.jrag.model.MessageFeedbackRequest;
import io.github.jerryt92.jrag.model.Translator;
import io.github.jerryt92.jrag.model.security.SessionBo;
import io.github.jerryt92.jrag.server.api.ChatApi;
import io.github.jerryt92.jrag.service.llm.ChatContextBo;
import io.github.jerryt92.jrag.service.llm.ChatContextService;
import io.github.jerryt92.jrag.service.llm.ChatService;
import io.github.jerryt92.jrag.service.security.LoginService;
import io.github.jerryt92.jrag.utils.UUIDUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Log4j2
@RestController
@AutoRegisterWebSocketHandler(path = "/ws/jrag/chat", allowedOrigin = "*")
public class ChatController extends AbstractWebSocketHandler implements ChatApi {
    private final ChatContextService chatContextService;
    private final ChatService chatService;
    private final LoginService loginService;
    private final CommonProperties commonProperties;

    public ChatController(ChatContextService chatContextService, ChatService chatService, LoginService loginService, CommonProperties commonProperties) {
        this.chatContextService = chatContextService;
        this.chatService = chatService;
        this.loginService = loginService;
        this.commonProperties = commonProperties;
    }

    @Override
    public ResponseEntity<ChatContextDto> getHistoryContext(String contextId) {
        ChatContextBo chatContextBo = chatContextService.getChatContext(contextId, null);
        return ResponseEntity.ok(chatContextBo == null ? null : Translator.translateToChatContextDto(chatContextBo));
    }

    @Override
    public ResponseEntity<Void> deleteHistoryContext(List<String> contextId) {
        chatContextService.deleteHistoryContext(contextId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<HistoryContextList> getHistoryContextList(Integer offset, Integer limit) {
        return ResponseEntity.ok(chatContextService.getHistoryContextList(offset, limit));
    }

    @Override
    public ResponseEntity<ContextIdDto> getNewContextId() {
        return ResponseEntity.ok(
                new ContextIdDto().contextId(UUIDUtil.randomUUID())
        );
    }

    @Override
    public ResponseEntity<Void> addMessageFeedback(MessageFeedbackRequest messageFeedbackRequest) {
        chatContextService.addMessageFeedback(messageFeedbackRequest);
        return ResponseEntity.ok().build();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String contextId = getParam("context-id", Objects.requireNonNull(session.getUri()).toString());
        if (StringUtils.isNotBlank(contextId)) {
            session.getAttributes().put("contextId", contextId);
            session.getAttributes().put("callback", new ChatCallback<ChatResponseDto>(UUIDUtil.randomUUID()));
        } else {
            closeSession(session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
        ChatRequestDto chatRequestDto = JSONObject.parseObject(message.getPayload(), ChatRequestDto.class);
        try {
            wsSession.sendMessage(new TextMessage(JSONObject.toJSONString(new ChatResponseDto())));
        } catch (IOException e) {
            closeSession(wsSession);
        }
        SessionBo session = commonProperties.publicMode ? null : loginService.getSession();
        ChatCallback<ChatResponseDto> innerChatChatCallback = getSseCallback(wsSession);
        innerChatChatCallback.responseCall = chatResponse -> {
            try {
                wsSession.sendMessage(new TextMessage(JSONObject.toJSONString(chatResponse)));
            } catch (Throwable t) {
                log.error("", t);
                closeSession(wsSession);
            }
        };
        innerChatChatCallback.completeCall = () -> closeSession(wsSession);
        innerChatChatCallback.errorCall = t -> {
            try {
                ChatResponseDto errorResponse = new ChatResponseDto().done(true).error(true);
                if (t instanceof org.springframework.web.reactive.function.client.WebClientResponseException ex) {
                    errorResponse.setErrorCode(String.valueOf(ex.getStatusCode().value()));
                    errorResponse.setErrorMessage(ex.getResponseBodyAsString());
                } else {
                    errorResponse.setErrorMessage(t.getMessage());
                }
                wsSession.sendMessage(new TextMessage(JSONObject.toJSONString(errorResponse)));
            } catch (Throwable e) {
                log.error("", e);
            }
        };
        Thread.startVirtualThread(() -> chatService.handleChat(innerChatChatCallback, chatRequestDto, session == null ? null : session.getUserId()));
    }

    private void closeSession(WebSocketSession session) {
        try {
            session.close();
        } catch (Throwable t) {
            log.error("", t);
        }
    }

    @SuppressWarnings("unchecked")
    private ChatCallback<ChatResponseDto> getSseCallback(WebSocketSession session) {
        Object callbackObj = session.getAttributes().get("callback");
        if (callbackObj instanceof ChatCallback) {
            return (ChatCallback<ChatResponseDto>) callbackObj;
        }
        throw new IllegalStateException("Callback attribute is missing or invalid");
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        ChatCallback<ChatResponseDto> chatCallback = getSseCallback(session);
        chatCallback.onWebsocketClose.run();
        super.afterConnectionClosed(session, status);
    }

    private static String getParam(String param, String url) {
        if (url != null) {
            // 找到查询参数部分（?后面的部分）
            int queryStart = url.indexOf('?');
            if (queryStart != -1 && queryStart < url.length() - 1) {
                String queryString = url.substring(queryStart + 1);
                String[] params = queryString.split("&");
                for (String p : params) {
                    String[] keyValue = p.split("=", 2); // 限制分割成2部分
                    if (keyValue.length >= 1 && keyValue[0].equals(param)) {
                        return keyValue.length >= 2 ? keyValue[1] : null;
                    }
                }
            }
        }
        return null;
    }
}
