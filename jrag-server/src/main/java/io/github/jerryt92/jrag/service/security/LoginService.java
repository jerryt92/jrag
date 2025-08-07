package io.github.jerryt92.jrag.service.security;

import io.github.jerryt92.jrag.mapper.mgb.UserPoMapper;
import io.github.jerryt92.jrag.model.security.SessionBo;
import io.github.jerryt92.jrag.po.mgb.UserPo;
import io.github.jerryt92.jrag.po.mgb.UserPoExample;
import io.github.jerryt92.jrag.utils.UUIDUtil;
import io.github.jerryt92.jrag.utils.UserUtil;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@EnableScheduling
public class LoginService {
    private static final ConcurrentHashMap<String, SessionBo> SESSION_MAP = new ConcurrentHashMap<>();
    private static final long EXPIRE_TIME_MINUTES = 30;
    private final UserPoMapper userPoMapper;
    private final CaptchaService captchaService;
    public ThreadLocal<String> sessionThreadLocal = new ThreadLocal();

    public LoginService(UserPoMapper userPoMapper, CaptchaService captchaService) {
        this.userPoMapper = userPoMapper;
        this.captchaService = captchaService;
    }

    public SessionBo login(String username, String password, String captchaCode, String hash) {
        UserPoExample example = new UserPoExample();
        example.createCriteria().andUsernameEqualTo(username);
        if (!captchaService.verifyCaptchaCode(captchaCode, hash)) {
            return null;
        }
        List<UserPo> userPos = userPoMapper.selectByExample(example);
        if (userPos.isEmpty()) {
            return null;
        }
        UserPo userPo = userPos.getFirst();
        if (UserUtil.verifyPassword(userPo.getId(), password, userPo.getPasswordHash())) {
            SessionBo sessionBo = new SessionBo();
            sessionBo.setSessionId(UUIDUtil.randomUUID());
            sessionBo.setUserId(userPo.getId());
            sessionBo.setUsername(userPo.getUsername());
            sessionBo.setExpireTime(System.currentTimeMillis() + 1000 * 60 * 60 * 24);
            SESSION_MAP.put(sessionBo.getSessionId(), sessionBo);
            return sessionBo;
        } else {
            return null;
        }
    }

    public SessionBo getSession(String sessionId) {
        return SESSION_MAP.get(sessionId);
    }

    public SessionBo getSession() {
        String sessionId = this.sessionThreadLocal.get();
        return getSession(sessionId);
    }

    public void logout(String sessionId) {
        SESSION_MAP.remove(sessionId);
    }

    public boolean validateSession(String sessionId) {
        boolean result = false;
        SessionBo sessionBo = SESSION_MAP.get(sessionId);
        if (sessionBo != null) {
            if (sessionBo.getExpireTime() > System.currentTimeMillis()) {
                sessionBo.setExpireTime(System.currentTimeMillis() + EXPIRE_TIME_MINUTES * 60 * 1000);
                result = true;
            }
        }
        return result;
    }

    /**
     * 定时任务，清理session
     */
    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    public void cleanSession() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, SessionBo> entry : SESSION_MAP.entrySet()) {
            if (entry.getValue().getExpireTime() < now) {
                SESSION_MAP.remove(entry.getKey());
            }
        }
    }
}
