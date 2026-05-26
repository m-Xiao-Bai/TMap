package com.mu.transitmap.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mu.transitmap.dto.CityManageCreateDTO;
import com.mu.transitmap.dto.CityManageQueryDTO;
import com.mu.transitmap.dto.CityManageUpdateDTO;
import com.mu.transitmap.entity.City;

import java.util.List;

public interface ICityService extends IService<City> {

    Page<City> getCityPage(CityManageQueryDTO dto);

    void createCity(CityManageCreateDTO dto, Integer operatorRoleCode);

    void updateCity(Long id, CityManageUpdateDTO dto, Integer operatorRoleCode);

    void deleteCity(Long id);

    void batchDeleteCities(List<Long> ids, Integer operatorRoleCode);

    int batchImportCities(List<CityManageCreateDTO> dtoList, Integer operatorRoleCode);
}
