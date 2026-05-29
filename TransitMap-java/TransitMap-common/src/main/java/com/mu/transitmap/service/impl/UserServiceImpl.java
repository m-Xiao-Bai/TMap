package com.mu.transitmap.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import com.mu.transitmap.constants.Constants;
import com.mu.transitmap.dto.*;
import com.mu.transitmap.entity.User;
import com.mu.transitmap.enums.*;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.mapper.UserMapper;
import com.mu.transitmap.service.IUserService;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mu.transitmap.utils.JwtUtil;
import com.mu.transitmap.utils.RedisUtils;
import com.mu.transitmap.vo.UserLoginVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final JwtUtil jwtUtil;
    private final RedisUtils redisUtils;
    private final SystemConfigServiceImpl systemConfigService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${project.data-dir:data}")
    private String dataDir;

    public UserServiceImpl(JwtUtil jwtUtil, RedisUtils redisUtils, SystemConfigServiceImpl systemConfigService) {
        this.jwtUtil = jwtUtil;
        this.redisUtils = redisUtils;
        this.systemConfigService = systemConfigService;
    }

    @Override
    public UserLoginVO login(UserLoginDTO dto) {
        return loginWithRole(dto, null);
    }

    private static final String LOGIN_FAIL_KEY = "login:fail:";
    private static final int MAX_LOGIN_FAIL = 10;
    private static final int LOGIN_LOCK_MINUTES = 30;

    private void onLoginFail(String failKey) {
        Long count = redisUtils.incrementKey(failKey, LOGIN_LOCK_MINUTES * 60);
        if (count != null && count >= MAX_LOGIN_FAIL) {
            log.warn("账号登录失败次数过多，已锁定 key={} count={}", failKey, count);
        }
    }

    @Override
    public UserLoginVO loginWithRole(UserLoginDTO dto, Integer requiredRoleCode) {
        // 登录失败锁定检查
        String failKey = LOGIN_FAIL_KEY + dto.getAccount();
        Long failCount = redisUtils.getKeyCount(failKey);
        if (failCount != null && failCount >= MAX_LOGIN_FAIL) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        User user = null;
        if (LoginTypeEnum.MOBILE.getType().equals(dto.getType())) {
            user = this.lambdaQuery().eq(User::getMobile, dto.getAccount()).one();
        } else if (LoginTypeEnum.EMAIL.getType().equals(dto.getType())) {
            user = this.lambdaQuery().eq(User::getEmail, dto.getAccount()).one();
        } else {
            onLoginFail(failKey);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND_AND_PASSWORD_ERROR);
        }
        if (user == null) {
            onLoginFail(failKey);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND_AND_PASSWORD_ERROR);
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            onLoginFail(failKey);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND_AND_PASSWORD_ERROR);
        }
        if (UserStatusEnum.DISABLED.getCode() == user.getStatusCode()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (requiredRoleCode != null && user.getRoleCode() < requiredRoleCode) {
            throw new BusinessException(ErrorCode.ROLE_NOT_MATCH);
        }

        // 登录成功，清除失败计数
        redisUtils.deleteKey(failKey);

        String token = jwtUtil.generateToken(user.getId(), user.getRoleCode());
        long tokenExpiry = systemConfigService.getConfigInt("auth.token_expiry", (int) jwtUtil.getExpiration());
        redisUtils.storeToken(user.getId(), token, tokenExpiry);

        user.setStatusCode(UserStatusEnum.ONLINE.getCode());
        user.setStatus(UserStatusEnum.ONLINE.getDesc());
        this.updateById(user);

        UserLoginVO vo = new UserLoginVO();
        BeanUtils.copyProperties(user, vo);
        vo.setToken(token);
        return vo;
    }

    @Override
    public void register(UserRegisterDTO userDto) {
        if(this.lambdaQuery().eq(User::getUsername,userDto.getUsername()).exists()){
            throw BusinessException.of(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if(this.lambdaQuery().eq(User::getEmail,userDto.getEmail()).exists()){
            throw BusinessException.of(ErrorCode.EMAIL_ALREADY_BIND);
        }
        if( !(userDto.getMobile()== null || userDto.getMobile().isEmpty())){
            if(this.lambdaQuery().eq(User::getMobile,userDto.getMobile()).exists()){
                throw BusinessException.of(ErrorCode.MOBILE_ALREADY_BIND);
            }
        }
        User user = new User();
        BeanUtils.copyProperties(userDto, user);
        user.setPassword(passwordEncoder.encode(userDto.getPassword()));
        user.setCountryId(Constants.ZEROES_L);
        user.setCountryName(Constants.WEI_ZHI);
        user.setGenderCode(UserGenderEnum.UNKNOWN.getCode());
        user.setGender(UserGenderEnum.UNKNOWN.getDesc());
        user.setCityId(Constants.ZEROES_L);
        user.setCityName(Constants.WEI_ZHI);
        user.setStatusCode(UserStatusEnum.OFFLINE.getCode());
        user.setStatus(UserStatusEnum.OFFLINE.getDesc());
        user.setRoleCode(UserRoleEnum.USER.getCode());
        user.setRole(UserRoleEnum.USER.getDesc());
        boolean save = this.save(user);
        if(!save){
            throw BusinessException.of(ErrorCode.USER_REGISTER_FAILED);
        }
    }

    @Override
    public void logout(Long userId) {
        redisUtils.deleteToken(userId);
        User user = this.getById(userId);
        if (user != null) {
            user.setStatusCode(UserStatusEnum.OFFLINE.getCode());
            user.setStatus(UserStatusEnum.OFFLINE.getDesc());
            this.updateById(user);
        }
    }

    @Override
    public UserLoginVO getUserInfo(Long userId) {
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND_AND_PASSWORD_ERROR);
        }
        UserLoginVO vo = new UserLoginVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    public Page<User> getUserPage(UserManageQueryDTO dto) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dto.getKeyword())) {
            wrapper.and(w -> w
                    .like(User::getUsername, dto.getKeyword())
                    .or()
                    .like(User::getEmail, dto.getKeyword())
                    .or()
                    .like(User::getMobile, dto.getKeyword())
            );
        }
        if (dto.getStatusCode() != null) {
            wrapper.eq(User::getStatusCode, dto.getStatusCode());
        }
        if (dto.getRoleCode() != null) {
            wrapper.eq(User::getRoleCode, dto.getRoleCode());
        }
        if (dto.getOnlineStatus() != null) {
            if (dto.getOnlineStatus() == 1) {
                wrapper.eq(User::getStatusCode, UserStatusEnum.ONLINE.getCode());
            } else {
                wrapper.ne(User::getStatusCode, UserStatusEnum.ONLINE.getCode());
            }
        }

        if (StringUtils.hasText(dto.getSortField())) {
            boolean asc = "asc".equalsIgnoreCase(dto.getSortOrder());
            switch (dto.getSortField()) {
                case "createdAt":
                    wrapper.orderBy(true, asc, User::getCreatedAt);
                    break;
                case "updatedAt":
                    wrapper.orderBy(true, asc, User::getUpdatedAt);
                    break;
                case "username":
                    wrapper.orderBy(true, asc, User::getUsername);
                    break;
                default:
                    wrapper.orderByDesc(User::getCreatedAt);
                    break;
            }
        } else {
            wrapper.orderByDesc(User::getCreatedAt);
        }
        return this.page(new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);
    }

    @Override
    public void createUser(UserManageCreateDTO dto, Integer operatorRoleCode) {
        int targetRole = dto.getRoleCode() != null ? dto.getRoleCode() : UserRoleEnum.USER.getCode();
        if (!canCreateRole(operatorRoleCode, targetRole)) {
            throw new BusinessException(ErrorCode.CANNOT_CREATE_USER_WITH_ROLE);
        }

        if (this.lambdaQuery().eq(User::getUsername, dto.getUsername()).exists()) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        if (this.lambdaQuery().eq(User::getEmail, dto.getEmail()).exists()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_BIND);
        }
        if (StringUtils.hasText(dto.getMobile())) {
            if (this.lambdaQuery().eq(User::getMobile, dto.getMobile()).exists()) {
                throw new BusinessException(ErrorCode.MOBILE_ALREADY_BIND);
            }
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setMobile(dto.getMobile());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setGenderCode(dto.getGenderCode() != null ? dto.getGenderCode() : UserGenderEnum.UNKNOWN.getCode());
        user.setGender(UserGenderEnum.fromCode(user.getGenderCode()).getDesc());
        user.setRoleCode(targetRole);
        user.setRole(UserRoleEnum.fromCode(targetRole).getDesc());
        user.setStatusCode(dto.getStatusCode() != null ? dto.getStatusCode() : UserStatusEnum.OFFLINE.getCode());
        user.setStatus(UserStatusEnum.fromCode(user.getStatusCode()).getDesc());
        user.setCountryId(Constants.ZEROES_L);
        user.setCountryName(Constants.WEI_ZHI);
        user.setCityId(Constants.ZEROES_L);
        user.setCityName(Constants.WEI_ZHI);

        if (!this.save(user)) {
            throw new BusinessException(ErrorCode.USER_REGISTER_FAILED);
        }
    }

    @Override
    public void updateUser(Long id, UserManageUpdateDTO dto, Integer operatorRoleCode, Long operatorUserId) {
        User user = this.getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND_AND_PASSWORD_ERROR);
        }

        if (!canModifyUser(operatorRoleCode, user.getRoleCode())) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_USER);
        }

        if (!user.getUsername().equals(dto.getUsername())) {
            if (this.lambdaQuery().eq(User::getUsername, dto.getUsername()).exists()) {
                throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
            }
        }
        if (!user.getEmail().equals(dto.getEmail())) {
            if (this.lambdaQuery().eq(User::getEmail, dto.getEmail()).exists()) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_BIND);
            }
        }
        if (StringUtils.hasText(dto.getMobile()) && !dto.getMobile().equals(user.getMobile())) {
            if (this.lambdaQuery().eq(User::getMobile, dto.getMobile()).exists()) {
                throw new BusinessException(ErrorCode.MOBILE_ALREADY_BIND);
            }
        }

        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setMobile(dto.getMobile());

        if (StringUtils.hasText(dto.getPassword())) {
            if (isBlankPassword(dto.getPassword())) {
                throw new BusinessException(ErrorCode.PASSWORD_CANNOT_BE_BLANK);
            }
            if (!canModifyPassword(operatorRoleCode)) {
                throw new BusinessException(ErrorCode.CANNOT_MODIFY_USER);
            }
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        if (dto.getGenderCode() != null) {
            user.setGenderCode(dto.getGenderCode());
            user.setGender(UserGenderEnum.fromCode(dto.getGenderCode()).getDesc());
        }

        if (dto.getStatusCode() != null) {
            if (user.getId().equals(operatorUserId)) {
                throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF_STATUS);
            }
            user.setStatusCode(dto.getStatusCode());
            user.setStatus(UserStatusEnum.fromCode(dto.getStatusCode()).getDesc());
        }

        if (dto.getRoleCode() != null && canModifyRole(operatorRoleCode)) {
            user.setRoleCode(dto.getRoleCode());
            user.setRole(UserRoleEnum.fromCode(dto.getRoleCode()).getDesc());
        }

        user.setUpdatedAt(LocalDateTime.now());
        if (!this.updateById(user)) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED);
        }
    }

    @Override
    public void updateProfile(Long userId, UserProfileUpdateDTO dto) {
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND_AND_PASSWORD_ERROR);
        }

        if (StringUtils.hasText(dto.getUsername()) && !dto.getUsername().equals(user.getUsername())) {
            if (this.lambdaQuery().eq(User::getUsername, dto.getUsername()).exists()) {
                throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
            }
            user.setUsername(dto.getUsername());
        }

        if (StringUtils.hasText(dto.getEmail()) && !dto.getEmail().equals(user.getEmail())) {
            if (this.lambdaQuery().eq(User::getEmail, dto.getEmail()).exists()) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_BIND);
            }
            user.setEmail(dto.getEmail());
        }

        if (StringUtils.hasText(dto.getMobile()) && !dto.getMobile().equals(user.getMobile())) {
            if (this.lambdaQuery().eq(User::getMobile, dto.getMobile()).exists()) {
                throw new BusinessException(ErrorCode.MOBILE_ALREADY_BIND);
            }
            user.setMobile(dto.getMobile());
        }

        if (dto.getBirthday() != null) {
            user.setBirthday(dto.getBirthday());
        }

        if (dto.getGenderCode() != null) {
            user.setGenderCode(dto.getGenderCode());
            user.setGender(UserGenderEnum.fromCode(dto.getGenderCode()).getDesc());
        }

        user.setUpdatedAt(LocalDateTime.now());
        this.updateById(user);
    }

    @Override
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND_AND_PASSWORD_ERROR);
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.CAPTCHA_ERROR);
        }
        if (newPassword.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PASSWORD_CANNOT_BE_BLANK);
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        this.updateById(user);
    }

    @Override
    public String updateAvatar(Long userId, MultipartFile file) {
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND_AND_PASSWORD_ERROR);
        }

        String originalName = file.getOriginalFilename();
        String ext = "jpg";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase();
        }
        if (!"jpg".equals(ext) && !"jpeg".equals(ext) && !"png".equals(ext) && !"gif".equals(ext) && !"webp".equals(ext)) {
            throw new BusinessException(ErrorCode.FILE_FORMAT_ERROR);
        }

        String filename = userId + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;
        String relativePath = "/data/avatars/" + filename;

        try {
            Path uploadDir = Paths.get(dataDir, "avatars");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            Path filePath = uploadDir.resolve(filename);
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED);
        }

        user.setAvatar(relativePath);
        user.setUpdatedAt(LocalDateTime.now());
        this.updateById(user);

        return relativePath;
    }

    @Override
    public void deleteUser(Long id, Integer operatorRoleCode) {
        User user = this.getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND_AND_PASSWORD_ERROR);
        }
        if (!canDeleteUser(operatorRoleCode, user.getRoleCode())) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_USER);
        }
        if (!this.removeById(id)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND_AND_PASSWORD_ERROR);
        }
    }

    @Override
    public void updateUserStatus(Long id, Integer statusCode, Integer operatorRoleCode, Long operatorUserId) {
        User user = this.getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND_AND_PASSWORD_ERROR);
        }
        if (!canModifyUser(operatorRoleCode, user.getRoleCode())) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_USER);
        }
        if (user.getId().equals(operatorUserId)
                && (UserRoleEnum.SUPER_ADMIN.getCode() == operatorRoleCode
                    || UserRoleEnum.ROOT_ADMIN.getCode() == operatorRoleCode)) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF_STATUS);
        }

        int actualStatusCode = statusCode;
        if (statusCode == UserStatusEnum.ONLINE.getCode()
                && user.getStatusCode() == UserStatusEnum.DISABLED.getCode()) {
            actualStatusCode = UserStatusEnum.OFFLINE.getCode();
        }

        user.setStatusCode(actualStatusCode);
        user.setStatus(UserStatusEnum.fromCode(actualStatusCode).getDesc());
        user.setUpdatedAt(LocalDateTime.now());
        this.updateById(user);
    }

    private boolean canCreateRole(int operatorRole, int targetRole) {
        if (operatorRole == UserRoleEnum.ADMIN.getCode()) {
            return targetRole == UserRoleEnum.USER.getCode();
        }
        if (operatorRole == UserRoleEnum.SUPER_ADMIN.getCode()) {
            return targetRole == UserRoleEnum.USER.getCode()
                    || targetRole == UserRoleEnum.ADMIN.getCode();
        }
        if (operatorRole == UserRoleEnum.ROOT_ADMIN.getCode()) {
            return true;
        }
        return false;
    }

    private boolean canModifyUser(int operatorRole, int targetRole) {
        if (operatorRole == UserRoleEnum.ADMIN.getCode()) {
            return targetRole == UserRoleEnum.USER.getCode();
        }
        if (operatorRole == UserRoleEnum.SUPER_ADMIN.getCode()) {
            return true;
        }
        if (operatorRole == UserRoleEnum.ROOT_ADMIN.getCode()) {
            return true;
        }
        return false;
    }

    private boolean canModifyRole(int operatorRole) {
        return operatorRole == UserRoleEnum.ROOT_ADMIN.getCode();
    }

    private boolean canModifyPassword(int operatorRole) {
        // 只有最高管理员 (role=4) 才能修改密码
        return operatorRole == UserRoleEnum.ROOT_ADMIN.getCode();
    }

    private boolean canDeleteUser(int operatorRole, int targetRole) {
        if (operatorRole == UserRoleEnum.ADMIN.getCode()) {
            return false;
        }
        if (operatorRole == UserRoleEnum.SUPER_ADMIN.getCode()) {
            return targetRole == UserRoleEnum.USER.getCode()
                    || targetRole == UserRoleEnum.ADMIN.getCode();
        }
        if (operatorRole == UserRoleEnum.ROOT_ADMIN.getCode()) {
            return true;
        }
        return false;
    }

    private boolean isBlankPassword(String password) {
        return password.trim().isEmpty();
    }
}
