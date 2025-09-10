package io.github.jerryt92.jrag.service.file;

import com.google.common.io.Files;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 压缩文件提取器
 */
@Slf4j
@Service
public class CompressExtractor {


    /**
     * 从压缩文件中提取文件
     *
     * @param multipartFile 上传的压缩文件
     * @return Map<String, MultipartFile> 其中key为文件在压缩包内的路径，value为对应的文件
     * @throws IOException      如果发生I/O错误
     * @throws RuntimeException 如果文件类型不受支持或解压失败
     */
    public Map<String, MultipartFile> extract(MultipartFile multipartFile) throws IOException {
        if (multipartFile == null || multipartFile.isEmpty()) {
            return new HashMap<>();
        }
        String filename = StringUtils.cleanPath(Objects.requireNonNull(multipartFile.getOriginalFilename()));
        String lowerFilename = filename.toLowerCase();
        if (!lowerFilename.endsWith(".zip") && !lowerFilename.endsWith(".tar") && !lowerFilename.endsWith(".tar.gz")) {
            String errorMsg = "Unsupported file type: " + Files.getFileExtension(lowerFilename);
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        // 对于 zip, tar, tar.gz, 等，使用 Commons Compress
        return extractWithCommonsCompress(multipartFile);
    }

    private Map<String, MultipartFile> extractWithCommonsCompress(MultipartFile multipartFile) {
        Map<String, MultipartFile> extractedFiles = new HashMap<>();
        List<EntryData> entryDataList = new ArrayList<>();
        // 第一步：收集所有entry信息
        try (InputStream is = multipartFile.getInputStream();
             BufferedInputStream bis = new BufferedInputStream(is);
             ArchiveInputStream<?> archiveInputStream = new ArchiveStreamFactory().createArchiveInputStream(bis)) {
            ArchiveEntry entry;
            while ((entry = archiveInputStream.getNextEntry()) != null) {
                if (!archiveInputStream.canReadEntryData(entry) || entry.isDirectory()) {
                    continue;
                }
                byte[] content = IOUtils.toByteArray(archiveInputStream);
                entryDataList.add(new EntryData(entry.getName(), content));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract file with Commons Compress: " + e.getMessage(), e);
        }
        // 第二步：计算公共前缀
        String commonPrefix = determineCommonPrefix(entryDataList);
        // 第三步：创建MultipartFile对象，去除公共前缀
        for (EntryData entryData : entryDataList) {
            String fileName = entryData.name;
            if (commonPrefix != null && !commonPrefix.isEmpty() && fileName.startsWith(commonPrefix)) {
                fileName = fileName.substring(commonPrefix.length());
            }

            MultipartFile extracted = new InMemoryMultipartFile(fileName, fileName, null, entryData.content);
            extractedFiles.put(fileName, extracted);
        }
        return extractedFiles;
    }

    // 辅助类用于存储entry数据
    private static class EntryData {
        final String name;
        final byte[] content;

        EntryData(String name, byte[] content) {
            this.name = name;
            this.content = content;
        }
    }

    // 确定公共前缀的方法
    private String determineCommonPrefix(List<EntryData> entries) {
        if (entries.isEmpty()) {
            return "";
        }
        String commonPrefix = null;
        for (EntryData entry : entries) {
            String entryName = entry.name;
            if (commonPrefix == null) {
                int firstSlash = entryName.indexOf('/');
                if (firstSlash != -1) {
                    commonPrefix = entryName.substring(0, firstSlash + 1);
                } else {
                    commonPrefix = "";
                }
            } else if (!commonPrefix.isEmpty()) {
                if (!entryName.startsWith(commonPrefix)) {
                    commonPrefix = ""; // 没有公共前缀
                }
            }
        }
        return commonPrefix;
    }

    /**
     * MultipartFile 的一个内存实现，用于存储从压缩文件中提取的数据。
     */
    private static class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        public InMemoryMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        @NonNull
        public String getName() {
            return this.name;
        }

        @Override
        public String getOriginalFilename() {
            return this.originalFilename;
        }

        @Override
        public String getContentType() {
            return this.contentType;
        }

        @Override
        public boolean isEmpty() {
            return this.content.length == 0;
        }

        @Override
        public long getSize() {
            return this.content.length;
        }

        @Override
        @NonNull
        public byte[] getBytes() {
            return this.content;
        }

        @Override
        @NonNull
        public InputStream getInputStream() {
            return new ByteArrayInputStream(this.content);
        }

        @Override
        public void transferTo(@NonNull File dest) throws IOException, IllegalStateException {
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                fos.write(this.content);
            }
        }
    }
}