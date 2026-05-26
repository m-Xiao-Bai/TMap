package com.mu.transitmap.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mu.transitmap.dto.MetroLineManageCreateDTO;
import com.mu.transitmap.dto.MetroLineManageQueryDTO;
import com.mu.transitmap.dto.MetroLineManageUpdateDTO;
import com.mu.transitmap.entity.MetroLine;

import java.util.List;

public interface IMetroLineService extends IService<MetroLine> {

    Page<MetroLine> getMetroLinePage(MetroLineManageQueryDTO dto);

    void createMetroLine(MetroLineManageCreateDTO dto, Integer operatorRoleCode);

    void updateMetroLine(Long id, MetroLineManageUpdateDTO dto, Integer operatorRoleCode);

    void deleteMetroLine(Long id);

    void batchDeleteMetroLines(List<Long> ids, Integer operatorRoleCode);

    int batchImportMetroLines(List<MetroLineManageCreateDTO> dtoList, Integer operatorRoleCode);
}
