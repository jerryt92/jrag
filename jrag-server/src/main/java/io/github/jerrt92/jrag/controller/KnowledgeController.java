package io.github.jerrt92.jrag.controller;

import io.github.jerrt92.jrag.model.EmbeddingModel;
import io.github.jerrt92.jrag.model.KnowledgeAddDto;
import io.github.jerrt92.jrag.model.KnowledgeSearchResponseDto;
import io.github.jerrt92.jrag.model.Translator;
import io.github.jerrt92.jrag.server.api.KnowledgeApi;
import io.github.jerrt92.jrag.service.rag.knowledge.KnowledgeService;
import io.github.jerrt92.jrag.service.rag.retrieval.Retriever;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class KnowledgeController implements KnowledgeApi {
    private final KnowledgeService knowledgeService;
    private final Retriever retriever;

    public KnowledgeController(KnowledgeService knowledgeService, Retriever retriever) {
        this.knowledgeService = knowledgeService;
        this.retriever = retriever;
    }

    @Override
    public ResponseEntity<Void> putKnowledge(List<KnowledgeAddDto> knowledgeAddDto) {
        knowledgeService.putKnowledge(knowledgeAddDto);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<KnowledgeSearchResponseDto> searchKnowledge(String queryText, Integer topK, Float minCosScore) {
        List<EmbeddingModel.EmbeddingsQueryItem> embeddingsQueryItems;
        embeddingsQueryItems = retriever.similarityRetrieval(queryText, topK, minCosScore);
        KnowledgeSearchResponseDto knowledgeSearchResponseDto = new KnowledgeSearchResponseDto().data(
                embeddingsQueryItems.stream().map(Translator::translateToEmbeddingsQueryItemDto).collect(Collectors.toList())
        );
        return ResponseEntity.ok(knowledgeSearchResponseDto);
    }
}
