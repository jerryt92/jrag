package io.github.jerryt92.jrag.model;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class FileBo {
    // ID
    private String id;
    // 全文件名（含后缀）
    private String fullFileName;
    // 后缀
    private String suffix;
    // 路径
    private String path;
    // 大小（字节）
    private Long size;
    // md5（32）
    private String md5;
    // sha1
    private String sha1;
    // 创建时间
    private Long uploadTime;
}
