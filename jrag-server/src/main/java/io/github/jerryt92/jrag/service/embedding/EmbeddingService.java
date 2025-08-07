package io.github.jerryt92.jrag.service.embedding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.jerryt92.jrag.config.EmbeddingProperties;
import io.github.jerryt92.jrag.model.EmbeddingModel;
import io.github.jerryt92.jrag.model.ollama.OllamaModel;
import io.github.jerryt92.jrag.model.openai.OpenAIModel;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class EmbeddingService {
    private final EmbeddingProperties embeddingProperties;

    private final OkHttpClient okHttpClient;

    private final String embeddingsPath;

    public EmbeddingService(@Autowired EmbeddingProperties embeddingProperties) {
        this.embeddingProperties = embeddingProperties;
        switch (embeddingProperties.embeddingProvider) {
            case "open-ai":
                this.okHttpClient = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS)
                        .build();
                this.embeddingsPath = embeddingProperties.embeddingsPath;
                break;
            case "ollama":
            default:
                this.okHttpClient = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS)
                        .build();
                this.embeddingsPath = "/api/embed";
                break;
        }
    }

    public EmbeddingModel.EmbeddingsResponse embed(EmbeddingModel.EmbeddingsRequest embeddingsRequest) {
        EmbeddingModel.EmbeddingsResponse embeddingsResponse = null;
        List<EmbeddingModel.EmbeddingsItem> embeddingsItems = new ArrayList<>();
        // 构建 JSON 请求体
        ObjectMapper mapper = new ObjectMapper();
        String jsonBody = null;
        switch (embeddingProperties.embeddingProvider) {
            case "open-ai":
                OpenAIModel.EmbeddingRequest<List<String>> openAIEmbeddingsRequest = new OpenAIModel.EmbeddingRequest<List<String>>()
                        .setModel(embeddingProperties.openAiModelName)
                        .setInput(embeddingsRequest.getInput());
                try {
                    jsonBody = mapper.writeValueAsString(openAIEmbeddingsRequest);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                RequestBody openAiBody = RequestBody.create(MediaType.get("application/json"), jsonBody);
                Request request = new Request.Builder()
                        .url(embeddingProperties.openAiBaseUrl + embeddingsPath)
                        .post(openAiBody)
                        .build();
                try {
                    Response response = okHttpClient.newCall(request).execute();
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        OpenAIModel.EmbeddingList openAIEmbeddingsResponse = mapper.readValue(responseBody, OpenAIModel.EmbeddingList.class);
                        if (openAIEmbeddingsResponse != null) {
                            for (int i = 0; i < openAIEmbeddingsResponse.getData().size(); i++) {
                                embeddingsItems.add(new EmbeddingModel.EmbeddingsItem()
                                        .setEmbeddingProvider(embeddingProperties.embeddingProvider)
                                        .setEmbeddingModel(embeddingProperties.openAiModelName)
                                        .setText(embeddingsRequest.getInput().get(i))
                                        .setEmbeddings(openAIEmbeddingsResponse.getData().get(i).getEmbedding()));
                            }
                        }
                    } else {
                        log.error("OpenAI API 返回错误状态码: {}", response.code());
                    }
                } catch (IOException e) {
                    log.error("调用 OpenAI 嵌入模型失败", e);
                }
                embeddingsResponse = new EmbeddingModel.EmbeddingsResponse().setData(embeddingsItems);
                break;
            case "ollama":
            default:
                OllamaModel.EmbeddingsRequest ollamaEmbeddingsRequest =
                        new OllamaModel.EmbeddingsRequest(embeddingProperties.ollamaModelName)
                                .setInput(embeddingsRequest.getInput())
                                .setKeepAlive(embeddingProperties.keepAliveSeconds);
                try {
                    jsonBody = mapper.writeValueAsString(ollamaEmbeddingsRequest);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                RequestBody ollamaBody = RequestBody.create(MediaType.get("application/json"), jsonBody);
                Request ollamaRequest = new Request.Builder()
                        .url(embeddingProperties.ollamaBaseUrl + embeddingsPath)
                        .post(ollamaBody)
                        .build();
                try {
                    Response response = okHttpClient.newCall(ollamaRequest).execute();
                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        OllamaModel.EmbeddingsResponse ollamaEmbeddingsResponse = mapper.readValue(responseBody, OllamaModel.EmbeddingsResponse.class);

                        if (ollamaEmbeddingsResponse != null) {
                            for (int i = 0; i < ollamaEmbeddingsResponse.getEmbeddings().size(); i++) {
                                embeddingsItems.add(new EmbeddingModel.EmbeddingsItem()
                                        .setEmbeddingProvider(embeddingProperties.embeddingProvider)
                                        .setEmbeddingModel(embeddingProperties.ollamaModelName)
                                        .setText(embeddingsRequest.getInput().get(i))
                                        .setEmbeddings(ollamaEmbeddingsResponse.getEmbeddings().get(i)));
                            }
                        }
                    } else {
                        log.error("Ollama API 返回错误状态码: {}", response.code());
                    }
                } catch (IOException e) {
                    log.error("调用 Ollama 嵌入模型失败", e);
                }
                embeddingsResponse = new EmbeddingModel.EmbeddingsResponse().setData(embeddingsItems);
                break;
        }
        return embeddingsResponse;
    }
}
