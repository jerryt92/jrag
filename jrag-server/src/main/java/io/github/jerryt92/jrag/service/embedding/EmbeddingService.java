package io.github.jerryt92.jrag.service.embedding;

import io.github.jerryt92.jrag.config.EmbeddingProperties;
import io.github.jerryt92.jrag.model.EmbeddingModel;
import io.github.jerryt92.jrag.utils.HashUtil;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class EmbeddingService {
    // 用于标记数据的嵌入模型
    @Getter
    private String checkEmbeddingHash;
    @Getter
    private Integer dimension;
    public final EmbeddingModel.EmbeddingsRequest checkEmbeddingsRequest = new EmbeddingModel.EmbeddingsRequest().setInput(List.of("test"));
    private final EmbeddingProperties embeddingProperties;
    private volatile WebClient webClient;
    private volatile String embeddingsPath;

    private static String secondsToDurationString(int seconds) {
        // Ollama expects duration with a unit. We store keep-alive as seconds.
        return Math.max(seconds, 0) + "s";
    }

    public EmbeddingService(@Autowired EmbeddingProperties embeddingProperties) {
        this.embeddingProperties = embeddingProperties;
        rebuildClient();
    }

    public void rebuildClient() {
        SslContext sslContext;
        try {
            // 配置忽略 SSL 证书校验
            sslContext = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            SslContext finalSslContext = sslContext;

            // 创建 HttpClient
            HttpClient httpClient = HttpClient.create()
                    .secure(t -> t.sslContext(finalSslContext));

            // 根据配置构建 WebClient
            WebClient.Builder builder = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient));

            switch (embeddingProperties.embeddingProvider) {
                case "open-ai":
                    webClient = builder
                            .baseUrl(embeddingProperties.openAiBaseUrl)
                            .defaultHeader("Authorization", "Bearer " + embeddingProperties.openAiKey)
                            .build();
                    embeddingsPath = embeddingProperties.embeddingsPath;
                    break;
                case "ollama":
                default:
                    if (StringUtils.isNotBlank(embeddingProperties.ollamaKey)) {
                        builder.defaultHeader("Authorization", "Bearer " + embeddingProperties.ollamaKey);
                    }
                    webClient = builder.baseUrl(embeddingProperties.ollamaBaseUrl).build();
                    embeddingsPath = "/api/embed";
                    break;
            }
        } catch (SSLException e) {
            throw new RuntimeException(e);
        }
    }

    public void init() {
        try {
            // 检查嵌入模型是否变化
            EmbeddingModel.EmbeddingsResponse response = embed(checkEmbeddingsRequest);
            if (response != null && !response.getData().isEmpty()) {
                EmbeddingModel.EmbeddingsItem testEmbed = response.getData().getFirst();
                dimension = testEmbed.getEmbeddings().length;
                checkEmbeddingHash = HashUtil.getMessageDigest(Arrays.toString(testEmbed.getEmbeddings()).getBytes(), HashUtil.MdAlgorithm.SHA256);
            } else {
                log.warn("Init failed: Unable to fetch embedding for test input.");
            }
        } catch (WebClientResponseException | WebClientRequestException e) {
            log.warn("Init failed: Embedding service unavailable.", e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public EmbeddingModel.EmbeddingsResponse embed(EmbeddingModel.EmbeddingsRequest embeddingsRequest) {
        List<EmbeddingModel.EmbeddingsItem> embeddingsItems = new ArrayList<>();
        switch (embeddingProperties.embeddingProvider) {
            case "open-ai":
                handleOpenAIEmbeddings(embeddingsRequest, embeddingsItems);
                break;
            case "ollama":
            default:
                handleOllamaEmbeddings(embeddingsRequest, embeddingsItems);
                break;
        }

        return new EmbeddingModel.EmbeddingsResponse().setData(embeddingsItems);
    }

    private void handleOpenAIEmbeddings(EmbeddingModel.EmbeddingsRequest embeddingsRequest, List<EmbeddingModel.EmbeddingsItem> embeddingsItems) {
        List<List<String>> partitionInputs = ListUtils.partition(embeddingsRequest.getInput(), 10);
        for (List<String> partitionInput : partitionInputs) {
            OpenAiApi.EmbeddingRequest<List<String>> openAIEmbeddingsRequest =
                    new OpenAiApi.EmbeddingRequest<>(partitionInput, embeddingProperties.openAiModelName);
            OpenAiApi.EmbeddingList<OpenAiApi.Embedding> openAIEmbeddingsResponse = webClient.post()
                    .uri(embeddingsPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(openAIEmbeddingsRequest) // 自动序列化为 JSON
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<OpenAiApi.EmbeddingList<OpenAiApi.Embedding>>() {
                    })
                    .block(); // 阻塞等待结果

            if (openAIEmbeddingsResponse != null && openAIEmbeddingsResponse.data() != null) {
                for (int i = 0; i < openAIEmbeddingsResponse.data().size(); i++) {
                    embeddingsItems.add(new EmbeddingModel.EmbeddingsItem()
                            .setEmbeddingProvider(embeddingProperties.embeddingProvider)
                            .setEmbeddingModel(embeddingProperties.openAiModelName)
                            .setCheckEmbeddingHash(checkEmbeddingHash)
                            .setText(partitionInput.get(i))
                            .setEmbeddings(openAIEmbeddingsResponse.data().get(i).embedding()));
                }
            }
        }
        log.info("finish embeddings: {}", embeddingsItems.size());
    }

    private void handleOllamaEmbeddings(EmbeddingModel.EmbeddingsRequest embeddingsRequest, List<EmbeddingModel.EmbeddingsItem> embeddingsItems) {
        OllamaApi.EmbeddingsRequest ollamaEmbeddingsRequest = new OllamaApi.EmbeddingsRequest(
                embeddingProperties.ollamaModelName,
                embeddingsRequest.getInput(),
                secondsToDurationString(embeddingProperties.keepAliveSeconds),
                null,
                null
        );
        OllamaApi.EmbeddingsResponse ollamaEmbeddingsResponse = webClient.post()
                .uri(embeddingsPath)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ollamaEmbeddingsRequest) // 自动序列化为 JSON
                .retrieve()
                .bodyToMono(OllamaApi.EmbeddingsResponse.class)
                .block(); // 阻塞等待结果
        if (ollamaEmbeddingsResponse != null && ollamaEmbeddingsResponse.embeddings() != null) {
            List<float[]> embeddings = ollamaEmbeddingsResponse.embeddings();
            for (int i = 0; i < embeddings.size(); i++) {
                // embeddings item is typically a List<Double>
                float[] floats = embeddings.get(i);
                for (int j = 0; j < embeddings.size(); j++) {
                    Number v = floats[j];
                    floats[j] = v.floatValue();
                }
                embeddingsItems.add(new EmbeddingModel.EmbeddingsItem()
                        .setEmbeddingProvider(embeddingProperties.embeddingProvider)
                        .setEmbeddingModel(embeddingProperties.ollamaModelName)
                        .setCheckEmbeddingHash(checkEmbeddingHash)
                        .setText(embeddingsRequest.getInput().get(i))
                        .setEmbeddings(floats));
            }
        }
    }
}