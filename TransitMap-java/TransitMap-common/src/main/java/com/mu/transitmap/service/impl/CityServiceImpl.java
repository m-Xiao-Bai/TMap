package com.mu.transitmap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mu.transitmap.dto.CityManageCreateDTO;
import com.mu.transitmap.dto.CityManageQueryDTO;
import com.mu.transitmap.dto.CityManageUpdateDTO;
import com.mu.transitmap.entity.City;
import com.mu.transitmap.entity.Country;
import com.mu.transitmap.entity.MetroLine;
import com.mu.transitmap.entity.MetroStation;
import com.mu.transitmap.enums.CountryStatusEnum;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.enums.UserRoleEnum;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.mapper.CityMapper;
import com.mu.transitmap.mapper.MetroLineMapper;
import com.mu.transitmap.mapper.MetroStationMapper;
import com.mu.transitmap.service.ICityService;
import com.mu.transitmap.service.ICountryService;
import com.mu.transitmap.utils.RedisUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CityServiceImpl extends ServiceImpl<CityMapper, City> implements ICityService {

    @Autowired
    private ICountryService countryService;

    @Autowired
    private MetroStationMapper metroStationMapper;

    @Autowired
    private MetroLineMapper metroLineMapper;

    @Autowired
    private RedisUtils redisUtils;

    @Override
    public Page<City> getCityPage(CityManageQueryDTO dto) {
        LambdaQueryWrapper<City> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dto.getKeyword())) {
            wrapper.and(w -> w
                    .like(City::getCityName, dto.getKeyword())
                    .or()
                    .like(City::getCityNameEn, dto.getKeyword())
                    .or()
                    .like(City::getCityAlias, dto.getKeyword())
                    .or()
                    .like(City::getCountryName, dto.getKeyword())
            );
        }
        if (dto.getCountryId() != null) {
            wrapper.eq(City::getCountryId, dto.getCountryId());
        }
        if (dto.getOnlineStatusCode() != null) {
            wrapper.eq(City::getStatusCode, dto.getOnlineStatusCode());
        } else if (dto.getStatusCode() != null) {
            wrapper.eq(City::getStatusCode, dto.getStatusCode());
        }
        if (dto.getMinMetroCount() != null) {
            wrapper.ge(City::getMetroCount, dto.getMinMetroCount());
        }
        if (dto.getMaxMetroCount() != null) {
            wrapper.le(City::getMetroCount, dto.getMaxMetroCount());
        }
        if (dto.getMinMetroLineCount() != null) {
            wrapper.ge(City::getMetroLineCount, dto.getMinMetroLineCount());
        }
        if (dto.getMaxMetroLineCount() != null) {
            wrapper.le(City::getMetroLineCount, dto.getMaxMetroLineCount());
        }
        if (dto.getMinHsrCount() != null) {
            wrapper.ge(City::getHsrCount, dto.getMinHsrCount());
        }
        if (dto.getMaxHsrCount() != null) {
            wrapper.le(City::getHsrCount, dto.getMaxHsrCount());
        }
        if (dto.getMinMetroKm() != null) {
            wrapper.ge(City::getMetroKm, dto.getMinMetroKm());
        }
        if (dto.getMaxMetroKm() != null) {
            wrapper.le(City::getMetroKm, dto.getMaxMetroKm());
        }
        if (dto.getMinHsrKm() != null) {
            wrapper.ge(City::getHsrKm, dto.getMinHsrKm());
        }
        if (dto.getMaxHsrKm() != null) {
            wrapper.le(City::getHsrKm, dto.getMaxHsrKm());
        }
        if (dto.getMinPopulation() != null) {
            wrapper.ge(City::getPopulation, dto.getMinPopulation());
        }
        if (dto.getMaxPopulation() != null) {
            wrapper.le(City::getPopulation, dto.getMaxPopulation());
        }

        if (StringUtils.hasText(dto.getSortField())) {
            boolean asc = "asc".equalsIgnoreCase(dto.getSortOrder());
            switch (dto.getSortField()) {
                case "createdAt":
                    wrapper.orderBy(true, asc, City::getCreatedAt);
                    break;
                case "updatedAt":
                    wrapper.orderBy(true, asc, City::getUpdatedAt);
                    break;
                case "cityName":
                    wrapper.orderBy(true, asc, City::getCityName);
                    break;
                case "metroKm":
                    wrapper.orderBy(true, asc, City::getMetroKm);
                    break;
                case "hsrKm":
                    wrapper.orderBy(true, asc, City::getHsrKm);
                    break;
                case "metroLineCount":
                    wrapper.orderBy(true, asc, City::getMetroLineCount);
                    break;
                case "population":
                    wrapper.orderBy(true, asc, City::getPopulation);
                    break;
                default:
                    wrapper.orderByDesc(City::getCreatedAt);
                    break;
            }
        } else {
            wrapper.orderByDesc(City::getCreatedAt);
        }
        int pn = dto.getPageNum() != null ? dto.getPageNum() : 1;
        int ps = dto.getPageSize() != null ? dto.getPageSize() : 10;
        return this.page(new Page<>(pn, ps), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createCity(CityManageCreateDTO dto, Integer operatorRoleCode) {
        if (!canCreate(operatorRoleCode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Country country = countryService.getById(dto.getCountryId());
        if (country == null) {
            throw new BusinessException(ErrorCode.CITY_WRONG_COUNTRY);
        }

        if (this.lambdaQuery().eq(City::getCityName, dto.getCityName()).exists()) {
            throw new BusinessException(ErrorCode.CITY_NAME_ALREADY_EXISTS);
        }

        City city = new City();
        city.setCountryId(country.getId());
        city.setCountryName(country.getCountryName());
        city.setCityName(dto.getCityName());
        city.setCityNameEn(dto.getCityNameEn());
        city.setCityAlias(dto.getCityAlias());
        city.setMetroLineLogo(dto.getMetroLineLogo());
        city.setMetroCount(dto.getMetroCount() != null ? dto.getMetroCount() : 0);
        city.setMetroLineCount(dto.getMetroLineCount() != null ? dto.getMetroLineCount() : 0);
        city.setHsrCount(dto.getHsrCount() != null ? dto.getHsrCount() : 0);
        city.setMetroKm(dto.getMetroKm() != null ? dto.getMetroKm() : java.math.BigDecimal.ZERO);
        city.setHsrKm(dto.getHsrKm() != null ? dto.getHsrKm() : java.math.BigDecimal.ZERO);
        city.setPopulation(dto.getPopulation());
        city.setMetroLines(dto.getMetroLines());
        city.setExtra(dto.getExtra());
        city.setStatusCode(dto.getStatusCode() != null ? dto.getStatusCode() : CountryStatusEnum.PENDING.getCode());
        city.setStatus(CountryStatusEnum.fromCode(city.getStatusCode()).getDescription());

        if (!this.save(city)) {
            throw new BusinessException(ErrorCode.CITY_IMPORT_FAILED);
        }
        redisUtils.deleteCityListCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCity(Long id, CityManageUpdateDTO dto, Integer operatorRoleCode) {
        City city = this.getById(id);
        if (city == null) {
            throw new BusinessException(ErrorCode.CITY_NOT_FOUND);
        }

        String oldCityName = city.getCityName();
        boolean nameChanged = dto.getCityName() != null && !dto.getCityName().equals(oldCityName);

        if (nameChanged) {
            if (!canModifyCityName(operatorRoleCode)) {
                throw new BusinessException(ErrorCode.CANNOT_MODIFY_CITY_NAME);
            }
            if (this.lambdaQuery().eq(City::getCityName, dto.getCityName()).exists()) {
                throw new BusinessException(ErrorCode.CITY_NAME_ALREADY_EXISTS);
            }
            city.setCityName(dto.getCityName());
        }

        if (dto.getCountryId() != null && !dto.getCountryId().equals(city.getCountryId())) {
            if (!canModifyCityName(operatorRoleCode)) {
                throw new BusinessException(ErrorCode.CANNOT_MODIFY_CITY_NAME);
            }
            Country country = countryService.getById(dto.getCountryId());
            if (country == null) {
                throw new BusinessException(ErrorCode.CITY_WRONG_COUNTRY);
            }
            city.setCountryId(country.getId());
            city.setCountryName(country.getCountryName());
        }

        if (!canModifyAllFields(operatorRoleCode)) {
            if (dto.getStatusCode() != null) {
                city.setStatusCode(dto.getStatusCode());
                city.setStatus(CountryStatusEnum.fromCode(dto.getStatusCode()).getDescription());
            }
            city.setUpdatedAt(LocalDateTime.now());
            if (!this.updateById(city)) {
                throw new BusinessException(ErrorCode.OPERATION_FAILED);
            }
            if (nameChanged) {
                cascadeUpdateCityName(id, dto.getCityName());
            }
            redisUtils.deleteCityListCache();
            return;
        }

        if (dto.getCityNameEn() != null) {
            city.setCityNameEn(dto.getCityNameEn());
        }
        if (dto.getCityAlias() != null) {
            city.setCityAlias(dto.getCityAlias());
        }
        if (dto.getMetroLineLogo() != null) {
            city.setMetroLineLogo(dto.getMetroLineLogo());
        }
        if (dto.getMetroCount() != null) {
            city.setMetroCount(dto.getMetroCount());
        }
        if (dto.getMetroLineCount() != null) {
            city.setMetroLineCount(dto.getMetroLineCount());
        }
        if (dto.getHsrCount() != null) {
            city.setHsrCount(dto.getHsrCount());
        }
        if (dto.getMetroKm() != null) {
            city.setMetroKm(dto.getMetroKm());
        }
        if (dto.getHsrKm() != null) {
            city.setHsrKm(dto.getHsrKm());
        }
        if (dto.getPopulation() != null) {
            city.setPopulation(dto.getPopulation());
        }
        if (dto.getMetroLines() != null) {
            city.setMetroLines(dto.getMetroLines());
        }
        if (dto.getExtra() != null) {
            city.setExtra(dto.getExtra());
        }
        if (dto.getStatusCode() != null) {
            city.setStatusCode(dto.getStatusCode());
            city.setStatus(CountryStatusEnum.fromCode(dto.getStatusCode()).getDescription());
        }

        city.setUpdatedAt(LocalDateTime.now());
        if (!this.updateById(city)) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED);
        }
        if (nameChanged) {
            cascadeUpdateCityName(id, dto.getCityName());
        }
        redisUtils.deleteCityListCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCity(Long id) {
        City city = this.getById(id);
        if (city == null) {
            throw new BusinessException(ErrorCode.CITY_NOT_FOUND);
        }
        // 检查是否有关联的地铁线路
        long lineCount = metroLineMapper.selectCount(
                new LambdaQueryWrapper<MetroLine>().eq(MetroLine::getCityId, id));
        if (lineCount > 0) {
            throw new BusinessException(ErrorCode.CITY_HAS_METRO_LINES);
        }
        // 检查是否有关联的地铁站点
        long stationCount = metroStationMapper.selectCount(
                new LambdaQueryWrapper<MetroStation>().eq(MetroStation::getCityId, id));
        if (stationCount > 0) {
            throw new BusinessException(ErrorCode.CITY_HAS_METRO_STATIONS);
        }
        this.removeById(id);
        redisUtils.deleteCityListCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteCities(List<Long> ids, Integer operatorRoleCode) {
        if (!canDelete(operatorRoleCode)) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_CITY);
        }
        for (Long id : ids) {
            long lineCount = metroLineMapper.selectCount(
                    new LambdaQueryWrapper<MetroLine>().eq(MetroLine::getCityId, id));
            if (lineCount > 0) {
                throw new BusinessException(ErrorCode.CITY_HAS_METRO_LINES);
            }
            long stationCount = metroStationMapper.selectCount(
                    new LambdaQueryWrapper<MetroStation>().eq(MetroStation::getCityId, id));
            if (stationCount > 0) {
                throw new BusinessException(ErrorCode.CITY_HAS_METRO_STATIONS);
            }
        }
        boolean removed = this.removeBatchByIds(ids);
        if (!removed) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED);
        }
        redisUtils.deleteCityListCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchImportCities(List<CityManageCreateDTO> dtoList, Integer operatorRoleCode) {
        if (!canCreate(operatorRoleCode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<City> cities = new ArrayList<>();
        int successCount = 0;

        for (CityManageCreateDTO dto : dtoList) {
            if (!StringUtils.hasText(dto.getCityName()) || dto.getCountryId() == null) {
                continue;
            }
            if (this.lambdaQuery().eq(City::getCityName, dto.getCityName()).exists()) {
                continue;
            }
            Country country = countryService.getById(dto.getCountryId());
            if (country == null) {
                continue;
            }

            City city = new City();
            city.setCountryId(country.getId());
            city.setCountryName(country.getCountryName());
            city.setCityName(dto.getCityName());
            city.setCityNameEn(dto.getCityNameEn());
            city.setCityAlias(dto.getCityAlias());
            city.setMetroLineLogo(dto.getMetroLineLogo());
            city.setMetroCount(dto.getMetroCount() != null ? dto.getMetroCount() : 0);
            city.setMetroLineCount(dto.getMetroLineCount() != null ? dto.getMetroLineCount() : 0);
            city.setHsrCount(dto.getHsrCount() != null ? dto.getHsrCount() : 0);
            city.setMetroKm(dto.getMetroKm() != null ? dto.getMetroKm() : java.math.BigDecimal.ZERO);
            city.setHsrKm(dto.getHsrKm() != null ? dto.getHsrKm() : java.math.BigDecimal.ZERO);
            city.setPopulation(dto.getPopulation());
            city.setStatusCode(dto.getStatusCode() != null ? dto.getStatusCode() : CountryStatusEnum.PENDING.getCode());
            city.setStatus(CountryStatusEnum.fromCode(city.getStatusCode()).getDescription());
            cities.add(city);
            successCount++;
        }

        if (!cities.isEmpty()) {
            this.saveBatch(cities);
            redisUtils.deleteCityListCache();
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

    private boolean canModifyCityName(int operatorRole) {
        return operatorRole == UserRoleEnum.ROOT_ADMIN.getCode();
    }

    /**
     * 城市名称变更后，级联更新 metro_station / metro_line 表中的 cityName
     */
    private void cascadeUpdateCityName(Long cityId, String newName) {
        metroStationMapper.update(null, Wrappers.<MetroStation>lambdaUpdate()
                .set(MetroStation::getCityName, newName)
                .eq(MetroStation::getCityId, cityId));
        metroLineMapper.update(null, Wrappers.<MetroLine>lambdaUpdate()
                .set(MetroLine::getCityName, newName)
                .eq(MetroLine::getCityId, cityId));
    }
}