package io.github.jerryt92.jrag.config;

import io.github.jerryt92.jrag.service.PropertiesService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LlmProperties {
    public String llmProvider;
    public Boolean useRag;
    public Boolean useTools;
    public String ollamaModelName;
    public String ollamaBaseUrl;
    public int ollamaKeepAliveSeconds;
    public int ollamaContextLength;
    public String ollamaKey;
    public String openAiModelName;
    public String completionsPath;
    public String openAiBaseUrl;
    public String openAiKey;
    public int openAiContextLength;
    public double temperature;

    private final PropertiesService propertiesService;

    // Keys in table `ai_properties.property_name`
    private static final String KEY_LLM_PROVIDER = "llm-provider";
    private static final String KEY_USE_RAG = "llm-use-rag";
    private static final String KEY_USE_TOOLS = "llm-use-tools";
    private static final String KEY_TEMPERATURE = "llm-temperature";
    private static final String KEY_OLLAMA_MODEL_NAME = "llm-ollama-model-name";
    private static final String KEY_OLLAMA_BASE_URL = "llm-ollama-base-url";
    private static final String KEY_OLLAMA_KEEP_ALIVE_SECONDS = "llm-ollama-keep-alive-seconds";
    private static final String KEY_OLLAMA_CONTEXT_LENGTH = "llm-ollama-context-length";
    private static final String KEY_OLLAMA_KEY = "llm-ollama-key";
    private static final String KEY_OPEN_AI_MODEL_NAME = "llm-open-ai-model-name";
    private static final String KEY_OPEN_AI_BASE_URL = "llm-open-ai-base-url";
    private static final String KEY_OPEN_AI_COMPLETIONS_PATH = "llm-open-ai-completions-path";
    private static final String KEY_OPEN_AI_KEY = "llm-open-ai-key";
    private static final String KEY_OPEN_AI_CONTEXT_LENGTH = "llm-open-ai-context-length";

    public LlmProperties(PropertiesService propertiesService) {
        this.propertiesService = propertiesService;
    }

    @PostConstruct
    public void init() {
        reloadFromDb();
    }

    /**
     * Load all LLM-related config from database table `ai_properties`.
     * This can be reloaded at runtime when properties change.
     */
    public void reloadFromDb() {
        this.temperature = readDouble(KEY_TEMPERATURE, 0d);
        this.llmProvider = readString(KEY_LLM_PROVIDER, "open-ai");
        this.useRag = readBoolean(KEY_USE_RAG, true);
        this.useTools = readBoolean(KEY_USE_TOOLS, true);

        this.ollamaModelName = readString(KEY_OLLAMA_MODEL_NAME, "qwen3:14b-q8_0");
        this.ollamaBaseUrl = readString(KEY_OLLAMA_BASE_URL, "http://172.16.8.107:11434");
        this.ollamaKeepAliveSeconds = readInt(KEY_OLLAMA_KEEP_ALIVE_SECONDS, 3600);
        this.ollamaContextLength = readInt(KEY_OLLAMA_CONTEXT_LENGTH, 32768);
        this.ollamaKey = readString(KEY_OLLAMA_KEY, "");

        this.openAiModelName = readString(KEY_OPEN_AI_MODEL_NAME, "qwen-plus");
        this.openAiBaseUrl = readString(KEY_OPEN_AI_BASE_URL, "https://dashscope.aliyuncs.com");
        this.completionsPath = readString(KEY_OPEN_AI_COMPLETIONS_PATH, "/compatible-mode/v1/chat/completions");
        this.openAiKey = readString(KEY_OPEN_AI_KEY, "");
        this.openAiContextLength = readInt(KEY_OPEN_AI_CONTEXT_LENGTH, 32768);
    }

    private String readString(String key, String defaultValue) {
        try {
            String v = propertiesService.getProperty(key);
            return v == null ? defaultValue : v;
        } catch (Exception e) {
            log.warn("Read ai_properties failed for key={}, using default.", key, e);
            return defaultValue;
        }
    }

    private boolean readBoolean(String key, boolean defaultValue) {
        String v = readString(key, null);
        if (v == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
    }

    private int readInt(String key, int defaultValue) {
        String v = readString(key, null);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            log.warn("Invalid int in ai_properties key={}, value={}, using default={}", key, v, defaultValue);
            return defaultValue;
        }
    }

    private double readDouble(String key, double defaultValue) {
        String v = readString(key, null);
        if (v == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(v.trim());
        } catch (Exception e) {
            log.warn("Invalid double in ai_properties key={}, value={}, using default={}", key, v, defaultValue);
            return defaultValue;
        }
    }
}
