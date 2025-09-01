package io.github.jerryt92.jrag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CommonProperties {
    @Value("${jrag.public-mode}")
    public Boolean publicMode;
}
