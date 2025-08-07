package io.github.jerryt92.jrag.controller;

import io.github.jerryt92.jrag.config.annotation.AutoRegisterWebSocketHandler;
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
        Thread chatThread = new Thread(() -> chatService.handleChat(sseEmitter, chatRequestDto, session == null ? null : session.getUserId()));
        Thread.startVirtualThread(chatThread);
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
            String[] params = url.split("&");
            for (String p : params) {
                String[] k = p.split("=");
                if (k[0].equals(param)) {
                    return k[1];
                }
            }
        }
        return null;
    }
}
