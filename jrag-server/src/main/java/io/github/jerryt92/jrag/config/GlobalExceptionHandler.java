package io.github.jerryt92.jrag.config;

import io.github.jerryt92.jrag.model.ErrorResponseDto;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponseDto> handleWebClientResponseException(WebClientResponseException ex) {
        String message = ex.getResponseBodyAsString();
        if (StringUtils.isBlank(message)) {
            message = ex.getStatusText();
        }
        if (StringUtils.isBlank(message)) {
            message = ex.getMessage();
        }
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponseDto().message(message));
    }

    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<ErrorResponseDto> handleWebClientRequestException(WebClientRequestException ex) {
        String message = StringUtils.isBlank(ex.getMessage())
                ? "Upstream service unavailable."
                : ex.getMessage();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponseDto().message(message));
    }
}
