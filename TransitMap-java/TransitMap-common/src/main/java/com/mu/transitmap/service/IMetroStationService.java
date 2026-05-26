package com.mu.transitmap.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mu.transitmap.dto.MetroStationManageCreateDTO;
import com.mu.transitmap.dto.MetroStationManageQueryDTO;
import com.mu.transitmap.dto.MetroStationManageUpdateDTO;
import com.mu.transitmap.entity.MetroStation;

import java.util.List;
import java.util.Map;

public interface IMetroStationService extends IService<MetroStation> {

    Page<MetroStation> getMetroStationPage(MetroStationManageQueryDTO dto);

    void createMetroStation(MetroStationManageCreateDTO dto, Integer operatorRoleCode);

    void updateMetroStation(Long id, MetroStationManageUpdateDTO dto, Integer operatorRoleCode);

    void deleteMetroStation(Long id);

    void batchDeleteMetroStations(List<Long> ids, Integer operatorRoleCode);

    int batchImportStations(List<MetroStationManageCreateDTO> dtoList, Integer operatorRoleCode);

    /**
     * 增强的批量导入：校验站名重复、坐标冲突，收集详细错误信息
     */
    Map<String, Object> batchImportStationsWithDetails(List<MetroStationManageCreateDTO> dtoList, Integer operatorRoleCode);

    /**
     * 获取指定城市所有运营中的站点
     */
    List<MetroStation> getStationsByCityId(Long cityId);

    /**
     * 获取指定线路的有序站点列表（含分支信息）
     */
    Map<String, Object> getOrderedStationsByLineId(Long lineId);

    /**
     * 批量将站点分配到指定线路
     */
    void batchAssignLine(Long lineId, String lineName, List<Long> stationIds, Integer operatorRoleCode);

    /**
     * 批量将站点从指定线路移除
     */
    void batchRemoveLine(Long lineId, List<Long> stationIds, Integer operatorRoleCode);
}
