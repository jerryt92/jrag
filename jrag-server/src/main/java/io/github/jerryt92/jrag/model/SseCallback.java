package io.github.jerryt92.jrag.model;

import java.util.function.Consumer;

public class SseCallback {
    public final String subscriptionId;
    public final Consumer<ChatResponseDto> responseCall;
    public final Runnable completeCall;
    public final Consumer<Throwable> errorCall;
    public final Runnable timeoutCall;
    public Runnable onSseCompletion;
    public Runnable onSseTimeout;
    public Consumer<Throwable> onSseError;

    public SseCallback(String subscriptionId, Consumer<ChatResponseDto> responseCall, Runnable completeCall, Consumer<Throwable> errorCall, Runnable timeoutCall) {
        this.subscriptionId = subscriptionId;
        this.responseCall = responseCall;
        this.completeCall = completeCall;
        this.errorCall = errorCall;
        this.timeoutCall = timeoutCall;
    }
}
