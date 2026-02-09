package io.github.jerryt92.jrag.service.llm.client;

import io.github.jerryt92.jrag.config.LlmProperties;
import io.github.jerryt92.jrag.model.ChatCallback;
import io.github.jerryt92.jrag.model.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public abstract class LlmClient {
    protected final LlmProperties llmProperties;

    protected LlmClient(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    /**
     * @param chatRequest
     * @param chatCallback
     * @return Disposable 将订阅关系返回，以便在需要时取消订阅
     */
    public abstract Disposable chat(ChatModel.ChatRequest chatRequest, ChatCallback<ChatModel.ChatResponse> chatCallback);

    /**
     * 同步接口
     *
     * @param chatRequest
     * @return ChatModel.ChatResponse
     */
    public ChatModel.ChatResponse syncChat(ChatModel.ChatRequest chatRequest) {
        // 1. 创建 Future 用于阻塞等待最终结果
        CompletableFuture<ChatModel.ChatResponse> resultFuture = new CompletableFuture<>();
        // 2. 准备累加器，用于拼凑流式返回的内容
        StringBuilder fullContent = new StringBuilder();
        List<ChatModel.ToolCall> finalToolCalls = new ArrayList<>();
        // 使用数组仅仅为了在 lambda 中能修改引用（或者使用 AtomicReference）
        String[] finishReason = new String[1];
        // 3. 生成本次会话的 ID
        String subscriptionId = UUID.randomUUID().toString();
        ChatCallback<ChatModel.ChatResponse> callback = new ChatCallback<>(subscriptionId);
        // 4. 定义流式响应的处理逻辑 (Accumulator 模式)
        callback.responseCall = chatResponse -> {
            ChatModel.Message msg = chatResponse.getMessage();
            if (msg != null) {
                // 情况 A: 累积文本内容 (流式返回时是一段一段的)
                if (StringUtils.isNotBlank(msg.getContent())) {
                    fullContent.append(msg.getContent());
                }
                // 情况 B: 获取工具调用 (根据你的 consumeResponse 逻辑，ToolCall 是在 [DONE] 时一次性完整返回的)
                if (!CollectionUtils.isEmpty(msg.getToolCalls())) {
                    finalToolCalls.addAll(msg.getToolCalls());
                }
            }
            // 记录结束原因
            if (chatResponse.getDoneReason() != null) {
                finishReason[0] = chatResponse.getDoneReason();
            }
        };
        // 5. 定义完成时的逻辑：组装最终对象并解除阻塞
        callback.completeCall = () -> {
            ChatModel.ChatResponse finalResponse = new ChatModel.ChatResponse();
            ChatModel.Message message = new ChatModel.Message();
            message.setRole(ChatModel.Role.ASSISTANT);
            // 设置累积后的完整文本
            message.setContent(fullContent.toString());
            // 设置收集到的工具调用
            if (!finalToolCalls.isEmpty()) {
                message.setToolCalls(finalToolCalls);
            }
            finalResponse.setMessage(message);
            finalResponse.setDone(true);
            finalResponse.setDoneReason(finishReason[0]);
            // 通知 Future 完成
            resultFuture.complete(finalResponse);
        };
        // 6. 定义错误处理
        callback.errorCall = resultFuture::completeExceptionally;
        // 7. 发起异步调用
        Disposable[] disposable = new Disposable[1];
        Thread.startVirtualThread(() -> {
            disposable[0] = this.chat(chatRequest, callback);
        });
        try {
            // 8. 阻塞等待结果 (建议设置一个合理的超时时间，例如 60-120 秒，取决于模型复杂度)
            return resultFuture.get(120, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.error("Sync chat execution failed", e);
            throw new RuntimeException("Sync chat request failed or timed out", e);
        } finally {
            // 9. 确保资源清理，防止连接泄漏
            if (disposable[0] != null && !disposable[0].isDisposed()) {
                disposable[0].dispose();
            }
        }
    }
}
