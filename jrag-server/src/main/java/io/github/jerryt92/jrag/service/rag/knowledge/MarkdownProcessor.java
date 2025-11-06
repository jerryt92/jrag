package io.github.jerryt92.jrag.service.rag.knowledge;

import com.google.common.io.Files;
import io.github.jerryt92.jrag.service.file.CompressExtractor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class MarkdownProcessor {
    private final CompressExtractor compressExtractor;

    public MarkdownProcessor(CompressExtractor compressExtractor) {
        this.compressExtractor = compressExtractor;
    }

    public void processMarkdown(MultipartFile file) {
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
    }
}
