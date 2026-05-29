package com.mu.transitmap.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.dto.CityManageCreateDTO;
import com.mu.transitmap.dto.CityManageQueryDTO;
import com.mu.transitmap.dto.CityManageUpdateDTO;
import com.mu.transitmap.entity.City;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.impl.CityServiceImpl;
import com.mu.transitmap.service.impl.SystemConfigServiceImpl;
import com.mu.transitmap.util.ExcelUtils;
import com.mu.transitmap.utils.RedisUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/manage/city")
public class CityManageController {

    @Autowired
    private CityServiceImpl cityService;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private SystemConfigServiceImpl systemConfigService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/list")
    public Result<Page<City>> getList(@Valid CityManageQueryDTO dto) {
        Page<City> page = cityService.getCityPage(dto);
        return Result.success(page);
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/all")
    public Result<List<Map<String, Object>>> getAll() {
        // 尝试从Redis缓存获取
        Object cached = redisUtils.getCityListCache();
        if (cached instanceof List) {
            log.debug("城市列表命中Redis缓存");
            return Result.success((List<Map<String, Object>>) cached);
        }

        List<City> cities = cityService.lambdaQuery()
                .orderByAsc(City::getCityName)
                .list();
        List<Map<String, Object>> list = cities.stream()
                .map(c -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", c.getId());
                    m.put("cityName", c.getCityName());
                    m.put("countryId", c.getCountryId());
                    return m;
                })
                .toList();

        // 写入Redis缓存
        int ttl = systemConfigService.getConfigInt("cache.ttl.city", 86400);
        redisUtils.setCityListCache(list, ttl);
        log.debug("城市列表写入Redis缓存, TTL={}s", ttl);

        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<City> getById(@PathVariable Long id) {
        City city = cityService.getById(id);
        if (city == null) {
            throw new BusinessException(ErrorCode.CITY_NOT_FOUND);
        }
        return Result.success(city);
    }

    @PostMapping
    public Result<Map<String, String>> create(@RequestBody @Valid CityManageCreateDTO dto,
                                               HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        cityService.createCity(dto, operatorRoleCode);
        return Result.success(null);
    }

    @PutMapping("/{id}")
    public Result<Map<String, String>> update(@PathVariable Long id,
                                               @RequestBody @Valid CityManageUpdateDTO dto,
                                               HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        cityService.updateCity(id, dto, operatorRoleCode);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Map<String, String>> delete(@PathVariable Long id,
                                               HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (operatorRoleCode != 4) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_COUNTRY);
        }
        cityService.deleteCity(id);
        return Result.success(null);
    }

    @DeleteMapping("/batch")
    public Result<Map<String, String>> batchDelete(@RequestBody Map<String, List<Object>> body,
                                                    HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        List<Object> rawIds = body.get("ids");
        if (rawIds == null || rawIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Long> ids = rawIds.stream()
                .map(obj -> obj instanceof Number ? ((Number) obj).longValue() : Long.parseLong(obj.toString()))
                .toList();
        cityService.batchDeleteCities(ids, operatorRoleCode);
        return Result.success(null);
    }

    @PostMapping("/import")
    public Result<Map<String, Object>> batchImport(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new BusinessException(ErrorCode.FILE_FORMAT_ERROR);
        }

        List<CityManageCreateDTO> dtoList;
        String lowerFilename = filename.toLowerCase();

        try (InputStream is = file.getInputStream()) {
            if (lowerFilename.endsWith(".json")) {
                dtoList = objectMapper.readValue(is,
                        new TypeReference<List<CityManageCreateDTO>>() {});
            } else if (lowerFilename.endsWith(".xls") || lowerFilename.endsWith(".xlsx")) {
                dtoList = parseExcel(is);
            } else {
                throw new BusinessException(ErrorCode.FILE_FORMAT_ERROR);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.FILE_FORMAT_ERROR);
        }

        int successCount = cityService.batchImportCities(dtoList, operatorRoleCode);
        return Result.success(Map.of("successCount", successCount, "totalCount", dtoList.size()));
    }

    private List<CityManageCreateDTO> parseExcel(InputStream is) throws Exception {
        List<CityManageCreateDTO> list = new ArrayList<>();
        Workbook workbook = new XSSFWorkbook(is);
        Sheet sheet = workbook.getSheetAt(0);

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            CityManageCreateDTO dto = new CityManageCreateDTO();
            dto.setCountryId(ExcelUtils.getCellLong(row, 0));
            dto.setCityName(ExcelUtils.getCellString(row, 1));
            dto.setCityNameEn(ExcelUtils.getCellString(row, 2));
            dto.setCityAlias(ExcelUtils.getCellString(row, 3));
            dto.setMetroLineLogo(ExcelUtils.getCellString(row, 4));
            dto.setMetroCount(ExcelUtils.getCellInt(row, 5));
            dto.setMetroLineCount(ExcelUtils.getCellInt(row, 6));
            dto.setHsrCount(ExcelUtils.getCellInt(row, 7));
            dto.setMetroKm(ExcelUtils.getCellBigDecimal(row, 8));
            dto.setHsrKm(ExcelUtils.getCellBigDecimal(row, 9));
            dto.setPopulation(ExcelUtils.getCellLong(row, 10));
            dto.setMetroLines(ExcelUtils.getCellString(row, 11));
            dto.setExtra(ExcelUtils.getCellString(row, 12));
            dto.setStatusCode(ExcelUtils.getCellInt(row, 13));

            if (dto.getCityName() != null && !dto.getCityName().isEmpty()) {
                list.add(dto);
            }
        }

        workbook.close();
        return list;
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long countryId,
            @RequestParam(required = false) Integer statusCode,
            @RequestParam(required = false) Integer onlineStatusCode,
            @RequestParam(required = false) String ids) {

        List<City> cities;
        if (StringUtils.hasText(ids)) {
            List<Long> idList = Arrays.stream(ids.split(","))
                    .map(Long::parseLong).collect(Collectors.toList());
            cities = cityService.listByIds(idList);
        } else {
            LambdaQueryWrapper<City> wrapper = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(keyword)) {
                wrapper.and(w -> w
                        .like(City::getCityName, keyword)
                        .or().like(City::getCityNameEn, keyword)
                        .or().like(City::getCountryName, keyword)
                );
            }
            if (countryId != null) wrapper.eq(City::getCountryId, countryId);
            if (statusCode != null) wrapper.eq(City::getStatusCode, statusCode);
            if (onlineStatusCode != null) wrapper.eq(City::getStatusCode, onlineStatusCode);
            wrapper.orderByDesc(City::getCreatedAt);
            cities = cityService.list(wrapper);
        }

        List<City> finalCities = cities;
        StreamingResponseBody body = outputStream -> {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String[] headers = {"ID", "所属国家", "城市名称", "英文名称", "别称", "地铁系统", "地铁线路", "高铁数", "地铁里程(km)", "高铁里程(km)", "人口", "状态", "状态码", "创建时间"};

            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("城市数据");
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                CellStyle headerStyle = workbook.createCellStyle();
                headerStyle.setFont(headerFont);

                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                int rowNum = 1;
                for (City c : finalCities) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(c.getId() != null ? c.getId().doubleValue() : 0);
                    row.createCell(1).setCellValue(c.getCountryName() != null ? c.getCountryName() : "");
                    row.createCell(2).setCellValue(c.getCityName() != null ? c.getCityName() : "");
                    row.createCell(3).setCellValue(c.getCityNameEn() != null ? c.getCityNameEn() : "");
                    row.createCell(4).setCellValue(c.getCityAlias() != null ? c.getCityAlias() : "");
                    row.createCell(5).setCellValue(c.getMetroCount() != null ? c.getMetroCount().doubleValue() : 0);
                    row.createCell(6).setCellValue(c.getMetroLineCount() != null ? c.getMetroLineCount().doubleValue() : 0);
                    row.createCell(7).setCellValue(c.getHsrCount() != null ? c.getHsrCount().doubleValue() : 0);
                    row.createCell(8).setCellValue(c.getMetroKm() != null ? c.getMetroKm().doubleValue() : 0);
                    row.createCell(9).setCellValue(c.getHsrKm() != null ? c.getHsrKm().doubleValue() : 0);
                    row.createCell(10).setCellValue(c.getPopulation() != null ? c.getPopulation().doubleValue() : 0);
                    row.createCell(11).setCellValue(c.getStatus() != null ? c.getStatus() : "");
                    row.createCell(12).setCellValue(c.getStatusCode() != null ? c.getStatusCode().doubleValue() : 0);
                    row.createCell(13).setCellValue(c.getCreatedAt() != null ? c.getCreatedAt().format(dtf) : "");
                }

                for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
                workbook.write(outputStream);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header("Content-Disposition", "attachment; filename=cities.xlsx")
                .body(body);
    }
}
