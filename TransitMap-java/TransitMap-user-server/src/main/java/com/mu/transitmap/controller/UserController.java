package com.mu.transitmap.controller;


import com.mu.transitmap.dto.UserLoginDTO;
import com.mu.transitmap.dto.UserPasswordUpdateDTO;
import com.mu.transitmap.dto.UserProfileUpdateDTO;
import com.mu.transitmap.dto.UserRegisterDTO;
import com.mu.transitmap.dto.WechatLoginDTO;
import com.mu.transitmap.service.impl.WeChatAuthService;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.CaptchaImageService;
import com.mu.transitmap.service.EmailService;
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
    private EmailService emailService;
    @Autowired
    private CaptchaImageService captchaImageService;
    @Autowired
    private WeChatAuthService weChatAuthService;

    @PostMapping("/register")
    public Result<Map<String, String>> postRegister(@RequestBody @Valid UserRegisterDTO userDto) {
        if (!emailService.validate(userDto.getEmail(), userDto.getEmailCode())) {
            throw BusinessException.of(ErrorCode.VERIFICATION_CODE_INCORRECT);
        }
        userService.register(userDto);
        return Result.success(null);
    }

    @PostMapping("/login")
    public Result<UserLoginVO> postLogin(@RequestBody @Valid UserLoginDTO dto) {
        if (!captchaImageService.validate(dto.getCaptchaKey(), dto.getCaptchaCode())) {
            throw BusinessException.of(ErrorCode.VERIFICATION_CODE_INCORRECT);
        }
        UserLoginVO vo = userService.login(dto);
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
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        userService.updatePassword(userId, dto.getOldPassword(), dto.getNewPassword());
        return Result.success(null);
    }

    @PostMapping("/wechat/login")
    public Result<UserLoginVO> wechatLogin(@RequestBody @Valid WechatLoginDTO dto) {
        UserLoginVO vo = weChatAuthService.loginByWeChat(dto.getCode());
        return Result.success(vo);
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
