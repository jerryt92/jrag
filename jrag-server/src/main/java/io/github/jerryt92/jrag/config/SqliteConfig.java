package io.github.jerryt92.jrag.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;

// 只有当 URL 以 jdbc:sqlite: 开头时，这个配置类才会被加载
@Configuration
@ConditionalOnExpression("'${spring.datasource.url}'.startsWith('jdbc:sqlite:')")
public class SqliteConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties properties) {
        String jdbcUrl = properties.getUrl();
        if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:sqlite:")) {
            String dbFilePath = jdbcUrl.replace("jdbc:sqlite:", "");
            initializeDbFile(new File(dbFilePath));
        }
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    private void initializeDbFile(File targetFile) {
        try {
            // 如果文件不存在，或者文件大小为0（处理驱动预创建空文件的情况）
            if (!targetFile.exists() || targetFile.length() == 0) {
                System.out.println("检测到 SQLite 数据库文件缺失或为空: " + targetFile.getAbsolutePath());

                File parentDir = targetFile.getParentFile();
                if (!parentDir.exists()) {
                    parentDir.mkdirs();
                }

                ClassPathResource resource = new ClassPathResource("sql/jrag.db");
                if (!resource.exists()) {
                    throw new RuntimeException("初始化失败：Classpath 下未找到 sql/jrag.db 模板文件");
                }

                System.out.println("正在从模板初始化数据库...");
                FileCopyUtils.copy(resource.getInputStream(), java.nio.file.Files.newOutputStream(targetFile.toPath()));
                System.out.println("SQLite 数据库初始化完成。");
            }
        } catch (IOException e) {
            throw new RuntimeException("初始化 SQLite 数据库文件失败", e);
        }
    }
}