package io.github.jerryt92.jrag.service.security;

import io.github.jerryt92.jrag.model.security.SessionBo;
import io.github.jerryt92.jrag.po.mgb.UserPo;
import io.github.jerryt92.jrag.po.mgb.UserPoExample;
import io.github.jerryt92.jrag.mapper.mgb.UserPoMapper;
import io.github.jerryt92.jrag.utils.UUIDUtil;
import io.github.jerryt92.jrag.utils.UserUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    private static final String ADMIN_USERNAME = "admin";
    private final UserPoMapper userPoMapper;

    public UserService(UserPoMapper userPoMapper) {
        this.userPoMapper = userPoMapper;
    }

    public List<UserPo> listUsers() {
        UserPoExample example = new UserPoExample();
        example.setOrderByClause("create_time DESC");
        return userPoMapper.selectByExample(example);
    }

    public UserPo createUser(String username, String password, Integer role, SessionBo operator) {
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            throw new IllegalArgumentException("username or password is empty");
        }
        if (role == null) {
            role = 2;
        }
        if (!isAdminUser(operator) && role < 2) {
            throw new IllegalArgumentException("only admin can create role 1 user");
        }
        if (findByUsername(username) != null) {
            throw new IllegalArgumentException("username already exists");
        }
        UserPo userPo = new UserPo();
        userPo.setId(UUIDUtil.randomUUID());
        userPo.setUsername(username.trim());
        userPo.setRole(role);
        userPo.setCreateTime(System.currentTimeMillis());
        userPo.setPasswordHash(UserUtil.getPasswordHash(userPo.getId(), password));
        userPoMapper.insert(userPo);
        return userPo;
    }

    public void deleteUser(String userId, SessionBo operator) {
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("userId is empty");
        }
        UserPo target = userPoMapper.selectByPrimaryKey(userId);
        if (target == null) {
            return;
        }
        if (ADMIN_USERNAME.equals(target.getUsername())) {
            throw new IllegalArgumentException("admin user cannot be deleted");
        }
        if (!canManageUser(operator, target)) {
            throw new IllegalArgumentException("permission denied");
        }
        userPoMapper.deleteByPrimaryKey(userId);
    }

    public void updateRole(String userId, Integer role, SessionBo operator) {
        if (StringUtils.isBlank(userId) || role == null) {
            throw new IllegalArgumentException("userId or role is empty");
        }
        UserPo target = userPoMapper.selectByPrimaryKey(userId);
        if (target == null) {
            throw new IllegalArgumentException("user not found");
        }
        if (!isAdminUser(operator)) {
            if (operator == null || operator.getRole() == null || operator.getRole() != 1) {
                throw new IllegalArgumentException("permission denied");
            }
            if (target.getRole() == null || target.getRole() <= 1 || role <= 1) {
                throw new IllegalArgumentException("role1 can only update role2 users");
            }
        }
        target.setRole(role);
        userPoMapper.updateByPrimaryKeySelective(target);
    }

    public void updatePassword(String userId, String oldPassword, String newPassword, SessionBo operator) {
        if (StringUtils.isBlank(newPassword)) {
            throw new IllegalArgumentException("new password is empty");
        }
        UserPo target;
        if (StringUtils.isBlank(userId)) {
            if (operator == null) {
                throw new IllegalArgumentException("invalid session");
            }
            userId = operator.getUserId();
        }
        target = userPoMapper.selectByPrimaryKey(userId);
        if (target == null) {
            throw new IllegalArgumentException("user not found");
        }
        if (isAdminUser(operator)) {
            updateUserPassword(target, newPassword);
            return;
        }
        if (operator != null && operator.getRole() != null && operator.getRole() == 1) {
            if (target.getRole() != null && target.getRole() >= 2) {
                updateUserPassword(target, newPassword);
                return;
            }
        }
        if (operator != null && operator.getUserId().equals(target.getId())) {
            if (StringUtils.isBlank(oldPassword)) {
                throw new IllegalArgumentException("old password is required");
            }
            if (!UserUtil.verifyPassword(target.getId(), oldPassword, target.getPasswordHash())) {
                throw new IllegalArgumentException("old password is incorrect");
            }
            updateUserPassword(target, newPassword);
            return;
        }
        throw new IllegalArgumentException("permission denied");
    }

    public UserPo findByUsername(String username) {
        if (StringUtils.isBlank(username)) {
            return null;
        }
        UserPoExample example = new UserPoExample();
        example.createCriteria().andUsernameEqualTo(username.trim());
        List<UserPo> userPos = userPoMapper.selectByExample(example);
        return userPos.isEmpty() ? null : userPos.getFirst();
    }

    private void updateUserPassword(UserPo target, String newPassword) {
        target.setPasswordHash(UserUtil.getPasswordHash(target.getId(), newPassword));
        userPoMapper.updateByPrimaryKeySelective(target);
    }

    private boolean isAdminUser(SessionBo operator) {
        return operator != null && ADMIN_USERNAME.equals(operator.getUsername());
    }

    private boolean canManageUser(SessionBo operator, UserPo target) {
        if (operator == null || operator.getRole() == null) {
            return false;
        }
        if (isAdminUser(operator)) {
            return true;
        }
        return operator.getRole() == 1 && target.getRole() != null && target.getRole() >= 2;
    }
}
