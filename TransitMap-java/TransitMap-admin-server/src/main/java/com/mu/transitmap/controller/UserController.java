package com.mu.transitmap.controller;


import com.mu.transitmap.dto.UserLoginDTO;
import com.mu.transitmap.dto.UserPasswordUpdateDTO;
import com.mu.transitmap.dto.UserProfileUpdateDTO;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.enums.UserRoleEnum;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.CaptchaImageService;
import com.mu.transitmap.service.impl.UserServiceImpl;
import com.mu.transitmap.vo.UserLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserServiceImpl userService;
    @Autowired
    private CaptchaImageService captchaImageService;

    @PostMapping("/login/admin")
    public Result<UserLoginVO> postAdminLogin(@RequestBody @Valid UserLoginDTO dto) {
        if (!captchaImageService.validate(dto.getCaptchaKey(), dto.getCaptchaCode())) {
            throw BusinessException.of(ErrorCode.VERIFICATION_CODE_INCORRECT);
        }
        UserLoginVO vo = userService.loginWithRole(dto, UserRoleEnum.ADMIN.getCode());
        return Result.success(vo);
    }

    @PostMapping("/login/super-admin")
    public Result<UserLoginVO> postSuperAdminLogin(@RequestBody @Valid UserLoginDTO dto) {
        if (!captchaImageService.validate(dto.getCaptchaKey(), dto.getCaptchaCode())) {
            throw BusinessException.of(ErrorCode.VERIFICATION_CODE_INCORRECT);
        }
        UserLoginVO vo = userService.loginWithRole(dto, UserRoleEnum.SUPER_ADMIN.getCode());
        return Result.success(vo);
    }

    @PostMapping("/login/root-admin")
    public Result<UserLoginVO> postRootAdminLogin(@RequestBody @Valid UserLoginDTO dto) {
        if (!captchaImageService.validate(dto.getCaptchaKey(), dto.getCaptchaCode())) {
            throw BusinessException.of(ErrorCode.VERIFICATION_CODE_INCORRECT);
        }
        UserLoginVO vo = userService.loginWithRole(dto, UserRoleEnum.ROOT_ADMIN.getCode());
        return Result.success(vo);
    }

    @PostMapping("/logout")
    public Result<Map<String, String>> postLogout(HttpServletRequest request) {
        try {
            Long userId = (Long) request.getAttribute("userId");
            if (userId != null) {
                userService.logout(userId);
            }
            return Result.success(null);
        } catch (RedisConnectionFailureException e) {
            throw new BusinessException(ErrorCode.REDIS_UNAVAILABLE);
        }
    }

    @GetMapping("/info")
    public Result<UserLoginVO> getUserInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        UserLoginVO vo = userService.getUserInfo(userId);
        return Result.success(vo);
    }

    @PutMapping("/profile")
    public Result<Map<String, String>> updateProfile(@RequestBody @Valid UserProfileUpdateDTO dto,
                                                      HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        userService.updateProfile(userId, dto);
        return Result.success(null);
    }

    @PutMapping("/password")
    public Result<Map<String, String>> updatePassword(@RequestBody @Valid UserPasswordUpdateDTO dto,
                                                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        // 只有最高管理员 (role=4) 才能修改密码
        if (roleCode == null || roleCode < 4) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        userService.updatePassword(userId, dto.getOldPassword(), dto.getNewPassword());
        return Result.success(null);
    }

    @PostMapping("/avatar")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file,
                                                     HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String avatarPath = userService.updateAvatar(userId, file);
        return Result.success(Map.of("avatar", avatarPath));
    }
}
