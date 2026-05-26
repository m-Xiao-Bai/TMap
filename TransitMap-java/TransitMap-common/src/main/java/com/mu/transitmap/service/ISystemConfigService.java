package com.mu.transitmap.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mu.transitmap.entity.SystemConfig;

import java.util.List;
import java.util.Map;

public interface ISystemConfigService extends IService<SystemConfig> {

    String getConfigValue(String key);

    List<SystemConfig> getAllPublicConfigs();

    Map<String, List<SystemConfig>> getAllConfigsGrouped();

    void updateConfigs(List<SystemConfig> configs);
}
