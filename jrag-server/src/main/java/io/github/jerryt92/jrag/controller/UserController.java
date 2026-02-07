package io.github.jerryt92.jrag.controller;

import io.github.jerryt92.jrag.config.annotation.RequiredRole;
import io.github.jerryt92.jrag.model.UserCreateRequestDto;
import io.github.jerryt92.jrag.model.UserDto;
import io.github.jerryt92.jrag.model.UserListDto;
import io.github.jerryt92.jrag.model.UserPasswordUpdateRequestDto;
import io.github.jerryt92.jrag.model.UserRoleUpdateRequestDto;
import io.github.jerryt92.jrag.model.security.SessionBo;
import io.github.jerryt92.jrag.po.mgb.UserPo;
import io.github.jerryt92.jrag.server.api.UserApi;
import io.github.jerryt92.jrag.service.security.LoginService;
import io.github.jerryt92.jrag.service.security.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class UserController implements UserApi {
    private final UserService userService;
    private final LoginService loginService;

    public UserController(UserService userService, LoginService loginService) {
        this.userService = userService;
        this.loginService = loginService;
    }

    @Override
    @RequiredRole(1)
    public ResponseEntity<UserListDto> getUserList() {
        List<UserDto> users = userService.listUsers().stream()
                .map(this::toUserDto)
                .collect(Collectors.toList());
        UserListDto userListDto = new UserListDto();
        userListDto.setData(users);
        return ResponseEntity.ok(userListDto);
    }

    @Override
    @RequiredRole(1)
    public ResponseEntity<UserDto> createUser(UserCreateRequestDto userCreateRequestDto) {
        SessionBo session = loginService.getSession();
        UserPo userPo = userService.createUser(
                userCreateRequestDto.getUsername(),
                userCreateRequestDto.getPassword(),
                userCreateRequestDto.getRole(),
                session
        );
        return ResponseEntity.ok(toUserDto(userPo));
    }

    @Override
    @RequiredRole(1)
    public ResponseEntity<Void> deleteUser(String userId) {
        SessionBo session = loginService.getSession();
        userService.deleteUser(userId, session);
        return ResponseEntity.ok().build();
    }

    @Override
    @RequiredRole(1)
    public ResponseEntity<Void> updateUserRole(UserRoleUpdateRequestDto userRoleUpdateRequestDto) {
        SessionBo session = loginService.getSession();
        userService.updateRole(
                userRoleUpdateRequestDto.getUserId(),
                userRoleUpdateRequestDto.getRole(),
                session
        );
        return ResponseEntity.ok().build();
    }

    @Override
    @RequiredRole(2)
    public ResponseEntity<Void> updateUserPassword(UserPasswordUpdateRequestDto userPasswordUpdateRequestDto) {
        SessionBo session = loginService.getSession();
        userService.updatePassword(
                userPasswordUpdateRequestDto.getUserId(),
                userPasswordUpdateRequestDto.getOldPassword(),
                userPasswordUpdateRequestDto.getNewPassword(),
                session
        );
        return ResponseEntity.ok().build();
    }

    private UserDto toUserDto(UserPo userPo) {
        UserDto userDto = new UserDto();
        userDto.setUserId(userPo.getId());
        userDto.setUsername(userPo.getUsername());
        userDto.setRole(userPo.getRole());
        userDto.setCreateTime(userPo.getCreateTime());
        return userDto;
    }
}
