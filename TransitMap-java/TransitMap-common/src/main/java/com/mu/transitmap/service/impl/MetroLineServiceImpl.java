package com.mu.transitmap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mu.transitmap.dto.MetroLineManageCreateDTO;
import com.mu.transitmap.dto.MetroLineManageQueryDTO;
import com.mu.transitmap.dto.MetroLineManageUpdateDTO;
import com.mu.transitmap.entity.City;
import com.mu.transitmap.entity.Country;
import com.mu.transitmap.entity.MetroLine;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.enums.UserRoleEnum;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.mapper.MetroLineMapper;
import com.mu.transitmap.mapper.MetroStationMapper;
import com.mu.transitmap.entity.MetroStation;
import com.mu.transitmap.service.ICityService;
import com.mu.transitmap.service.ICountryService;
import com.mu.transitmap.service.IMetroLineService;
import com.mu.transitmap.utils.RedisUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class MetroLineServiceImpl extends ServiceImpl<MetroLineMapper, MetroLine> implements IMetroLineService {

    @Autowired
    private ICountryService countryService;

    @Autowired
    private ICityService cityService;

    @Autowired
    private SystemConfigServiceImpl systemConfigService;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private MetroStationMapper metroStationMapper;

    @Override
    public Page<MetroLine> getMetroLinePage(MetroLineManageQueryDTO dto) {
        int pn = dto.getPageNum() != null ? dto.getPageNum() : 1;
        int ps = dto.getPageSize() != null ? dto.getPageSize() : systemConfigService.getConfigInt("pagination.default_size", 10);
        LambdaQueryWrapper<MetroLine> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dto.getKeyword())) {
            wrapper.and(w -> w
                    .like(MetroLine::getLineName, dto.getKeyword())
                    .or()
                    .like(MetroLine::getLineNo, dto.getKeyword())
                    .or()
                    .like(MetroLine::getCityName, dto.getKeyword())
                    .or()
                    .like(MetroLine::getCountryName, dto.getKeyword())
            );
        }
        if (dto.getCountryId() != null) {
            wrapper.eq(MetroLine::getCountryId, dto.getCountryId());
        }
        if (dto.getCityId() != null) {
            wrapper.eq(MetroLine::getCityId, dto.getCityId());
        }
        if (dto.getStatusCode() != null) {
            wrapper.eq(MetroLine::getStatusCode, dto.getStatusCode());
        }
        if (dto.getMinTotalKm() != null) {
            wrapper.ge(MetroLine::getTotalKm, dto.getMinTotalKm());
        }
        if (dto.getMaxTotalKm() != null) {
            wrapper.le(MetroLine::getTotalKm, dto.getMaxTotalKm());
        }
        if (dto.getMinStationCount() != null) {
            wrapper.ge(MetroLine::getStationCount, dto.getMinStationCount());
        }
        if (dto.getMaxStationCount() != null) {
            wrapper.le(MetroLine::getStationCount, dto.getMaxStationCount());
        }
        if (dto.getMinTrainCount() != null) {
            wrapper.ge(MetroLine::getTrainCount, dto.getMinTrainCount());
        }
        if (dto.getMaxTrainCount() != null) {
            wrapper.le(MetroLine::getTrainCount, dto.getMaxTrainCount());
        }
        if (dto.getMinAvgSpeed() != null) {
            wrapper.ge(MetroLine::getAvgSpeed, dto.getMinAvgSpeed());
        }
        if (dto.getMaxAvgSpeed() != null) {
            wrapper.le(MetroLine::getAvgSpeed, dto.getMaxAvgSpeed());
        }

        if (StringUtils.hasText(dto.getSortField())) {
            boolean asc = "asc".equalsIgnoreCase(dto.getSortOrder());
            switch (dto.getSortField()) {
                case "lineNo":
                    wrapper.orderBy(true, asc, MetroLine::getLineNo);
                    break;
                case "totalKm":
                    wrapper.orderBy(true, asc, MetroLine::getTotalKm);
                    break;
                case "stationCount":
                    wrapper.orderBy(true, asc, MetroLine::getStationCount);
                    break;
                case "openDate":
                    wrapper.orderBy(true, asc, MetroLine::getOpenDate);
                    break;
                case "createdAt":
                    wrapper.orderBy(true, asc, MetroLine::getCreatedAt);
                    break;
                default:
                    wrapper.orderByDesc(MetroLine::getCreatedAt);
                    break;
            }
        } else {
            wrapper.orderByDesc(MetroLine::getCreatedAt);
        }
        return this.page(new Page<>(pn, ps), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createMetroLine(MetroLineManageCreateDTO dto, Integer operatorRoleCode) {
        if (!canCreate(operatorRoleCode)) {
            throw new BusinessException(ErrorCode.METRO_LINE_PERMISSION_DENIED);
        }

        Country country = countryService.getById(dto.getCountryId());
        if (country == null) {
            throw new BusinessException(ErrorCode.METRO_LINE_WRONG_COUNTRY);
        }

        City city = cityService.getById(dto.getCityId());
        if (city == null) {
            throw new BusinessException(ErrorCode.METRO_LINE_WRONG_CITY);
        }

        if (this.lambdaQuery()
                .eq(MetroLine::getCityId, dto.getCityId())
                .eq(MetroLine::getLineNo, dto.getLineNo())
                .exists()) {
            throw new BusinessException(ErrorCode.METRO_LINE_ALREADY_EXISTS);
        }

        MetroLine line = new MetroLine();
        line.setCountryId(country.getId());
        line.setCountryName(country.getCountryName());
        line.setCityId(city.getId());
        line.setCityName(city.getCityName());
        line.setCityNameEn(city.getCityNameEn());
        line.setLineName(dto.getLineName());
        line.setLineNo(dto.getLineNo());
        line.setLineColor(dto.getLineColor());
        line.setLineColorCn(dto.getLineColorCn());
        line.setTotalKm(dto.getTotalKm() != null ? dto.getTotalKm() : java.math.BigDecimal.ZERO);
        line.setStationCount(dto.getStationCount() != null ? dto.getStationCount() : 0);
        line.setTransferLineCount(dto.getTransferLineCount() != null ? dto.getTransferLineCount() : 0);
        line.setTransferLines(dto.getTransferLines());
        line.setTransferStationCount(dto.getTransferStationCount() != null ? dto.getTransferStationCount() : 0);
        line.setTransferStations(dto.getTransferStations());
        line.setTrainCount(dto.getTrainCount() != null ? dto.getTrainCount() : 0);
        line.setAvgSpeed(dto.getAvgSpeed());
        line.setFullTime(dto.getFullTime());
        line.setExtra(dto.getExtra());

        try {
            if (StringUtils.hasText(dto.getFirstTime())) {
                line.setFirstTime(LocalTime.parse(dto.getFirstTime()));
            }
            if (StringUtils.hasText(dto.getLastTime())) {
                line.setLastTime(LocalTime.parse(dto.getLastTime()));
            }
            if (StringUtils.hasText(dto.getOpenDate())) {
                line.setOpenDate(LocalDate.parse(dto.getOpenDate()));
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.METRO_LINE_DATE_FORMAT_ERROR);
        }

        int statusCode = dto.getStatusCode() != null ? dto.getStatusCode() : 0;
        line.setStatusCode(statusCode);
        line.setStatus(systemConfigService.getStatusName(statusCode));

        if (!this.save(line)) {
            throw new BusinessException(ErrorCode.METRO_LINE_IMPORT_FAILED);
        }
        redisUtils.deleteMetroLineListCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMetroLine(Long id, MetroLineManageUpdateDTO dto, Integer operatorRoleCode) {
        MetroLine line = this.getById(id);
        if (line == null) {
            throw new BusinessException(ErrorCode.METRO_LINE_NOT_FOUND);
        }

        if (dto.getLineName() != null && !dto.getLineName().equals(line.getLineName())) {
            if (!canModifyLineName(operatorRoleCode)) {
                throw new BusinessException(ErrorCode.METRO_LINE_CANNOT_MODIFY_NAME);
            }
            line.setLineName(dto.getLineName());
        }

        if (dto.getLineNo() != null && !dto.getLineNo().equals(line.getLineNo())) {
            if (!canModifyLineName(operatorRoleCode)) {
                throw new BusinessException(ErrorCode.METRO_LINE_CANNOT_MODIFY_NAME);
            }
            if (this.lambdaQuery()
                    .eq(MetroLine::getCityId, line.getCityId())
                    .eq(MetroLine::getLineNo, dto.getLineNo())
                    .exists()) {
                throw new BusinessException(ErrorCode.METRO_LINE_ALREADY_EXISTS);
            }
            line.setLineNo(dto.getLineNo());
        }

        if (dto.getCityId() != null && !dto.getCityId().equals(line.getCityId())) {
            if (!canModifyLineName(operatorRoleCode)) {
                throw new BusinessException(ErrorCode.METRO_LINE_CANNOT_MODIFY_NAME);
            }
            City city = cityService.getById(dto.getCityId());
            if (city == null) {
                throw new BusinessException(ErrorCode.METRO_LINE_WRONG_CITY);
            }
            line.setCityId(city.getId());
            line.setCityName(city.getCityName());
            line.setCityNameEn(city.getCityNameEn());
        }

        if (dto.getCountryId() != null && !dto.getCountryId().equals(line.getCountryId())) {
            if (!canModifyLineName(operatorRoleCode)) {
                throw new BusinessException(ErrorCode.METRO_LINE_CANNOT_MODIFY_NAME);
            }
            Country country = countryService.getById(dto.getCountryId());
            if (country == null) {
                throw new BusinessException(ErrorCode.METRO_LINE_WRONG_COUNTRY);
            }
            line.setCountryId(country.getId());
            line.setCountryName(country.getCountryName());
        }

        if (!canModifyAllFields(operatorRoleCode)) {
            if (dto.getStatusCode() != null) {
                line.setStatusCode(dto.getStatusCode());
                line.setStatus(systemConfigService.getStatusName(dto.getStatusCode()));
            }
            line.setUpdatedAt(LocalDateTime.now());
            if (!this.updateById(line)) {
                throw new BusinessException(ErrorCode.OPERATION_FAILED);
            }
            redisUtils.deleteMetroLineListCache();
            return;
        }

        if (dto.getLineColor() != null) {
            line.setLineColor(dto.getLineColor());
        }
        if (dto.getLineColorCn() != null) {
            line.setLineColorCn(dto.getLineColorCn());
        }
        if (dto.getTotalKm() != null) {
            line.setTotalKm(dto.getTotalKm());
        }
        if (dto.getStationCount() != null) {
            line.setStationCount(dto.getStationCount());
        }
        if (dto.getTransferLineCount() != null) {
            line.setTransferLineCount(dto.getTransferLineCount());
        }
        if (dto.getTransferLines() != null) {
            line.setTransferLines(dto.getTransferLines());
        }
        if (dto.getTransferStationCount() != null) {
            line.setTransferStationCount(dto.getTransferStationCount());
        }
        if (dto.getTransferStations() != null) {
            line.setTransferStations(dto.getTransferStations());
        }
        if (dto.getTrainCount() != null) {
            line.setTrainCount(dto.getTrainCount());
        }
        if (dto.getAvgSpeed() != null) {
            line.setAvgSpeed(dto.getAvgSpeed());
        }
        if (dto.getFullTime() != null) {
            line.setFullTime(dto.getFullTime());
        }
        if (dto.getExtra() != null) {
            line.setExtra(dto.getExtra());
        }
        if (dto.getStatusCode() != null) {
            line.setStatusCode(dto.getStatusCode());
            line.setStatus(systemConfigService.getStatusName(dto.getStatusCode()));
        }

        try {
            if (dto.getFirstTime() != null) {
                line.setFirstTime(LocalTime.parse(dto.getFirstTime()));
            }
            if (dto.getLastTime() != null) {
                line.setLastTime(LocalTime.parse(dto.getLastTime()));
            }
            if (dto.getOpenDate() != null) {
                line.setOpenDate(LocalDate.parse(dto.getOpenDate()));
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.METRO_LINE_DATE_FORMAT_ERROR);
        }

        line.setUpdatedAt(LocalDateTime.now());
        if (!this.updateById(line)) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED);
        }
        redisUtils.deleteMetroLineListCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMetroLine(Long id) {
        MetroLine line = this.getById(id);
        if (line == null) {
            throw new BusinessException(ErrorCode.METRO_LINE_NOT_FOUND);
        }
        // 检查是否有站点引用此线路
        long stationCount = metroStationMapper.selectCount(
                new LambdaQueryWrapper<MetroStation>()
                        .apply("JSON_CONTAINS(line_ids, {0})", "\"" + id + "\""));
        if (stationCount > 0) {
            throw new BusinessException(ErrorCode.METRO_LINE_HAS_STATIONS);
        }
        this.removeById(id);
        redisUtils.deleteMetroLineListCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteMetroLines(List<Long> ids, Integer operatorRoleCode) {
        if (!canDelete(operatorRoleCode)) {
            throw new BusinessException(ErrorCode.METRO_LINE_PERMISSION_DENIED);
        }
        for (Long id : ids) {
            long stationCount = metroStationMapper.selectCount(
                    new LambdaQueryWrapper<MetroStation>()
                            .apply("JSON_CONTAINS(line_ids, {0})", "\"" + id + "\""));
            if (stationCount > 0) {
                throw new BusinessException(ErrorCode.METRO_LINE_HAS_STATIONS);
            }
        }
        boolean removed = this.removeBatchByIds(ids);
        if (!removed) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED);
        }
        redisUtils.deleteMetroLineListCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchImportMetroLines(List<MetroLineManageCreateDTO> dtoList, Integer operatorRoleCode) {
        if (!canCreate(operatorRoleCode)) {
            throw new BusinessException(ErrorCode.METRO_LINE_PERMISSION_DENIED);
        }

        List<MetroLine> lines = new ArrayList<>();
        int successCount = 0;

        for (MetroLineManageCreateDTO dto : dtoList) {
            if (!StringUtils.hasText(dto.getLineName()) || !StringUtils.hasText(dto.getLineNo())
                    || dto.getCityId() == null) {
                continue;
            }
            if (this.lambdaQuery()
                    .eq(MetroLine::getCityId, dto.getCityId())
                    .eq(MetroLine::getLineNo, dto.getLineNo())
                    .exists()) {
                continue;
            }

            Country country = dto.getCountryId() != null ? countryService.getById(dto.getCountryId()) : null;
            City city = cityService.getById(dto.getCityId());
            if (city == null) continue;

            MetroLine line = new MetroLine();
            if (country != null) {
                line.setCountryId(country.getId());
                line.setCountryName(country.getCountryName());
            }
            line.setCityId(city.getId());
            line.setCityName(city.getCityName());
            line.setCityNameEn(city.getCityNameEn());
            line.setLineName(dto.getLineName());
            line.setLineNo(dto.getLineNo());
            line.setLineColor(dto.getLineColor());
            line.setLineColorCn(dto.getLineColorCn());
            line.setTotalKm(dto.getTotalKm() != null ? dto.getTotalKm() : java.math.BigDecimal.ZERO);
            line.setStationCount(dto.getStationCount() != null ? dto.getStationCount() : 0);
            line.setTrainCount(dto.getTrainCount() != null ? dto.getTrainCount() : 0);
            line.setAvgSpeed(dto.getAvgSpeed());
            line.setExtra(dto.getExtra());

            int statusCode = dto.getStatusCode() != null ? dto.getStatusCode() : 0;
            line.setStatusCode(statusCode);
            line.setStatus(systemConfigService.getStatusName(statusCode));

            lines.add(line);
            successCount++;
        }

        if (!lines.isEmpty()) {
            this.saveBatch(lines);
            redisUtils.deleteMetroLineListCache();
        }
        return successCount;
    }

    private boolean canCreate(int operatorRole) {
        return operatorRole == UserRoleEnum.ROOT_ADMIN.getCode();
    }

    private boolean canDelete(int operatorRole) {
        return operatorRole == UserRoleEnum.ROOT_ADMIN.getCode();
    }

    private boolean canModifyAllFields(int operatorRole) {
        return operatorRole == UserRoleEnum.SUPER_ADMIN.getCode()
                || operatorRole == UserRoleEnum.ROOT_ADMIN.getCode();
    }

    private boolean canModifyLineName(int operatorRole) {
        return operatorRole == UserRoleEnum.ROOT_ADMIN.getCode();
    }

}
