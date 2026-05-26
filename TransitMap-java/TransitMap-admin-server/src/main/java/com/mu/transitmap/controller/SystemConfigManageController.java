package com.mu.transitmap.controller;

import com.mu.transitmap.entity.SystemConfig;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/manage/system-config")
public class SystemConfigManageController {

    @Autowired
    private SystemConfigServiceImpl systemConfigService;

    @GetMapping("/all")
    public Result<Map<String, List<SystemConfig>>> getAll(HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode != 4) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return Result.success(systemConfigService.getAllConfigsGrouped());
    }

    @GetMapping("/public")
    public Result<List<SystemConfig>> getPublic() {
        return Result.success(systemConfigService.getAllPublicConfigs());
    }

    @PutMapping("/update")
    public Result<Map<String, String>> update(@RequestBody List<SystemConfig> configs,
                                               HttpServletRequest request) {
        Integer roleCode = (Integer) request.getAttribute("roleCode");
        if (roleCode == null || roleCode != 4) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        systemConfigService.updateConfigs(configs);
        return Result.success(null);
    }
}
