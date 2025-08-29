package io.github.jerryt92.jrag.controller;

import io.github.jerryt92.jrag.config.annotation.AutoRegisterWebSocketHandler;
import io.github.jerryt92.jrag.model.ChatContextDto;
import io.github.jerryt92.jrag.model.ChatRequestDto;
import io.github.jerryt92.jrag.model.ChatResponseDto;
import io.github.jerryt92.jrag.model.ContextIdDto;
import io.github.jerryt92.jrag.model.HistoryContextList;
import io.github.jerryt92.jrag.model.MessageFeedbackRequest;
import io.github.jerryt92.jrag.model.SseCallback;
import io.github.jerryt92.jrag.model.Translator;
import io.github.jerryt92.jrag.model.security.SessionBo;
import io.github.jerryt92.jrag.server.api.ChatApi;
import io.github.jerryt92.jrag.service.llm.ChatContextBo;
import io.github.jerryt92.jrag.service.llm.ChatContextService;
import io.github.jerryt92.jrag.service.llm.ChatService;
import io.github.jerryt92.jrag.service.security.LoginService;
import io.github.jerryt92.jrag.utils.UUIDUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@RestController
@Qualifier("jrag.alive.checker")
@AutoRegisterWebSocketHandler(path = "/ws/jrag/chat/alive", allowedOrigin = "*")
public class ChatController extends AbstractWebSocketHandler implements ChatApi {
    private final ChatContextService chatContextService;
    private final ChatService chatService;
    private final LoginService loginService;

    public ChatController(ChatContextService chatContextService, ChatService chatService, LoginService loginService) {
        this.chatContextService = chatContextService;
        this.chatService = chatService;
        this.loginService = loginService;
    }

    @PostMapping("/v1/rest/jrag/chat")
    public SseEmitter chat(@RequestBody ChatRequestDto chatRequestDto) {
        SseEmitter sseEmitter = new SseEmitter(120000L);
        try {
            sseEmitter.send(new ChatResponseDto());
        } catch (IOException e) {
            sseEmitter.completeWithError(e);
        }
        SessionBo session = loginService.getSession();
        SseCallback sseCallback = new SseCallback(
                UUIDUtil.randomUUID(),
                chatResponse -> {
                    try {
                        sseEmitter.send(chatResponse);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                sseEmitter::complete,
                sseEmitter::completeWithError,
                sseEmitter::complete
        );
        sseEmitter.onCompletion(() -> sseCallback.onSseCompletion.run());
        sseEmitter.onTimeout(() -> sseCallback.onSseTimeout.run());
        sseEmitter.onError((t) -> sseCallback.onSseError.accept(t));
        Thread.startVirtualThread(() -> chatService.handleChat(sseCallback, chatRequestDto, session == null ? null : session.getUserId()));
        return sseEmitter;
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
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        String contextId = getParam("context-id", Objects.requireNonNull(session.getUri()).toString());
        if (contextId != null) {
            chatService.interruptChat(contextId);
        }
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
