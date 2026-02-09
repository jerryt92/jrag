package io.github.jerryt92.jrag.service.llm.client;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.github.jerryt92.jrag.config.LlmProperties;
import io.github.jerryt92.jrag.model.ChatCallback;
import io.github.jerryt92.jrag.model.ChatModel;
import io.github.jerryt92.jrag.model.FunctionCallingModel;
import io.github.jerryt92.jrag.utils.LlmBaseUtils;
import io.github.jerryt92.jrag.utils.SmartJsonFixer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.CollectionUtils;
import reactor.core.Disposable;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class OpenAiClient extends LlmClient {

    private final OpenAiApi openAiApi;

    public OpenAiClient(LlmProperties llmProperties) {
        super(llmProperties);
        this.openAiApi = OpenAiApi.builder()
                .baseUrl(llmProperties.openAiBaseUrl)
                .apiKey(StringUtils.isBlank(llmProperties.openAiKey) ? new NoopApiKey() : new SimpleApiKey(llmProperties.openAiKey))
                .completionsPath(llmProperties.completionsPath)
                // not used by this client directly, but required by OpenAiApi
                .embeddingsPath("/v1/embeddings")
                .webClientBuilder(org.springframework.web.reactive.function.client.WebClient.builder()
                        .clientConnector(new ReactorClientHttpConnector(HttpClient.create().protocol(HttpProtocol.HTTP11))))
                .build();
    }

    private final Map<String, ChatModel.ToolCallFunction> functionCallingInfoMap = new HashMap<>();

    @Override
    public Disposable chat(ChatModel.ChatRequest chatRequest, ChatCallback<ChatModel.ChatResponse> chatCallback) {
        List<OpenAiApi.ChatCompletionMessage> messagesContext = new ArrayList<>();
        for (ChatModel.Message chatMessage : chatRequest.getMessages()) {
            switch (chatMessage.getRole()) {
                case SYSTEM:
                    messagesContext.add(new OpenAiApi.ChatCompletionMessage(chatMessage.getContent(), OpenAiApi.ChatCompletionMessage.Role.SYSTEM));
                    break;
                case USER:
                    messagesContext.add(new OpenAiApi.ChatCompletionMessage(chatMessage.getContent(), OpenAiApi.ChatCompletionMessage.Role.USER));
                    break;
                case ASSISTANT:
                    // Assistant may include tool calls
                    if (!CollectionUtils.isEmpty(chatMessage.getToolCalls())) {
                        List<OpenAiApi.ChatCompletionMessage.ToolCall> toolCalls = new ArrayList<>();
                        for (ChatModel.ToolCall toolCall : chatMessage.getToolCalls()) {
                            ChatModel.ToolCallFunction fn = toolCall.getFunction();
                            String argsJson = fn == null ? null : ModelOptionsUtils.toJsonString(fn.getArguments());
                            OpenAiApi.ChatCompletionMessage.ChatCompletionFunction openAiFn =
                                    new OpenAiApi.ChatCompletionMessage.ChatCompletionFunction(fn == null ? null : fn.getName(), argsJson);
                            toolCalls.add(new OpenAiApi.ChatCompletionMessage.ToolCall(
                                    fn == null ? null : fn.getIndex(),
                                    fn == null ? null : fn.getId(),
                                    "function",
                                    openAiFn
                            ));
                        }
                        messagesContext.add(new OpenAiApi.ChatCompletionMessage(
                                chatMessage.getContent(),
                                OpenAiApi.ChatCompletionMessage.Role.ASSISTANT,
                                null,
                                null,
                                toolCalls,
                                null,
                                null,
                                null,
                                null
                        ));
                    } else {
                        messagesContext.add(new OpenAiApi.ChatCompletionMessage(chatMessage.getContent(), OpenAiApi.ChatCompletionMessage.Role.ASSISTANT));
                    }
                    break;
                case TOOL:
                    messagesContext.add(new OpenAiApi.ChatCompletionMessage(
                            chatMessage.getContent(),
                            OpenAiApi.ChatCompletionMessage.Role.TOOL,
                            null,
                            chatMessage.getToolCallId(),
                            null,
                            null,
                            null,
                            null,
                            null
                    ));
                    break;
            }
        }
        List<OpenAiApi.FunctionTool> openAiTools = null;
        if (!CollectionUtils.isEmpty(chatRequest.getTools())) {
            openAiTools = new ArrayList<>();
            for (FunctionCallingModel.Tool tool : chatRequest.getTools()) {
                OpenAiApi.FunctionTool.Function function = new OpenAiApi.FunctionTool.Function(
                        tool.getDescription(),
                        tool.getName(),
                        FunctionCallingModel.generateToolParameters(tool.getParameters()),
                        true);
                openAiTools.add(new OpenAiApi.FunctionTool(OpenAiApi.FunctionTool.Type.FUNCTION, function));
            }
        }
        OpenAiApi.ChatCompletionRequest request = new OpenAiApi.ChatCompletionRequest(
                messagesContext,
                llmProperties.openAiModelName,
                null, // store
                null, // metadata
                null, // frequencyPenalty
                null, // logitBias
                null, // logprobs
                null, // topLogprobs
                null, // maxTokens
                llmProperties.openAiContextLength, // maxCompletionTokens
                null, // n
                null, // outputModalities
                null, // audioParameters
                null, // presencePenalty
                null, // responseFormat
                null, // seed
                null, // serviceTier
                null, // stop
                true, // stream
                null, // streamOptions
                llmProperties.temperature,
                null, // topP
                openAiTools,
                null, // toolChoice
                null, // parallelToolCalls
                null, // user
                null, // reasoningEffort
                null, // webSearchOptions
                null, // verbosity
                null, // promptCacheKey
                null, // safetyIdentifier
                null  // extraBody
        );
        // Debug
//        log.info(ModelOptionsUtils.toJsonString(request));
        log.info("context length:{}", ModelOptionsUtils.toJsonString(request).length());
        return openAiApi.chatCompletionStream(request)
                .doOnError(t -> {
                    functionCallingInfoMap.remove(chatCallback.subscriptionId);
                    chatCallback.errorCall.accept(t);
                })
                .doOnComplete(() -> {
                    ChatModel.ToolCallFunction toolCallFunction = functionCallingInfoMap.remove(chatCallback.subscriptionId);
                    if (toolCallFunction != null) {
                        // function calling completed - emit tool call result message
                        finalizeToolCall(toolCallFunction, chatCallback);
                    } else {
                        // normal chat completed
                        ChatModel.ChatResponse chatResponse = new ChatModel.ChatResponse()
                                .setMessage(new ChatModel.Message()
                                        .setRole(ChatModel.Role.ASSISTANT)
                                        .setContent(""))
                                .setDone(true);
                        chatCallback.responseCall.accept(chatResponse);
                    }
                    chatCallback.completeCall.run();
                })
                .subscribe(
                        chunk -> this.consumeResponse(chunk, chatCallback),
                        t -> {
                            // error already forwarded via doOnError; prevent ErrorCallbackNotImplemented
                        }
                );
    }

    private void consumeResponse(OpenAiApi.ChatCompletionChunk chunk, ChatCallback<ChatModel.ChatResponse> chatCallback) {
        try {
            ChatModel.ToolCallFunction toolCallFunction = functionCallingInfoMap.get(chatCallback.subscriptionId);
            if (chunk == null || CollectionUtils.isEmpty(chunk.choices())) {
                return;
            }
            for (OpenAiApi.ChatCompletionChunk.ChunkChoice chunkChoice : chunk.choices()) {
                OpenAiApi.ChatCompletionMessage delta = chunkChoice.delta();
                if (delta == null) {
                    continue;
                }
                if (!CollectionUtils.isEmpty(delta.toolCalls())) {
                    // function calling chunks
                    if (toolCallFunction == null) {
                        toolCallFunction = new ChatModel.ToolCallFunction();
                        toolCallFunction.setArgumentsStream(new StringBuilder());
                        functionCallingInfoMap.put(chatCallback.subscriptionId, toolCallFunction);
                    }
                    for (OpenAiApi.ChatCompletionMessage.ToolCall openAiToolCall : delta.toolCalls()) {
                        if (openAiToolCall.function() != null) {
                            if (StringUtils.isNotBlank(openAiToolCall.function().name())) {
                                toolCallFunction.setName(openAiToolCall.function().name());
                            }
                            if (openAiToolCall.function().arguments() != null) {
                                toolCallFunction.getArgumentsStream().append(openAiToolCall.function().arguments());
                            }
                        }
                        if (openAiToolCall.index() != null) {
                            toolCallFunction.setIndex(openAiToolCall.index());
                        }
                        if (openAiToolCall.id() != null) {
                            toolCallFunction.setId(openAiToolCall.id());
                        }
                    }
                } else {
                    // normal content chunk
                    String contentDelta = delta.content();
                    if (contentDelta != null && !contentDelta.isEmpty()) {
                        ChatModel.ChatResponse chatResponse = new ChatModel.ChatResponse()
                                .setMessage(new ChatModel.Message()
                                        .setRole(ChatModel.Role.ASSISTANT)
                                        .setContent(contentDelta))
                                .setDone(false)
                                .setDoneReason(chunkChoice.finishReason() == null ? null : chunkChoice.finishReason().toString());
                        chatCallback.responseCall.accept(chatResponse);
                    }
                }
            }
        } catch (Throwable t) {
            log.error("Error processing chat completion chunk.", t);
            chatCallback.errorCall.accept(t);
        }
    }

    private void finalizeToolCall(ChatModel.ToolCallFunction toolCallFunction, ChatCallback<ChatModel.ChatResponse> chatCallback) {
        try {
            String rawArgs = toolCallFunction.getArgumentsStream() == null ? "" : toolCallFunction.getArgumentsStream().toString();
            String finalArgumentsString = SmartJsonFixer.fix(rawArgs);
            List<Map<String, Object>> argumentMaps = new ArrayList<>();
            List<JSONObject> argumentJsons;
            try {
                // prefer array format (legacy behavior), fallback to single object
                argumentJsons = JSONArray.parseArray(finalArgumentsString, JSONObject.class);
                argumentMaps.addAll(argumentJsons);
            } catch (Exception ignore) {
                try {
                    JSONObject argumentJson = JSONObject.parseObject(finalArgumentsString);
                    if (argumentJson != null) {
                        argumentMaps.add(argumentJson);
                    }
                } catch (Exception e2) {
                    log.warn("Standard JSON parse failed, attempting LLM self-healing. Error: {}", e2.getMessage());
                    // 第二层尝试：LLM 自我修复 (Heavy Weapon)
                    try {
                        // 调用 LlmBaseUtils 进行修复
                        String llmFixedJson = LlmBaseUtils.jsonSyntaxFix(rawArgs);
                        log.info("LLM self-healing result: {}", llmFixedJson);

                        // 修复后再次解析
                        List<String> strings = JSONArray.parseArray(llmFixedJson, String.class);
                        for (String string : strings) {
                            argumentMaps.add(JSONObject.parseObject(string));
                        }
                    } catch (Exception llmEx) {
                        log.error("Critical: LLM self-healing also failed. Raw: {}", rawArgs, llmEx);
                        // 彻底失败，回调错误
                        chatCallback.errorCall.accept(llmEx);
                        return; // 结束
                    }
                }
            }
            toolCallFunction.setArguments(argumentMaps);
            ChatModel.ToolCall toolCall = new ChatModel.ToolCall().setFunction(toolCallFunction);
            ChatModel.ChatResponse chatResponse = new ChatModel.ChatResponse()
                    .setMessage(new ChatModel.Message()
                            .setRole(ChatModel.Role.ASSISTANT)
                            .setContent("")
                            .setToolCalls(List.of(toolCall)))
                    .setDone(true);
            chatCallback.responseCall.accept(chatResponse);
        } catch (Throwable t) {
            chatCallback.errorCall.accept(t);
        }
    }
}
