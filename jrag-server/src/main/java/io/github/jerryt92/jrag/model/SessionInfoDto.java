package io.github.jerryt92.jrag.model;

import lombok.Data;

@Data
public class SessionInfoDto {
    private String userId;
    private String username;
    private Integer role;
}
