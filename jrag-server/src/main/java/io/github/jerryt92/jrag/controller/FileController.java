package io.github.jerryt92.jrag.controller;

import io.github.jerryt92.jrag.constants.CommonConstants;
import io.github.jerryt92.jrag.constants.ErrorConstants;
import io.github.jerryt92.jrag.model.FileBo;
import io.github.jerryt92.jrag.model.FileDto;
import io.github.jerryt92.jrag.model.Translator;
import io.github.jerryt92.jrag.server.api.FileApi;
import io.github.jerryt92.jrag.service.file.FileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
public class FileController implements FileApi {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @Override
    public ResponseEntity<FileDto> uploadFIle(MultipartFile file) {
        // 如果超过1G就不允许上传
        if (file.getSize() > 1024 * 1024 * 1024) {
            throw new RuntimeException(ErrorConstants.FILE_UPLOAD_SIZE_LIMIT);
        }
        FileBo fileBo = fileService.uploadFIle(file);
        if (fileBo == null) {
            throw new RuntimeException("file upload failed");
        }
        return ResponseEntity.ok(Translator.translateToFileDto(fileBo));
    }

    @Override
    public ResponseEntity<FileDto> putFile(String id, MultipartFile file) {
        // 如果超过1G就不允许上传
        if (file.getSize() > 1024 * 1024 * 1024) {
            throw new RuntimeException(ErrorConstants.FILE_UPLOAD_SIZE_LIMIT);
        }
        FileBo fileBo = fileService.putFile(id, file);
        if (fileBo == null) {
            throw new RuntimeException("file upload failed");
        }
        return ResponseEntity.ok(Translator.translateToFileDto(fileBo));
    }

    @Override
    public ResponseEntity<Void> deleteFile(List<String> fileId) {
        fileService.deleteFile(fileId);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Resource> getFile(String fileId) {
        Resource resource = fileService.getFile(fileId);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        String filename = resource.getFilename();
        String encodedFilename = null; // 保留空格为 %20
        if (filename != null) {
            encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");
        }
        return ResponseEntity.ok()
                .contentType(FileService.parseMediaType(filename))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "filename*=UTF-8''" + encodedFilename)
                .body(resource);
    }

    @GetMapping(CommonConstants.STATIC_FILE_URL + "**")
    public ResponseEntity<Resource> getStaticFile(HttpServletRequest request) {
        try {
            // 获取请求中的子路径
            String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            String filePath = path.replaceFirst(CommonConstants.STATIC_FILE_URL, "");
            Resource resource = fileService.getStaticFile(filePath);
            if (resource == null || !resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            // 自动识别文件类型
            return ResponseEntity.ok()
                    .contentType(FileService.parseMediaType(resource.getFilename()))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}
