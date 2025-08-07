package io.github.jerryt92.jrag.controller;

import io.github.jerryt92.jrag.model.EmbeddingsRequestDto;
import io.github.jerryt92.jrag.model.EmbeddingsResponseDto;
import io.github.jerryt92.jrag.model.Translator;
import io.github.jerryt92.jrag.server.api.EmbeddingApi;
import io.github.jerryt92.jrag.service.embedding.EmbeddingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmbeddingsController implements EmbeddingApi {
    @Autowired
    private EmbeddingService embeddingService;

    @Override
    public ResponseEntity<EmbeddingsResponseDto> embed(EmbeddingsRequestDto embeddingsRequestDto) {
        return ResponseEntity.ok(
                Translator.translateToEmbeddingsResponseDto(
                        embeddingService.embed(Translator.translateToEmbeddingsRequest(embeddingsRequestDto))
                )
        );
    }
}
