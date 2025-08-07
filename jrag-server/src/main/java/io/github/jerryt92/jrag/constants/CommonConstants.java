package io.github.jerryt92.jrag.constants;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class CommonConstants {
    @Getter
    private static String springApplicationName;

    @Value("${spring.application.name}")
    private void setSpringApplicationName(String springApplicationName) {
        CommonConstants.springApplicationName = springApplicationName;
    }

    public static final String FILE_URL = "/v1/rest/jrag/file/";

    public static final String STATIC_FILE_URL = FILE_URL + "static/";
}
