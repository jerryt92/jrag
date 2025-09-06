
// MarkdownProcessorController.java
package io.github.jerryt92.jrag.controller;

import com.google.common.io.Files;
import io.github.jerryt92.jrag.server.api.MarkdownProcessorApi;
import io.github.jerryt92.jrag.service.file.CompressExtractor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
public class MarkdownProcessorController implements MarkdownProcessorApi {

    private final CompressExtractor compressExtractor;

    public MarkdownProcessorController(CompressExtractor compressExtractor) {
        this.compressExtractor = compressExtractor;
    }

    @Override
    public ResponseEntity<Void> uploadMarkdown(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        String extension = Files.getFileExtension(fileName);

        if ("md".equalsIgnoreCase(extension)) {
            // 直接处理Markdown文件
        } else {
            // 处理压缩文件
            Map<String, MultipartFile> extract = null;
            try {
                extract = compressExtractor.extract(file);
                for (String filePath : extract.keySet()) {
                    System.out.println(filePath);
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return ResponseEntity.ok().build();
    }
}