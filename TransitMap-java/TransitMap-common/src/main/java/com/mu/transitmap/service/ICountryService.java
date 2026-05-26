package com.mu.transitmap.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mu.transitmap.dto.CountryManageCreateDTO;
import com.mu.transitmap.dto.CountryManageQueryDTO;
import com.mu.transitmap.dto.CountryManageUpdateDTO;
import com.mu.transitmap.entity.Country;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mu.transitmap.vo.CountrySelectIdNameVO;

import java.util.List;

/**
 * <p>
 * 国家信息表 服务类
 * </p>
 *
 * @author muxiaobai
 * @since 2026-01-22
 */
public interface ICountryService extends IService<Country> {
    List<CountrySelectIdNameVO> allListId();

    Page<Country> getCountryPage(CountryManageQueryDTO dto);

    void createCountry(CountryManageCreateDTO dto, Integer operatorRoleCode);

    void updateCountry(Long id, CountryManageUpdateDTO dto, Integer operatorRoleCode);

    void deleteCountry(Long id);

    void batchDeleteCountries(List<Long> ids, Integer operatorRoleCode);

    int batchImportCountries(List<CountryManageCreateDTO> dtoList, Integer operatorRoleCode);
}
