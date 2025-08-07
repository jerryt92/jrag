package io.github.jerryt92.jrag.model.openai;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.jerryt92.jrag.model.ModelOptionsUtils;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

public class OpenAIModel {
    public enum Role {
        /**
         * System message type used as instructions to the model.
         */
        @JsonProperty("system") SYSTEM,
        /**
         * User message type.
         */
        @JsonProperty("user") USER,
        /**
         * Assistant message type. Usually the response from the model.
         */
        @JsonProperty("assistant") ASSISTANT,
        /**
         * Tool message.
         */
        @JsonProperty("tool") TOOL
    }

    /**
     * The type of modality for the model completion.
     */
    public enum OutputModality {

        // @formatter:off
        @JsonProperty("audio")
        AUDIO,
        @JsonProperty("text")
        TEXT
        // @formatter:on

    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatCompletionRequest {
        // @formatter:off
        @JsonProperty("messages") List<ChatCompletionMessage> messages;
        @JsonProperty("model") String model;
        @JsonProperty("store") Boolean store;
        @JsonProperty("metadata") Object metadata;
        @JsonProperty("frequency_penalty") Double frequencyPenalty;
        @JsonProperty("logit_bias") Map<String, Integer> logitBias;
        @JsonProperty("logprobs") Boolean logprobs;
        @JsonProperty("top_logprobs") Integer topLogprobs;
        @JsonProperty("max_tokens") @Deprecated Integer maxTokens; // Use maxCompletionTokens instead
        @JsonProperty("max_completion_tokens") Integer maxCompletionTokens;
        @JsonProperty("n") Integer n;
        @JsonProperty("modalities") List<OutputModality> outputModalities;
        @JsonProperty("audio") AudioParameters audioParameters;
        @JsonProperty("presence_penalty") Double presencePenalty;
        @JsonProperty("response_format") ResponseFormat responseFormat;
        @JsonProperty("seed") Integer seed;
        @JsonProperty("service_tier") String serviceTier;
        @JsonProperty("stop") List<String> stop;
        @JsonProperty("stream") Boolean stream;
        @JsonProperty("stream_options") StreamOptions streamOptions;
        @JsonProperty("temperature") Double temperature;
        @JsonProperty("top_p") Double topP;
        @JsonProperty("tools") List<FunctionTool> tools;
        @JsonProperty("tool_choice") Object toolChoice;
        @JsonProperty("parallel_tool_calls") Boolean parallelToolCalls;
        @JsonProperty("user") String user;
        @Data
        @Accessors(chain = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class AudioParameters {
            @JsonProperty("voice") Voice voice;
            @JsonProperty("format") AudioResponseFormat format;
            /**
             * Specifies the voice type.
             */
            public enum Voice {
                /** Alloy voice */
                @JsonProperty("alloy") ALLOY,
                /** Echo voice */
                @JsonProperty("echo") ECHO,
                /** Fable voice */
                @JsonProperty("fable") FABLE,
                /** Onyx voice */
                @JsonProperty("onyx") ONYX,
                /** Nova voice */
                @JsonProperty("nova") NOVA,
                /** Shimmer voice */
                @JsonProperty("shimmer") SHIMMER
            }

            /**
             * Specifies the output audio format.
             */
            public enum AudioResponseFormat {
                /** MP3 format */
                @JsonProperty("mp3") MP3,
                /** FLAC format */
                @JsonProperty("flac") FLAC,
                /** OPUS format */
                @JsonProperty("opus") OPUS,
                /** PCM16 format */
                @JsonProperty("pcm16") PCM16,
                /** WAV format */
                @JsonProperty("wav") WAV
            }
        }
        @Data
        @Accessors(chain = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class StreamOptions {
            public StreamOptions(Boolean includeUsage) {
                this.includeUsage = includeUsage;
            }
            @JsonProperty("include_usage") Boolean includeUsage;
            public static StreamOptions INCLUDE_USAGE = new StreamOptions(true);
        }
    }
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatCompletionChunk {
        @JsonProperty("id") String id;
        @JsonProperty("choices") List<ChunkChoice> choices;
        @JsonProperty("created") Long created;
        @JsonProperty("model") String model;
        @JsonProperty("service_tier") String serviceTier;
        @JsonProperty("system_fingerprint") String systemFingerprint;
        @JsonProperty("object") String object;
        @JsonProperty("usage") Usage usage;

        @Data
        @Accessors(chain = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class ChunkChoice {
            @JsonProperty("finish_reason") ChatCompletionFinishReason finishReason;
            @JsonProperty("index") Integer index;
            @JsonProperty("delta") ChatCompletionMessage delta;
            @JsonProperty("logprobs") LogProbs logprobs;

        }

    }
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Usage {
        @JsonProperty("completion_tokens") Integer completionTokens;
        @JsonProperty("prompt_tokens") Integer promptTokens;
        @JsonProperty("total_tokens") Integer totalTokens;
        @JsonProperty("prompt_tokens_details") PromptTokensDetails promptTokensDetails;
        @JsonProperty("completion_tokens_details") CompletionTokenDetails completionTokenDetails;

        public Usage() {
            this(null, null, null, null, null);
        }

        public Usage(Integer completionTokens, Integer promptTokens, Integer totalTokens) {
            this(completionTokens, promptTokens, totalTokens, null, null);
        }

        public Usage(Integer completionTokens, Integer promptTokens, Integer totalTokens, Object o, Object o1) {
            this.completionTokens = completionTokens;
            this.promptTokens = promptTokens;
            this.totalTokens = totalTokens;
            this.promptTokensDetails = (PromptTokensDetails) o;
            this.completionTokenDetails = (CompletionTokenDetails) o1;
        }

        @Data
        @Accessors(chain = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class PromptTokensDetails {
            @JsonProperty("audio_tokens") Integer audioTokens;
            @JsonProperty("cached_tokens") Integer cachedTokens;
        }

        @Data
        @Accessors(chain = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class CompletionTokenDetails {
            @JsonProperty("reasoning_tokens") Integer reasoningTokens;
            @JsonProperty("accepted_prediction_tokens") Integer acceptedPredictionTokens;
            @JsonProperty("audio_tokens") Integer audioTokens;
            @JsonProperty("rejected_prediction_tokens") Integer rejectedPredictionTokens;
        }
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LogProbs {
        @JsonProperty("content") List<Content> content;
        @JsonProperty("refusal") List<Content> refusal;

        @Data
        @Accessors(chain = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Content {
            @JsonProperty("token") String token;
            @JsonProperty("logprob") Float logprob;
            @JsonProperty("bytes") List<Integer> probBytes;
            @JsonProperty("top_logprobs") List<TopLogProbs> topLogprobs;
            @Data
            @Accessors(chain = true)
            @JsonInclude(JsonInclude.Include.NON_NULL)
            public static class TopLogProbs {
                @JsonProperty("token") String token;
                @JsonProperty("logprob") Float logprob;
                @JsonProperty("bytes") List < Integer > probBytes;
            }
        }
    }

    /**
     * The reason the model stopped generating tokens.
     */
    public enum ChatCompletionFinishReason {

        /**
         * The model hit a natural stop point or a provided stop sequence.
         */
        @JsonProperty("stop")
        STOP,
        /**
         * The maximum number of tokens specified in the request was reached.
         */
        @JsonProperty("length")
        LENGTH,
        /**
         * The content was omitted due to a flag from our content filters.
         */
        @JsonProperty("content_filter")
        CONTENT_FILTER,
        /**
         * The model called a tool.
         */
        @JsonProperty("tool_calls")
        TOOL_CALLS,
        /**
         * Only for compatibility with Mistral AI API.
         */
        @JsonProperty("tool_call")
        TOOL_CALL

    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatCompletionMessage {
        @JsonProperty("content")
        Object rawContent;
        @JsonProperty("role")
        Role role;
        @JsonProperty("name")
        String name;
        @JsonProperty("tool_call_id")
        String toolCallId;
        @JsonProperty("tool_calls")
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        List<ToolCall> toolCalls;
        @JsonProperty("refusal")
        String refusal;
        @JsonProperty("audio")
        AudioOutput audioOutput;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionTool {

        /**
         * The type of the tool. Currently, only 'function' is supported.
         */
        @JsonProperty("type")
        private Type type = Type.FUNCTION;

        /**
         * The function definition.
         */
        @JsonProperty("function")
        private Function function;

        public FunctionTool() {

        }

        /**
         * Create a tool of type 'function' and the given function definition.
         *
         * @param type     the tool type
         * @param function function definition
         */
        public FunctionTool(Type type, Function function) {
            this.type = type;
            this.function = function;
        }

        /**
         * Create a tool of type 'function' and the given function definition.
         *
         * @param function function definition.
         */
        public FunctionTool(Function function) {
            this(Type.FUNCTION, function);
        }

        public Type getType() {
            return this.type;
        }

        public Function getFunction() {
            return this.function;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public void setFunction(Function function) {
            this.function = function;
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

        /**
         * Function definition.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Function {

            @JsonProperty("description")
            private String description;

            @JsonProperty("name")
            private String name;

            @JsonProperty("parameters")
            private Map<String, Object> parameters;

            @JsonProperty("strict")
            Boolean strict;

            @JsonIgnore
            private String jsonSchema;

            /**
             * NOTE: Required by Jackson, JSON deserialization!
             */
            @SuppressWarnings("unused")
            private Function() {
            }

            /**
             * Create tool function definition.
             *
             * @param description A description of what the function does, used by the
             *                    model to choose when and how to call the function.
             * @param name        The name of the function to be called. Must be a-z, A-Z, 0-9,
             *                    or contain underscores and dashes, with a maximum length of 64.
             * @param parameters  The parameters the functions accepts, described as a JSON
             *                    Schema object. To describe a function that accepts no parameters, provide
             *                    the value {"type": "object", "properties": {}}.
             * @param strict      Whether to enable strict schema adherence when generating the
             *                    function call. If set to true, the model will follow the exact schema
             *                    defined in the parameters field. Only a subset of JSON Schema is supported
             *                    when strict is true.
             */
            public Function(String description, String name, Map<String, Object> parameters, Boolean strict) {
                this.description = description;
                this.name = name;
                this.parameters = parameters;
                this.strict = strict;
            }

            /**
             * Create tool function definition.
             *
             * @param description tool function description.
             * @param name        tool function name.
             * @param jsonSchema  tool function schema as json.
             */
            public Function(String description, String name, String jsonSchema) {
                this(description, name, ModelOptionsUtils.jsonToMap(jsonSchema), null);
            }

            public String getDescription() {
                return this.description;
            }

            public String getName() {
                return this.name;
            }

            public Map<String, Object> getParameters() {
                return this.parameters;
            }

            public void setDescription(String description) {
                this.description = description;
            }

            public void setName(String name) {
                this.name = name;
            }

            public void setParameters(Map<String, Object> parameters) {
                this.parameters = parameters;
            }

            public Boolean getStrict() {
                return this.strict;
            }

            public void setStrict(Boolean strict) {
                this.strict = strict;
            }

            public String getJsonSchema() {
                return this.jsonSchema;
            }

            public void setJsonSchema(String jsonSchema) {
                this.jsonSchema = jsonSchema;
                if (jsonSchema != null) {
                    this.parameters = ModelOptionsUtils.jsonToMap(jsonSchema);
                }
            }

        }

    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolCall {
        @JsonProperty("index")
        Integer index;
        @JsonProperty("id")
        String id;
        @JsonProperty("type")
        String type;
        @JsonProperty("function")
        ChatCompletionFunction function;
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChatCompletionFunction {
        @JsonProperty("name")
        String name;
        @JsonProperty("arguments")
        String arguments;
    }

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AudioOutput {
        @JsonProperty("id")
        String id;
        @JsonProperty("data")
        String data;
        @JsonProperty("expires_at")
        Long expiresAt;
        @JsonProperty("transcript")
        String transcript;
    }

    /**
     * 嵌入请求对象
     * @param <T>
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EmbeddingRequest<T> {
        @JsonProperty("input")
        T input;
        @JsonProperty("model")
        String model;
        @JsonProperty("encoding_format")
        String encodingFormat;
        @JsonProperty("dimensions")
        Integer dimensions;
        @JsonProperty("user")
        String user;
    }

    /**
     * 嵌入响应对象
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EmbeddingList {
        @JsonProperty("object")
        String object;
        @JsonProperty("data")
        List<Embedding> data;
        @JsonProperty("model")
        String model;
        @JsonProperty("usage")
        Usage usage;
    }

    /**
     * 嵌入对象
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Embedding {
        @JsonProperty("index")
        Integer index;
        @JsonProperty("embedding")
        float[] embedding;
        @JsonProperty("object")
        String object;
    }
}
