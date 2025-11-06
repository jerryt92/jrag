package io.github.jerryt92.jrag.controller;

import io.github.jerryt92.jrag.server.api.MarkdownProcessorApi;
import io.github.jerryt92.jrag.service.rag.knowledge.MarkdownProcessor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MarkdownProcessorController implements MarkdownProcessorApi {

    private final MarkdownProcessor markdownProcessor;

    public MarkdownProcessorController(MarkdownProcessor markdownProcessor) {
        this.markdownProcessor = markdownProcessor;
    }

    @Override
    public ResponseEntity<Void> uploadMarkdown(MultipartFile file) {
        markdownProcessor.processMarkdown(file);
        return ResponseEntity.ok().build();
    }
}