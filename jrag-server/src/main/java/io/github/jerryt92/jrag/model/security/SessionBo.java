package io.github.jerryt92.jrag.model.security;

import lombok.Data;

@Data
public class SessionBo {
    private String sessionId;
    private String userId;
    private String username;
    private Integer role;
    private long expireTime;

    public boolean hasAccess(Integer requiredRole) {
        if (requiredRole == null) {
            return true;
        }
        if (role == null) {
            return false;
        }
        return role <= requiredRole;
    }

    public boolean isAdmin() {
        return hasAccess(1);
    }
}
