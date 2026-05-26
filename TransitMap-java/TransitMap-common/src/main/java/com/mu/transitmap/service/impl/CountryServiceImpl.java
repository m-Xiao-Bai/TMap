package com.mu.transitmap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mu.transitmap.constants.RedisKey;
import com.mu.transitmap.dto.CountryManageCreateDTO;
import com.mu.transitmap.dto.CountryManageQueryDTO;
import com.mu.transitmap.dto.CountryManageUpdateDTO;
import com.mu.transitmap.entity.City;
import com.mu.transitmap.entity.Country;
import com.mu.transitmap.entity.MetroLine;
import com.mu.transitmap.entity.MetroStation;
import com.mu.transitmap.enums.CountryStatusEnum;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.enums.UserRoleEnum;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.mapper.CityMapper;
import com.mu.transitmap.mapper.CountryMapper;
import com.mu.transitmap.mapper.MetroLineMapper;
import com.mu.transitmap.mapper.MetroStationMapper;
import com.mu.transitmap.utils.RedisUtils;
import com.mu.transitmap.vo.CountrySelectIdNameVO;
import com.mu.transitmap.service.ICountryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 国家信息表 服务实现类
 * </p>
 *
 * @author muxiaobai
 * @since 2026-01-22
 */
@Service
public class CountryServiceImpl extends ServiceImpl<CountryMapper, Country> implements ICountryService {
    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private CityMapper cityMapper;

    @Autowired
    private MetroStationMapper metroStationMapper;

    @Autowired
    private MetroLineMapper metroLineMapper;

    @Override
    public List<CountrySelectIdNameVO> allListId() {
        List<CountrySelectIdNameVO> idNameVOList = redisUtils.getCountryIdNameList();
        if (idNameVOList == null || idNameVOList.isEmpty()) {
            List<Country> list = this.list(
                    Wrappers.<Country>lambdaQuery()
                            .orderByAsc(Country::getCountryName)
            );
            idNameVOList = list.stream()
                    .map(c -> new CountrySelectIdNameVO(c.getId(), c.getCountryName()))
                    .toList();
            redisUtils.redisCountryIdNameList(idNameVOList);
        }
        return idNameVOList;
    }

    @Override
    public Page<Country> getCountryPage(CountryManageQueryDTO dto) {
        LambdaQueryWrapper<Country> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dto.getKeyword())) {
            wrapper.and(w -> w
                    .like(Country::getCountryName, dto.getKeyword())
                    .or()
                    .like(Country::getCountryNameEn, dto.getKeyword())
                    .or()
                    .like(Country::getCountryAlias, dto.getKeyword())
            );
        }
        if (dto.getOnlineStatusCode() != null) {
            wrapper.eq(Country::getStatusCode, dto.getOnlineStatusCode());
        } else if (dto.getStatusCode() != null) {
            wrapper.eq(Country::getStatusCode, dto.getStatusCode());
        }
        if (dto.getMinCityCount() != null) {
            wrapper.ge(Country::getCityCount, dto.getMinCityCount());
        }
        if (dto.getMaxCityCount() != null) {
            wrapper.le(Country::getCityCount, dto.getMaxCityCount());
        }
        if (dto.getMinMetroLineCount() != null) {
            wrapper.ge(Country::getMetroLineCount, dto.getMinMetroLineCount());
        }
        if (dto.getMaxMetroLineCount() != null) {
            wrapper.le(Country::getMetroLineCount, dto.getMaxMetroLineCount());
        }
        if (dto.getMinMetroStationCount() != null) {
            wrapper.ge(Country::getMetroStationCount, dto.getMinMetroStationCount());
        }
        if (dto.getMaxMetroStationCount() != null) {
            wrapper.le(Country::getMetroStationCount, dto.getMaxMetroStationCount());
        }
        if (dto.getMinMetroKm() != null) {
            wrapper.ge(Country::getMetroKm, dto.getMinMetroKm());
        }
        if (dto.getMaxMetroKm() != null) {
            wrapper.le(Country::getMetroKm, dto.getMaxMetroKm());
        }
        if (dto.getMinHsrStationCount() != null) {
            wrapper.ge(Country::getHsrStationCount, dto.getMinHsrStationCount());
        }
        if (dto.getMaxHsrStationCount() != null) {
            wrapper.le(Country::getHsrStationCount, dto.getMaxHsrStationCount());
        }
        if (dto.getMinHsrKm() != null) {
            wrapper.ge(Country::getHsrKm, dto.getMinHsrKm());
        }
        if (dto.getMaxHsrKm() != null) {
            wrapper.le(Country::getHsrKm, dto.getMaxHsrKm());
        }

        if (StringUtils.hasText(dto.getSortField())) {
            boolean asc = "asc".equalsIgnoreCase(dto.getSortOrder());
            switch (dto.getSortField()) {
                case "createdAt":
                    wrapper.orderBy(true, asc, Country::getCreatedAt);
                    break;
                case "updatedAt":
                    wrapper.orderBy(true, asc, Country::getUpdatedAt);
                    break;
                case "countryName":
                    wrapper.orderBy(true, asc, Country::getCountryName);
                    break;
                case "cityCount":
                    wrapper.orderBy(true, asc, Country::getCityCount);
                    break;
                case "metroKm":
                    wrapper.orderBy(true, asc, Country::getMetroKm);
                    break;
                case "hsrKm":
                    wrapper.orderBy(true, asc, Country::getHsrKm);
                    break;
                default:
                    wrapper.orderByDesc(Country::getCreatedAt);
                    break;
            }
        } else {
            wrapper.orderByDesc(Country::getCreatedAt);
        }
        return this.page(new Page<>(dto.getPageNum(), dto.getPageSize()), wrapper);
    }

    @Override
    public void createCountry(CountryManageCreateDTO dto, Integer operatorRoleCode) {
        if (!canCreate(operatorRoleCode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (this.lambdaQuery().eq(Country::getCountryName, dto.getCountryName()).exists()) {
            throw new BusinessException(ErrorCode.COUNTRY_NAME_ALREADY_EXISTS);
        }

        Country country = new Country();
        country.setCountryName(dto.getCountryName());
        country.setCountryNameEn(dto.getCountryNameEn());
        country.setCountryAlias(dto.getCountryAlias());
        country.setCityCount(dto.getCityCount() != null ? dto.getCityCount() : 0);
        country.setMetroLineCount(dto.getMetroLineCount() != null ? dto.getMetroLineCount() : 0);
        country.setMetroStationCount(dto.getMetroStationCount() != null ? dto.getMetroStationCount() : 0);
        country.setMetroKm(dto.getMetroKm() != null ? dto.getMetroKm() : java.math.BigDecimal.ZERO);
        country.setHsrStationCount(dto.getHsrStationCount() != null ? dto.getHsrStationCount() : 0);
        country.setHsrKm(dto.getHsrKm() != null ? dto.getHsrKm() : java.math.BigDecimal.ZERO);
        country.setStatusCode(dto.getStatusCode() != null ? dto.getStatusCode() : CountryStatusEnum.PENDING.getCode());
        country.setStatus(CountryStatusEnum.fromCode(country.getStatusCode()).getDescription());

        if (!this.save(country)) {
            throw new BusinessException(ErrorCode.COUNTRY_IMPORT_FAILED);
        }
        redisUtils.deleCountryIdNameList();
        redisUtils.deleteCountryListCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCountry(Long id, CountryManageUpdateDTO dto, Integer operatorRoleCode) {
        Country country = this.getById(id);
        if (country == null) {
            throw new BusinessException(ErrorCode.COUNTRY_NOT_FOUND);
        }

        // 记录旧的名称，用于后面级联更新
        String oldCountryName = country.getCountryName();
        boolean nameChanged = dto.getCountryName() != null && !dto.getCountryName().equals(oldCountryName);

        if (nameChanged) {
            if (!canModifyCountryName(operatorRoleCode)) {
                throw new BusinessException(ErrorCode.CANNOT_MODIFY_COUNTRY_NAME);
            }
            if (this.lambdaQuery().eq(Country::getCountryName, dto.getCountryName()).exists()) {
                throw new BusinessException(ErrorCode.COUNTRY_NAME_ALREADY_EXISTS);
            }
            country.setCountryName(dto.getCountryName());
        }

        if (!canModifyAllFields(operatorRoleCode)) {
            if (dto.getStatusCode() != null) {
                country.setStatusCode(dto.getStatusCode());
                country.setStatus(CountryStatusEnum.fromCode(dto.getStatusCode()).getDescription());
            }
            country.setUpdatedAt(LocalDateTime.now());
            if (!this.updateById(country)) {
                throw new BusinessException(ErrorCode.OPERATION_FAILED);
            }
            // 级联更新关联表的国家名称
            if (nameChanged) {
                cascadeUpdateCountryName(id, dto.getCountryName());
            }
            // 清除 Redis 中国家 ID-名称缓存，确保下次 allListId() 获取最新数据
            redisUtils.deleCountryIdNameList();
            redisUtils.deleteCountryListCache();
            return;
        }

        if (dto.getCountryNameEn() != null) {
            country.setCountryNameEn(dto.getCountryNameEn());
        }
        if (dto.getCountryAlias() != null) {
            country.setCountryAlias(dto.getCountryAlias());
        }
        if (dto.getCityCount() != null) {
            country.setCityCount(dto.getCityCount());
        }
        if (dto.getMetroLineCount() != null) {
            country.setMetroLineCount(dto.getMetroLineCount());
        }
        if (dto.getMetroStationCount() != null) {
            country.setMetroStationCount(dto.getMetroStationCount());
        }
        if (dto.getMetroKm() != null) {
            country.setMetroKm(dto.getMetroKm());
        }
        if (dto.getHsrStationCount() != null) {
            country.setHsrStationCount(dto.getHsrStationCount());
        }
        if (dto.getHsrKm() != null) {
            country.setHsrKm(dto.getHsrKm());
        }
        if (dto.getStatusCode() != null) {
            country.setStatusCode(dto.getStatusCode());
            country.setStatus(CountryStatusEnum.fromCode(dto.getStatusCode()).getDescription());
        }

        country.setUpdatedAt(LocalDateTime.now());
        if (!this.updateById(country)) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED);
        }
        // 级联更新关联表的国家名称
        if (nameChanged) {
            cascadeUpdateCountryName(id, dto.getCountryName());
        }
        // 清除 Redis 中国家 ID-名称缓存，确保下次 allListId() 获取最新数据
        redisUtils.deleCountryIdNameList();
        redisUtils.deleteCountryListCache();
    }

    /**
     * 国家名称变更后，级联更新 city / metro_station / metro_line 表中的 countryName
     */
    private void cascadeUpdateCountryName(Long countryId, String newName) {
        cityMapper.update(null, Wrappers.<City>lambdaUpdate()
                .set(City::getCountryName, newName)
                .eq(City::getCountryId, countryId));
        metroStationMapper.update(null, Wrappers.<MetroStation>lambdaUpdate()
                .set(MetroStation::getCountryName, newName)
                .eq(MetroStation::getCountryId, countryId));
        metroLineMapper.update(null, Wrappers.<MetroLine>lambdaUpdate()
                .set(MetroLine::getCountryName, newName)
                .eq(MetroLine::getCountryId, countryId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCountry(Long id) {
        Country country = this.getById(id);
        if (country == null) {
            throw new BusinessException(ErrorCode.COUNTRY_NOT_FOUND);
        }
        // 检查是否有关联的城市
        long cityCount = cityMapper.selectCount(
                new LambdaQueryWrapper<City>().eq(City::getCountryId, id));
        if (cityCount > 0) {
            throw new BusinessException(ErrorCode.COUNTRY_HAS_CITIES);
        }
        this.removeById(id);
        redisUtils.deleCountryIdNameList();
        redisUtils.deleteCountryListCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteCountries(List<Long> ids, Integer operatorRoleCode) {
        if (!canDelete(operatorRoleCode)) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_COUNTRY);
        }
        for (Long id : ids) {
            long cityCount = cityMapper.selectCount(
                    new LambdaQueryWrapper<City>().eq(City::getCountryId, id));
            if (cityCount > 0) {
                Country country = this.getById(id);
                String name = country != null ? country.getCountryName() : String.valueOf(id);
                throw new BusinessException(ErrorCode.COUNTRY_HAS_CITIES);
            }
        }
        boolean removed = this.removeBatchByIds(ids);
        if (!removed) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED);
        }
        redisUtils.deleCountryIdNameList();
        redisUtils.deleteCountryListCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchImportCountries(List<CountryManageCreateDTO> dtoList, Integer operatorRoleCode) {
        if (!canCreate(operatorRoleCode)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        List<Country> countries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int successCount = 0;

        for (CountryManageCreateDTO dto : dtoList) {
            if (!StringUtils.hasText(dto.getCountryName())) {
                continue;
            }
            if (this.lambdaQuery().eq(Country::getCountryName, dto.getCountryName()).exists()) {
                continue;
            }
            Country country = new Country();
            country.setCountryName(dto.getCountryName());
            country.setCountryNameEn(dto.getCountryNameEn());
            country.setCountryAlias(dto.getCountryAlias());
            country.setCityCount(dto.getCityCount() != null ? dto.getCityCount() : 0);
            country.setMetroLineCount(dto.getMetroLineCount() != null ? dto.getMetroLineCount() : 0);
            country.setMetroStationCount(dto.getMetroStationCount() != null ? dto.getMetroStationCount() : 0);
            country.setMetroKm(dto.getMetroKm() != null ? dto.getMetroKm() : java.math.BigDecimal.ZERO);
            country.setHsrStationCount(dto.getHsrStationCount() != null ? dto.getHsrStationCount() : 0);
            country.setHsrKm(dto.getHsrKm() != null ? dto.getHsrKm() : java.math.BigDecimal.ZERO);
            country.setStatusCode(dto.getStatusCode() != null ? dto.getStatusCode() : CountryStatusEnum.PENDING.getCode());
            country.setStatus(CountryStatusEnum.fromCode(country.getStatusCode()).getDescription());
            countries.add(country);
            successCount++;
        }

        if (!countries.isEmpty()) {
            this.saveBatch(countries);
            redisUtils.deleCountryIdNameList();
            redisUtils.deleteCountryListCache();
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

    private boolean canModifyCountryName(int operatorRole) {
        return operatorRole == UserRoleEnum.ROOT_ADMIN.getCode();
    }
}
