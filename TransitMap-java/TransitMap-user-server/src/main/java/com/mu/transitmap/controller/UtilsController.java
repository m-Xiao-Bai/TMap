package com.mu.transitmap.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mu.transitmap.entity.City;
import com.mu.transitmap.entity.MetroLine;
import com.mu.transitmap.entity.MetroStation;
import com.mu.transitmap.entity.SystemConfig;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.CaptchaImageService;
import com.mu.transitmap.service.EmailService;
import com.mu.transitmap.service.impl.CityServiceImpl;
import com.mu.transitmap.service.impl.CountryServiceImpl;
import com.mu.transitmap.service.impl.MetroLineServiceImpl;
import com.mu.transitmap.service.impl.MetroStationServiceImpl;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;


@RestController
@Validated
@RequestMapping("/utils")
public class UtilsController {

    @Autowired
    private CaptchaImageService captchaImage;
    @Autowired
    private EmailService emailService;
    @Autowired
    private SystemConfigServiceImpl systemConfigService;
    @Autowired
    private MetroLineServiceImpl metroLineService;
    @Autowired
    private MetroStationServiceImpl metroStationService;
    @Autowired
    private CityServiceImpl cityService;
    @Autowired
    private CountryServiceImpl countryService;
    /**
     * 获取图形验证码
     */
    @GetMapping("/captcha/image")
    public Result<Map<String, String>> getCaptcha() {
        try {
            return Result.success(captchaImage.generateCaptcha());
        } catch (RedisConnectionFailureException e) {
            throw new BusinessException(ErrorCode.REDIS_UNAVAILABLE);
        }
    }
    /**
     * 发送邮箱验证码
     * */
    @PostMapping("/captcha/send")
    public Result<Map<String, String>> postSend(@RequestParam("email")
                                                @NotBlank(message = "邮箱不能为空")
                                                @Email(message = "邮箱格式不正确")
                                                String email
    ) {
        try {
            emailService.sendVerifyCode(email);
            return Result.success(null);
        } catch (RedisConnectionFailureException e) {
            throw new BusinessException(ErrorCode.REDIS_UNAVAILABLE);
        }
    }

    // ═══════════════════════════════════════════
    //  公开接口（无需登录）
    // ═══════════════════════════════════════════

    /** 获取公开系统配置（首页内容等） */
    @GetMapping("/config/public")
    public Result<List<SystemConfig>> getPublicConfigs() {
        return Result.success(systemConfigService.getAllPublicConfigs());
    }

    /** 获取所有运营中的地铁线路 */
    @GetMapping("/metro/lines")
    public Result<List<Map<String, Object>>> getMetroLines() {
        List<MetroLine> lines = metroLineService.lambdaQuery()
                .eq(MetroLine::getStatusCode, 1)
                .orderByAsc(MetroLine::getCityName)
                .orderByAsc(MetroLine::getLineNo)
                .list();
        List<Map<String, Object>> result = lines.stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("lineName", l.getLineName());
            m.put("lineNo", l.getLineNo());
            m.put("lineColor", l.getLineColor());
            m.put("stationCount", l.getStationCount());
            m.put("cityId", l.getCityId());
            m.put("cityName", l.getCityName());
            m.put("totalKm", l.getTotalKm());
            return m;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    /** 获取所有运营中的地铁站点 */
    @GetMapping("/metro/stations")
    public Result<List<Map<String, Object>>> getMetroStations() {
        List<MetroStation> stations = metroStationService.lambdaQuery()
                .eq(MetroStation::getStatusCode, 1)
                .list();
        List<Map<String, Object>> result = stations.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("stationName", s.getStationName());
            m.put("longitude", s.getLongitude());
            m.put("latitude", s.getLatitude());
            m.put("isTransfer", s.getIsTransfer());
            m.put("lineIds", s.getLineIds());
            m.put("lineNames", s.getLineNames());
            m.put("prevStationIds", s.getPrevStationIds());
            m.put("prevStationDistances", s.getPrevStationDistances());
            m.put("nextStationIds", s.getNextStationIds());
            m.put("nextStationDistances", s.getNextStationDistances());
            m.put("cityId", s.getCityId());
            m.put("cityName", s.getCityName());
            return m;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    /** 获取地铁统计概览 */
    @GetMapping("/metro/stats")
    public Result<Map<String, Object>> getMetroStats() {
        long lineCount = metroLineService.lambdaQuery().eq(MetroLine::getStatusCode, 1).count();
        long stationCount = metroStationService.lambdaQuery().eq(MetroStation::getStatusCode, 1).count();
        long cityCount = cityService.lambdaQuery().eq(City::getStatusCode, 1).count();
        // 只查询 totalKm 字段，避免加载全部实体
        List<BigDecimal> kmList = metroLineService.lambdaQuery()
                .eq(MetroLine::getStatusCode, 1)
                .select(MetroLine::getTotalKm)
                .list()
                .stream()
                .map(l -> l.getTotalKm() != null ? l.getTotalKm() : BigDecimal.ZERO)
                .collect(Collectors.toList());
        BigDecimal totalKm = kmList.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("cityCount", cityCount);
        stats.put("lineCount", lineCount);
        stats.put("stationCount", stationCount);
        stats.put("totalKm", totalKm);
        return Result.success(stats);
    }

    /** 获取所有已上线的城市列表 */
    @GetMapping("/cities")
    public Result<List<Map<String, Object>>> getPublicCities() {
        List<City> cities = cityService.lambdaQuery()
                .eq(City::getStatusCode, 3)
                .orderByDesc(City::getMetroLineCount)
                .list();
        List<Map<String, Object>> result = cities.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("cityName", c.getCityName());
            m.put("countryId", c.getCountryId());
            m.put("countryName", c.getCountryName());
            m.put("metroLineCount", c.getMetroLineCount());
            return m;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    /** 获取有运营线路的国家列表 */
    @GetMapping("/countries")
    public Result<List<Map<String, Object>>> getPublicCountries() {
        // 从有运营线路的城市中提取不重复的国家
        Set<Long> countryIds = new LinkedHashSet<>();
        Map<Long, String> countryNameMap = new LinkedHashMap<>();
        List<City> cities = cityService.lambdaQuery()
                .eq(City::getStatusCode, 3)
                .select(City::getCountryId, City::getCountryName)
                .list();
        for (City c : cities) {
            if (c.getCountryId() != null) {
                countryIds.add(c.getCountryId());
                countryNameMap.put(c.getCountryId(), c.getCountryName());
            }
        }
        List<Map<String, Object>> result = countryIds.stream().map(id -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("countryName", countryNameMap.get(id));
            return m;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    /** 获取指定线路的有序站点列表（拓扑图用） */
    @GetMapping("/metro/lines/{lineId}/stations-ordered")
    public Result<Map<String, Object>> getLineOrderedStations(@PathVariable Long lineId) {
        MetroLine line = metroLineService.getById(lineId);
        if (line == null) {
            throw new BusinessException(ErrorCode.METRO_LINE_NOT_FOUND);
        }
        Map<String, Object> data = metroStationService.getOrderedStationsByLineId(lineId);
        data.put("lineId", line.getId());
        data.put("lineName", line.getLineName());
        data.put("lineColor", line.getLineColor());
        return Result.success(data);
    }

}
