package io.github.jerryt92.jrag.service.file;

import io.github.jerryt92.jrag.mapper.mgb.FilePoMapper;
import io.github.jerryt92.jrag.model.FileBo;
import io.github.jerryt92.jrag.model.Translator;
import io.github.jerryt92.jrag.po.mgb.FilePo;
import io.github.jerryt92.jrag.po.mgb.FilePoExample;
import io.github.jerryt92.jrag.utils.MDUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.File;
import java.net.URL;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class FileService {
    @Value("${jrag.file.upload-path}")
    public String uploadPath;

    @Value("${jrag.file.static-path}")
    public String staticFilePath;

    private final FilePoMapper filePoMapper;

    private final String CLASS_PATH;

    public FileService(FilePoMapper filePoMapper) {
        URL resource = this.getClass().getResource("/");
        if (resource == null) {
            throw new RuntimeException("classpath not found");
        }
        CLASS_PATH = resource.getPath();
        this.filePoMapper = filePoMapper;
    }

    @PostConstruct
    public void init() {
        // 上传文件路径
        if (uploadPath.startsWith("classpath:")) {
            uploadPath = uploadPath.replace("classpath:/", CLASS_PATH);
        }
        // 静态文件路径
        if (staticFilePath.startsWith("classpath:")) {
            staticFilePath = staticFilePath.replace("classpath:/", CLASS_PATH);
        }
    }

    public FileBo uploadFIle(MultipartFile multipartFile) {
        try {
            if (!multipartFile.isEmpty()) {
                FileBo fileBo;
                // 校验MD5和SHA-1
                String fileMd5 = MDUtil.getMessageDigest(multipartFile.getBytes(), MDUtil.MdAlgorithm.MD5);
                String fileSha1 = MDUtil.getMessageDigest(multipartFile.getBytes(), MDUtil.MdAlgorithm.SHA1);
                FilePoExample filePoExample = new FilePoExample();
                filePoExample.createCriteria()
                        .andMd5EqualTo(fileMd5)
                        .andSha1EqualTo(fileSha1);
                List<FilePo> filePos = filePoMapper.selectByExample(filePoExample);
                // 文件存储复用
                if (!filePos.isEmpty()) {
                    log.info("文件已存在，已进行复用");
                    fileBo = Translator.translateToFileBo(filePos.get(0));
                } else {
                    //获得上传文件名
                    String fileName = multipartFile.getOriginalFilename();
                    fileBo = new FileBo();
                    fileBo.setFullFileName(fileName);
                    if (fileName != null && fileName.contains(".")) {
                        fileBo.setSuffix(fileName.split("\\.")[fileName.split("\\.").length - 1]);
                    }
                    log.info("path => [" + uploadPath + "]");
                    log.info("fileName => [" + fileName + "]");
                    // 设置图片实例化信息
                    fileBo.setPath(uploadPath + File.separator + fileBo.getFullFileName());
                    fileBo.setSize(multipartFile.getSize());
                    fileBo.setMd5(fileMd5);
                    fileBo.setSha1(fileSha1);
                    fileBo.setUploadTime(new Date().getTime());
                    File file = new File(fileBo.getPath());
                    //如果文件目录不存在，创建目录
                    if (!file.getParentFile().exists()) {
                        file.getParentFile().mkdirs();
                    }
                    //将上传文件保存到一个目标文件中
                    multipartFile.transferTo(file);
                    FilePo insertFilePo = Translator.translateToFilePo(fileBo);
                    filePoMapper.insert(insertFilePo);
                    fileBo.setId(insertFilePo.getId());
                }
                return fileBo;
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional(rollbackFor = Throwable.class)
    public FileBo putFile(Integer id, MultipartFile multipartFile) {
        try {
            if (!multipartFile.isEmpty()) {
                // 校验MD5和SHA-1
                String fileMd5 = MDUtil.getMessageDigest(multipartFile.getBytes(), MDUtil.MdAlgorithm.MD5);
                String fileSha1 = MDUtil.getMessageDigest(multipartFile.getBytes(), MDUtil.MdAlgorithm.SHA1);
                FilePo oldFilePo = filePoMapper.selectByPrimaryKey(id);
                if (oldFilePo != null) {
                    // 删除旧文件
                    FilePoExample filePoExample = new FilePoExample();
                    filePoExample.createCriteria()
                            .andIdNotEqualTo(id)
                            .andPathEqualTo(oldFilePo.getPath());
                    if (filePoMapper.countByExample(filePoExample) == 0) {
                        File file = new File(oldFilePo.getPath());
                        if (file.exists()) {
                            file.delete();
                        }
                    }
                    filePoMapper.deleteByPrimaryKey(id);
                }
                //获得上传文件名
                String fileName = multipartFile.getOriginalFilename();
                FileBo fileBo = new FileBo();
                fileBo.setId(id);
                fileBo.setFullFileName(fileName);
                if (fileName != null && fileName.contains(".")) {
                    fileBo.setSuffix(fileName.split("\\.")[fileName.split("\\.").length - 1]);
                }
                // 上传文件路径
                if (uploadPath.startsWith("classpath:")) {
                    uploadPath = uploadPath.replace("classpath:/", CLASS_PATH);
                }
                log.info("path => [" + uploadPath + "]");
                log.info("fileName => [" + fileName + "]");
                // 设置图片实例化信息
                fileBo.setPath(uploadPath + File.separator + fileBo.getFullFileName());
                fileBo.setSize(multipartFile.getSize());
                fileBo.setMd5(fileMd5);
                fileBo.setSha1(fileSha1);
                fileBo.setUploadTime(new Date().getTime());
                File file = new File(fileBo.getPath());
                //如果文件目录不存在，创建目录
                if (!file.getParentFile().exists()) {
                    file.getParentFile().mkdirs();
                }
                //将上传文件保存到一个目标文件中
                multipartFile.transferTo(file);
                FilePo insertFilePo = Translator.translateToFilePo(fileBo);
                filePoMapper.insert(insertFilePo);
                fileBo.setId(insertFilePo.getId());
                return fileBo;
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Transactional(rollbackFor = Throwable.class)
    public void deleteFile(List<Integer> fileId) {
        FilePoExample filePoExample = new FilePoExample();
        filePoExample.createCriteria().andIdIn(fileId);
        List<FilePo> filePos = filePoMapper.selectByExample(filePoExample);
        filePoMapper.deleteByExample(filePoExample);
        if (!filePos.isEmpty()) {
            for (FilePo filePo : filePos) {
                filePoExample = new FilePoExample();
                filePoExample.createCriteria()
                        .andIdNotEqualTo(filePo.getId())
                        .andPathEqualTo(filePo.getPath());
                if (filePoMapper.countByExample(filePoExample) == 0) {
                    File file = new File(filePo.getPath());
                    if (file.exists()) {
                        file.delete();
                    }
                }
            }
        }
    }

    public Resource getFile(Integer fileId) {
        FilePoExample example = new FilePoExample();
        example.createCriteria().andIdEqualTo(fileId);
        List<FilePo> dbFiles = filePoMapper.selectByExample(example);
        if (dbFiles.isEmpty()) {
            return null;
        }
        FilePo dbFile = dbFiles.get(0);
        FileSystemResource fileSystemResource = new FileSystemResource(dbFile.getPath());
        if (fileSystemResource.exists() && fileSystemResource.isFile()) {
            return fileSystemResource;
        } else {
            log.warn("文件不存在或不是有效文件: {}", dbFile.getPath());
            return null;
        }
    }

    public Resource getStaticFile(String filePath) {
        return new FileSystemResource(staticFilePath + File.separator + filePath);
    }

    public static MediaType parseMediaType(String fullFileName) {
        if (fullFileName == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        String fileExtension = fullFileName.substring(fullFileName.lastIndexOf(".") + 1);
        MediaType mediaType;
        switch (fileExtension.toLowerCase()) {
            case "jpg":
            case "jpeg":
                mediaType = MediaType.IMAGE_JPEG;
                break;
            case "png":
                mediaType = MediaType.IMAGE_PNG;
                break;
            case "gif":
                mediaType = MediaType.IMAGE_GIF;
                break;
            case "pdf":
                mediaType = MediaType.APPLICATION_PDF;
                break;
            case "txt":
                mediaType = MediaType.TEXT_PLAIN;
                break;
            default:
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return mediaType;
    }
}
