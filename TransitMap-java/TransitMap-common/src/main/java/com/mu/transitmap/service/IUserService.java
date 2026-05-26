package com.mu.transitmap.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mu.transitmap.dto.*;
import com.mu.transitmap.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mu.transitmap.vo.UserLoginVO;
import org.springframework.web.multipart.MultipartFile;

public interface IUserService extends IService<User> {
    UserLoginVO login(UserLoginDTO dto);
    UserLoginVO loginWithRole(UserLoginDTO dto, Integer requiredRoleCode);
    void register(UserRegisterDTO userDto);
    void logout(Long userId);
    UserLoginVO getUserInfo(Long userId);

    Page<User> getUserPage(UserManageQueryDTO dto);
    void createUser(UserManageCreateDTO dto, Integer operatorRoleCode);
    void updateUser(Long id, UserManageUpdateDTO dto, Integer operatorRoleCode, Long operatorUserId);
    void deleteUser(Long id, Integer operatorRoleCode);
    void updateUserStatus(Long id, Integer statusCode, Integer operatorRoleCode, Long operatorUserId);

    void updateProfile(Long userId, UserProfileUpdateDTO dto);
    void updatePassword(Long userId, String oldPassword, String newPassword);
    String updateAvatar(Long userId, MultipartFile file);
}
