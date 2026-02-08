package io.github.jerryt92.jrag.config;

import io.github.jerryt92.jrag.service.PropertiesService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmbeddingProperties {
    public String embeddingProvider;
    public String ollamaModelName;
    public String ollamaBaseUrl;
    public int keepAliveSeconds;
    public String ollamaKey;
    public String openAiModelName;
    public String openAiBaseUrl;
    public String embeddingsPath;
    public String openAiKey;

    private final PropertiesService propertiesService;
    // Keys in table `ai_properties.property_name`
    private static final String KEY_PROVIDER = "embedding-provider";
    private static final String KEY_OLLAMA_MODEL_NAME = "embedding-ollama-model-name";
    private static final String KEY_OLLAMA_BASE_URL = "embedding-ollama-base-url";
    private static final String KEY_OLLAMA_KEEP_ALIVE_SECONDS = "embedding-ollama-keep_alive_seconds";
    private static final String KEY_OLLAMA_KEY = "embedding-ollama-key";

    private static final String KEY_OPEN_AI_MODEL_NAME = "embedding-open-ai-model-name";
    private static final String KEY_OPEN_AI_BASE_URL = "embedding-open-ai-base-url";
    private static final String KEY_OPEN_AI_EMBEDDINGS_PATH = "embedding-open-ai-embeddings-path";
    private static final String KEY_OPEN_AI_KEY = "embedding-open-ai-key";

    public EmbeddingProperties(PropertiesService propertiesService) {
        this.propertiesService = propertiesService;
    }

    @PostConstruct
    public void init() {
        reloadFromDb();
    }

    /**
     * Load all embedding-related config from database table `ai_properties`.
     * This can be reloaded at runtime when properties change.
     */
    public void reloadFromDb() {
        this.embeddingProvider = readString(KEY_PROVIDER, "open-ai");

        this.ollamaModelName = readString(KEY_OLLAMA_MODEL_NAME, "nomic-embed-text:latest");
        this.ollamaBaseUrl = readString(KEY_OLLAMA_BASE_URL, "http://127.0.0.1:11434");
        this.keepAliveSeconds = readInt(KEY_OLLAMA_KEEP_ALIVE_SECONDS, 3600);
        this.ollamaKey = readString(KEY_OLLAMA_KEY, "");

        this.openAiModelName = readString(KEY_OPEN_AI_MODEL_NAME, "text-embedding-v4");
        this.openAiBaseUrl = readString(KEY_OPEN_AI_BASE_URL, "https://dashscope.aliyuncs.com");
        this.embeddingsPath = readString(KEY_OPEN_AI_EMBEDDINGS_PATH, "/compatible-mode/v1/embeddings");
        this.openAiKey = readString(KEY_OPEN_AI_KEY, "");
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
}
