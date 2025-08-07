package io.github.jerryt92.jrag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagProperties {
    @Value("${jrag.rag.md-location}")
    private String mdLocation;
}
