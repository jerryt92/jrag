package io.github.jerryt92.jrag.model.ollama;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class OllamaModel {
    /**
     * Chat request object.
     * <p>
     * model The model to use for completion. It should be a name familiar to Ollama from the <a href="https://ollama.com/library">Library</a>.
     * messages The list of messages in the chat. This can be used to keep a chat memory.
     * stream Whether to stream the response. If false, the response will be returned as a single response object rather than a stream of objects.
     * format The format to return the response in. Currently, the only accepted value is "json".
     * keepAlive Controls how long the model will stay loaded into memory following this request (default: 5m).
     * tools List of tools the model has access to.
     * options Model-specific options. For example, "temperature" can be set through this field, if the model supports it.
     *
     * @see <a href=
     * "https://github.com/ollama/ollama/blob/main/docs/api.md#generate-a-chat-completion">Chat
     * Completion API</a>
     * @see <a href="https://github.com/ollama/ollama/blob/main/api/types.go">Ollama
     * Types</a>
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatRequest {
        @JsonProperty("model")
        String model;
        @JsonProperty("messages")
        List<Message> messages;
        @JsonProperty("stream")
        Boolean stream;
        @JsonProperty("format")
        String format;
        @JsonProperty("keepAlive")
        String keepAlive;
        @JsonProperty("tools")
        List<Tool> tools;
        @JsonProperty("options")
        Map<String, Object> options;
        @JsonProperty("think")
        private Boolean think;
    }

    /**
     * Ollama chat response object.
     * model The model used for generating the response.
     * createdAt The timestamp of the response generation.
     * message The response {@link Message} with {Message.Role#ASSISTANT}.
     * doneReason The reason the model stopped generating text.
     * done Whether this is the final response. For streaming response only the
     * last message is marked as done. If true, this response may be followed by another
     * response with the following, additional fields: context, prompt_eval_count,
     * prompt_eval_duration, eval_count, eval_duration.
     * totalDuration Time spent generating the response.
     * loadDuration Time spent loading the model.
     * promptEvalCount Number of tokens in the prompt.
     * promptEvalDuration Time spent evaluating the prompt.
     * evalCount Number of tokens in the response.
     * evalDuration Time spent generating the response.
     *
     * @see <a href=
     * "https://github.com/ollama/ollama/blob/main/docs/api.md#generate-a-chat-completion">Chat
     * Completion API</a>
     * @see <a href="https://github.com/ollama/ollama/blob/main/api/types.go">Ollama
     * Types</a>
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatResponse {
        @JsonProperty("model")
        String model;
        @JsonProperty("created_at")
        Instant createdAt;
        @JsonProperty("message")
        Message message;
        @JsonProperty("done_reason")
        String doneReason;
        @JsonProperty("done")
        Boolean done;
        @JsonProperty("total_duration")
        Long totalDuration;
        @JsonProperty("load_duration")
        Long loadDuration;
        @JsonProperty("prompt_eval_count")
        Integer promptEvalCount;
        @JsonProperty("prompt_eval_duration")
        Long promptEvalDuration;
        @JsonProperty("eval_count")
        Integer evalCount;
        @JsonProperty("eval_duration")
        Long evalDuration;
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        @JsonProperty("role")
        Role role;
        @JsonProperty("content")
        String content;
        @JsonProperty("images")
        List<String> images;
        @JsonProperty("tool_calls")
        List<ToolCall> toolCalls;
    }

    @Data
    @Accessors(chain = true)
    public static class ToolResponse {
        String name;
        String responseData;
    }

    public static Message buildToolResponseMessage(Collection<ToolResponse> toolResponses) {
        return new Message()
                .setRole(Role.TOOL)
                .setContent("ToolResponseMessage{" + "responses=" + toolResponses + ", messageType=tool}");
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Tool {
        @JsonProperty("type")
        Type type;
        @JsonProperty("function")
        Function function;
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Function {
        @JsonProperty("name")
        String name;
        @JsonProperty("description")
        String description;
        @JsonProperty("parameters")
        Map<String, Object> parameters;
    }

    /**
     * Create a tool of type 'function' and the given function definition.
     */
    public enum Type {
        /**
         * Function tool type.
         */
        @JsonProperty("function")
        FUNCTION
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolCall {
        @JsonProperty("function")
        ToolCallFunction function;
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolCallFunction {
        @JsonProperty("name")
        String name;
        @JsonProperty("arguments")
        Map<String, Object> arguments;
    }

    public enum Role {
        /**
         * System message type used as instructions to the model.
         */
        @JsonProperty("system")
        SYSTEM,
        /**
         * User message type.
         */
        @JsonProperty("user")
        USER,
        /**
         * Assistant message type. Usually the response from the model.
         */
        @JsonProperty("assistant")
        ASSISTANT,
        /**
         * Tool message.
         */
        @JsonProperty("tool")
        TOOL
    }

    /**
     * Generate embeddings from a model.
     * Returns error if false and context length is exceeded. Defaults to true.
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EmbeddingsRequest {
        @JsonProperty("model")
        String model;
        @JsonProperty("input")
        List<String> input;
        @JsonProperty("keep_alive")
        int keepAlive;
        @JsonProperty("options")
        Map<String, Object> options;
        @JsonProperty("truncate")
        Boolean truncate;

        /**
         * Shortcut constructor to create a EmbeddingRequest without options.
         *
         * @param model The name of model to generate embeddings from.
         */
        public EmbeddingsRequest(String model) {
            this.model = model;
        }
    }

    /**
     * The response object returned from the /embedding endpoint.
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EmbeddingsResponse {
        @JsonProperty("model")
        String model;
        @JsonProperty("embeddings")
        List<float[]> embeddings;
        @JsonProperty("total_duration")
        Long totalDuration;
        @JsonProperty("load_duration")
        Long loadDuration;
        @JsonProperty("prompt_eval_count")
        Integer promptEvalCount;
    }
}
