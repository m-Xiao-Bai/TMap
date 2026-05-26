package com.mu.transitmap.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mu.transitmap.dto.UserManageCreateDTO;
import com.mu.transitmap.dto.UserManageQueryDTO;
import com.mu.transitmap.dto.UserManageUpdateDTO;
import com.mu.transitmap.entity.User;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.impl.UserServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/manage/user")
public class UserManageController {

    @Autowired
    private UserServiceImpl userService;

    @GetMapping("/list")
    public Result<Page<User>> getList(@Valid UserManageQueryDTO dto) {
        Page<User> page = userService.getUserPage(dto);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND_AND_PASSWORD_ERROR);
        }
        user.setPassword(null);
        return Result.success(user);
    }

    @PostMapping
    public Result<Map<String, String>> create(@RequestBody @Valid UserManageCreateDTO dto,
                                               HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        userService.createUser(dto, operatorRoleCode);
        return Result.success(null);
    }

    @PutMapping("/{id}")
    public Result<Map<String, String>> update(@PathVariable Long id,
                                               @RequestBody @Valid UserManageUpdateDTO dto,
                                               HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        Long operatorUserId = (Long) request.getAttribute("userId");
        if (operatorRoleCode == null || operatorUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        userService.updateUser(id, dto, operatorRoleCode, operatorUserId);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Map<String, String>> delete(@PathVariable Long id,
                                               HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        userService.deleteUser(id, operatorRoleCode);
        return Result.success(null);
    }

    @PutMapping("/{id}/status")
    public Result<Map<String, String>> updateStatus(@PathVariable Long id,
                                                     @RequestParam Integer statusCode,
                                                     HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        Long operatorUserId = (Long) request.getAttribute("userId");
        if (operatorRoleCode == null || operatorUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        userService.updateUserStatus(id, statusCode, operatorRoleCode, operatorUserId);
        return Result.success(null);
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer statusCode,
            @RequestParam(required = false) Integer roleCode,
            @RequestParam(required = false) Integer onlineStatus,
            @RequestParam(required = false) String ids) {

        List<User> users;
        if (StringUtils.hasText(ids)) {
            List<Long> idList = Arrays.stream(ids.split(","))
                    .map(Long::parseLong).collect(Collectors.toList());
            users = userService.listByIds(idList);
        } else {
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(keyword)) {
                wrapper.and(w -> w
                        .like(User::getUsername, keyword)
                        .or().like(User::getEmail, keyword)
                        .or().like(User::getMobile, keyword)
                );
            }
            if (statusCode != null) wrapper.eq(User::getStatusCode, statusCode);
            if (roleCode != null) wrapper.eq(User::getRoleCode, roleCode);
            if (onlineStatus != null) {
                if (onlineStatus == 1) wrapper.eq(User::getStatusCode, 1);
                else wrapper.ne(User::getStatusCode, 1);
            }
            wrapper.orderByDesc(User::getCreatedAt);
            users = userService.list(wrapper);
        }

        List<User> finalUsers = users;
        StreamingResponseBody body = outputStream -> {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String[] headers = {"ID", "用户名", "邮箱", "手机号", "性别", "角色", "在线状态", "账号状态", "生日", "年龄", "国家", "城市", "创建时间", "更新时间"};

            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("用户数据");
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                CellStyle headerStyle = workbook.createCellStyle();
                headerStyle.setFont(headerFont);

                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                int rowNum = 1;
                for (User user : finalUsers) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(user.getId() != null ? user.getId().doubleValue() : 0);
                    row.createCell(1).setCellValue(user.getUsername() != null ? user.getUsername() : "");
                    row.createCell(2).setCellValue(user.getEmail() != null ? user.getEmail() : "");
                    row.createCell(3).setCellValue(user.getMobile() != null ? user.getMobile() : "");
                    row.createCell(4).setCellValue(user.getGender() != null ? user.getGender() : "");
                    row.createCell(5).setCellValue(user.getRole() != null ? user.getRole() : "");
                    row.createCell(6).setCellValue(user.getStatusCode() != null && user.getStatusCode() == 1 ? "在线" : "离线");
                    row.createCell(7).setCellValue(user.getStatusCode() != null && user.getStatusCode() == 3 ? "禁用" : "启用");
                    row.createCell(8).setCellValue(user.getBirthday() != null ? user.getBirthday().toString() : "");
                    row.createCell(9).setCellValue(user.getAge() != null ? user.getAge().doubleValue() : 0);
                    row.createCell(10).setCellValue(user.getCountryName() != null ? user.getCountryName() : "");
                    row.createCell(11).setCellValue(user.getCityName() != null ? user.getCityName() : "");
                    row.createCell(12).setCellValue(user.getCreatedAt() != null ? user.getCreatedAt().format(dtf) : "");
                    row.createCell(13).setCellValue(user.getUpdatedAt() != null ? user.getUpdatedAt().format(dtf) : "");
                }

                for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
                workbook.write(outputStream);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header("Content-Disposition", "attachment; filename=users.xlsx")
                .body(body);
    }
}
