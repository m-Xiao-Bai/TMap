package com.mu.transitmap.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.dto.MetroStationManageCreateDTO;
import com.mu.transitmap.dto.MetroStationManageQueryDTO;
import com.mu.transitmap.dto.MetroStationManageUpdateDTO;
import com.mu.transitmap.entity.City;
import com.mu.transitmap.entity.Country;
import com.mu.transitmap.entity.MetroStation;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.impl.CityServiceImpl;
import com.mu.transitmap.service.impl.CountryServiceImpl;
import com.mu.transitmap.service.impl.MetroStationServiceImpl;
import com.mu.transitmap.util.ExcelUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/manage/metro-station")
public class MetroStationManageController {

    @Autowired
    private MetroStationServiceImpl metroStationService;

    @Autowired
    private CountryServiceImpl countryService;

    @Autowired
    private CityServiceImpl cityService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/list")
    public Result<Page<MetroStation>> getList(@Valid MetroStationManageQueryDTO dto) {
        Page<MetroStation> page = metroStationService.getMetroStationPage(dto);
        return Result.success(page);
    }

    @GetMapping("/by-city/{cityId}")
    public Result<List<MetroStation>> getByCity(@PathVariable Long cityId) {
        List<MetroStation> stations = metroStationService.getStationsByCityId(cityId);
        return Result.success(stations);
    }

    @GetMapping("/{id}")
    public Result<MetroStation> getById(@PathVariable Long id) {
        MetroStation station = metroStationService.getById(id);
        if (station == null) throw new BusinessException(ErrorCode.METRO_STATION_NOT_FOUND);
        return Result.success(station);
    }

    @PostMapping
    public Result<Map<String, String>> create(@RequestBody @Valid MetroStationManageCreateDTO dto,
                                               HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        metroStationService.createMetroStation(dto, operatorRoleCode);
        return Result.success(null);
    }

    @PutMapping("/{id}")
    public Result<Map<String, String>> update(@PathVariable Long id,
                                               @RequestBody @Valid MetroStationManageUpdateDTO dto,
                                               HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        metroStationService.updateMetroStation(id, dto, operatorRoleCode);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Map<String, String>> delete(@PathVariable Long id,
                                               HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (operatorRoleCode != 4) throw new BusinessException(ErrorCode.METRO_STATION_PERMISSION_DENIED);
        metroStationService.deleteMetroStation(id);
        return Result.success(null);
    }

    @DeleteMapping("/batch")
    public Result<Map<String, String>> batchDelete(@RequestBody List<Object> rawIds,
                                                    HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (rawIds == null || rawIds.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR);
        List<Long> ids = rawIds.stream()
                .map(obj -> obj instanceof Number ? ((Number) obj).longValue() : Long.parseLong(obj.toString()))
                .toList();
        metroStationService.batchDeleteMetroStations(ids, operatorRoleCode);
        return Result.success(null);
    }

    @PostMapping("/batch-assign-line")
    public Result<Map<String, String>> batchAssignLine(@RequestBody Map<String, Object> body,
                                                        HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        Long lineId = Long.valueOf(body.get("lineId").toString());
        String lineName = (String) body.get("lineName");
        List<Long> stationIds = toLongList(body.get("stationIds"));
        if (stationIds == null || stationIds.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR);
        metroStationService.batchAssignLine(lineId, lineName, stationIds, operatorRoleCode);
        return Result.success(null);
    }

    @PostMapping("/batch-remove-line")
    public Result<Map<String, String>> batchRemoveLine(@RequestBody Map<String, Object> body,
                                                        HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        Long lineId = Long.valueOf(body.get("lineId").toString());
        List<Long> stationIds = toLongList(body.get("stationIds"));
        if (stationIds == null || stationIds.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR);
        metroStationService.batchRemoveLine(lineId, stationIds, operatorRoleCode);
        return Result.success(null);
    }

    @SuppressWarnings("unchecked")
    private List<Long> toLongList(Object obj) {
        if (obj == null) return List.of();
        if (obj instanceof List<?> list) {
            return list.stream()
                    .map(item -> Long.valueOf(item.toString()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long countryId,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) Integer isTransfer,
            @RequestParam(required = false) Integer statusCode,
            @RequestParam(required = false) Long lineId,
            @RequestParam(required = false) String ids) {

        List<MetroStation> stations;
        if (StringUtils.hasText(ids)) {
            List<Long> idList = Arrays.stream(ids.split(","))
                    .map(String::trim).map(Long::parseLong).collect(Collectors.toList());
            stations = metroStationService.listByIds(idList);
        } else {
            LambdaQueryWrapper<MetroStation> wrapper = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(keyword)) {
                wrapper.and(w -> w
                        .like(MetroStation::getStationName, keyword)
                        .or().like(MetroStation::getStationNameEn, keyword)
                        .or().like(MetroStation::getStationAlias, keyword)
                );
            }
            if (countryId != null) wrapper.eq(MetroStation::getCountryId, countryId);
            if (cityId != null) wrapper.eq(MetroStation::getCityId, cityId);
            if (isTransfer != null) wrapper.eq(MetroStation::getIsTransfer, isTransfer);
            if (statusCode != null) wrapper.eq(MetroStation::getStatusCode, statusCode);
            if (lineId != null) wrapper.like(MetroStation::getLineIds, String.valueOf(lineId));
            wrapper.orderByDesc(MetroStation::getCreatedAt);
            stations = metroStationService.list(wrapper);
        }

        List<MetroStation> finalStations = stations;
        StreamingResponseBody body = outputStream -> {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String[] headers = {"ID", "国家", "城市", "站点名称", "英文名", "别称", "经度", "纬度",
                    "换乘站", "线路ID", "线路名称", "出口数", "有卫生间", "站点类型",
                    "开通日期", "首班车", "末班车", "状态", "状态码", "创建时间"};

            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("地铁站点数据");
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
                for (MetroStation s : finalStations) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(s.getId() != null ? s.getId().doubleValue() : 0);
                    row.createCell(1).setCellValue(s.getCountryName() != null ? s.getCountryName() : "");
                    row.createCell(2).setCellValue(s.getCityName() != null ? s.getCityName() : "");
                    row.createCell(3).setCellValue(s.getStationName() != null ? s.getStationName() : "");
                    row.createCell(4).setCellValue(s.getStationNameEn() != null ? s.getStationNameEn() : "");
                    row.createCell(5).setCellValue(s.getStationAlias() != null ? s.getStationAlias() : "");
                    row.createCell(6).setCellValue(s.getLongitude() != null ? s.getLongitude().toPlainString() : "");
                    row.createCell(7).setCellValue(s.getLatitude() != null ? s.getLatitude().toPlainString() : "");
                    row.createCell(8).setCellValue(s.getIsTransfer() != null ? (s.getIsTransfer() == 1 ? "是" : "否") : "");
                    row.createCell(9).setCellValue(s.getLineIds() != null ? s.getLineIds() : "");
                    row.createCell(10).setCellValue(s.getLineNames() != null ? s.getLineNames() : "");
                    row.createCell(11).setCellValue(s.getExitCount() != null ? s.getExitCount().doubleValue() : 0);
                    row.createCell(12).setCellValue(s.getHasToilet() != null ? (s.getHasToilet() == 1 ? "是" : "否") : "");
                    String[] typeMap = {"地下站", "地面站", "高架站"};
                    String typeText = s.getStationType() != null && s.getStationType() >= 0 && s.getStationType() <= 2
                            ? typeMap[s.getStationType()] : "";
                    row.createCell(13).setCellValue(typeText);
                    row.createCell(14).setCellValue(s.getOpenDate() != null ? s.getOpenDate().toString() : "");
                    row.createCell(15).setCellValue(s.getFirstTime() != null ? s.getFirstTime().toString() : "");
                    row.createCell(16).setCellValue(s.getLastTime() != null ? s.getLastTime().toString() : "");
                    row.createCell(17).setCellValue(s.getStatus() != null ? s.getStatus() : "");
                    row.createCell(18).setCellValue(s.getStatusCode() != null ? s.getStatusCode().doubleValue() : 0);
                    row.createCell(19).setCellValue(s.getCreatedAt() != null ? s.getCreatedAt().format(dtf) : "");
                }

                for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
                workbook.write(outputStream);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header("Content-Disposition", "attachment; filename=metro-stations.xlsx")
                .body(body);
    }

    @PostMapping("/import")
    public Result<Map<String, Object>> batchImport(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        String filename = file.getOriginalFilename();
        if (filename == null) throw new BusinessException(ErrorCode.FILE_FORMAT_ERROR);

        String lowerFilename = filename.toLowerCase();

        try (InputStream is = file.getInputStream()) {
            if (lowerFilename.endsWith(".json")) {
                List<MetroStationManageCreateDTO> dtoList = objectMapper.readValue(
                        is, new TypeReference<List<MetroStationManageCreateDTO>>() {});
                Map<String, Object> importResult = metroStationService.batchImportStationsWithDetails(dtoList, operatorRoleCode);
                int successCount = (Integer) importResult.getOrDefault("successCount", 0);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> jsonErrors = (List<Map<String, Object>>) importResult.get("errors");
                Map<String, Object> response = new HashMap<>();
                response.put("successCount", successCount);
                response.put("totalCount", dtoList.size());
                response.put("errors", jsonErrors != null && !jsonErrors.isEmpty() ? jsonErrors : null);
                return Result.success(response);
            } else if (lowerFilename.endsWith(".xls") || lowerFilename.endsWith(".xlsx")) {
                return handleExcelImport(is, operatorRoleCode);
            } else {
                throw new BusinessException(ErrorCode.FILE_FORMAT_ERROR);
            }
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException(ErrorCode.FILE_FORMAT_ERROR); }
    }

    /**
     * 处理 Excel 导入（列0=国家名称, 列1=城市名称，自动解析为 DB ID）
     * 在同一方法内完成所有校验，确保 Excel 行号准确反馈给前端
     */
    private Result<Map<String, Object>> handleExcelImport(InputStream is, Integer operatorRoleCode) throws Exception {
        List<ExcelImportRow> rawRows = parseExcelRows(is);
        List<Map<String, Object>> errors = new ArrayList<>();
        int successCount = 0;

        for (int i = 0; i < rawRows.size(); i++) {
            ExcelImportRow row = rawRows.get(i);
            int excelRow = i + 2; // 第 0 行为表头
            List<String> reasons = new ArrayList<>();

            // 1. 校验并解析国家（按名称 → ID）
            Long countryId = null;
            String resolvedCountryName = null;
            if (row.countryName != null && !row.countryName.isEmpty()) {
                Country country = countryService.lambdaQuery()
                        .eq(Country::getCountryName, row.countryName)
                        .one();
                if (country == null) {
                    reasons.add("国家「" + row.countryName + "」在数据库中不存在，请先在「国家管理」中添加");
                } else {
                    countryId = country.getId();
                    resolvedCountryName = country.getCountryName();
                }
            } else {
                reasons.add("国家名称为空");
            }

            // 2. 校验并解析城市（按名称 + 所属国家 → ID）
            //    自动适配「东莞市」和「东莞」两种写法
            Long cityId = null;
            String resolvedCityName = null;
            if (row.cityName != null && !row.cityName.isEmpty()) {
                cityId = resolveCityByName(row.cityName, countryId);
                if (cityId == null && row.cityName.endsWith("市")) {
                    // 去掉"市"再试一次
                    String stripped = row.cityName.substring(0, row.cityName.length() - 1);
                    cityId = resolveCityByName(stripped, countryId);
                } else if (cityId == null && !row.cityName.endsWith("市")) {
                    // 加上"市"再试一次
                    cityId = resolveCityByName(row.cityName + "市", countryId);
                }
                if (cityId == null) {
                    String hint = countryId != null
                            ? "城市「" + row.cityName + "」在数据库中不存在或不属于「" + row.countryName + "」，请先在「城市管理」中添加"
                            : "城市「" + row.cityName + "」在数据库中不存在，请先在「城市管理」中添加";
                    reasons.add(hint);
                } else {
                    resolvedCityName = row.cityName;
                }
            } else {
                reasons.add("城市名称为空");
            }

            // 3. 校验站名是否已存在（同一城市下站名唯一）
            if (cityId != null && row.stationName != null && !row.stationName.isEmpty()) {
                boolean nameExists = metroStationService.lambdaQuery()
                        .eq(MetroStation::getCityId, cityId)
                        .eq(MetroStation::getStationName, row.stationName)
                        .exists();
                if (nameExists) {
                    reasons.add("站名「" + row.stationName + "」在「" + resolvedCityName + "」下已存在");
                }
            }

            // 4. 校验坐标冲突：同坐标+同站名→重复；同坐标+异站名→不同站点，允许导入
            if (cityId != null && row.longitude != null && row.latitude != null && reasons.isEmpty()) {
                MetroStation existingAtCoords = metroStationService.lambdaQuery()
                        .eq(MetroStation::getLongitude, row.longitude)
                        .eq(MetroStation::getLatitude, row.latitude)
                        .last("LIMIT 1")
                        .one();
                if (existingAtCoords != null
                        && existingAtCoords.getStationName().equals(row.stationName)) {
                    reasons.add("经纬度(" + row.longitude + ", " + row.latitude
                            + ") 与已有站点「" + existingAtCoords.getStationName() + "」完全一致，视为同一站点");
                }
            }

            // 5. 有校验错误 → 记录并跳过
            if (!reasons.isEmpty()) {
                Map<String, Object> err = new HashMap<>();
                err.put("row", excelRow);
                err.put("stationName", row.stationName != null ? row.stationName : "");
                err.put("reasons", reasons);
                errors.add(err);
                continue;
            }

            // 6. 构造 DTO 并执行导入
            try {
                MetroStationManageCreateDTO dto = new MetroStationManageCreateDTO();
                dto.setCountryId(countryId);
                dto.setCityId(cityId);
                dto.setStationName(row.stationName);
                dto.setStationNameEn(row.stationNameEn);
                dto.setStationAlias(row.stationAlias);
                dto.setLongitude(row.longitude);
                dto.setLatitude(row.latitude);
                dto.setIsTransfer(row.isTransfer);
                dto.setLineIds(row.lineIds);
                dto.setLineNames(row.lineNames);
                dto.setExitCount(row.exitCount);
                dto.setHasToilet(row.hasToilet);
                dto.setStationType(row.stationType);
                dto.setOpenDate(row.openDate);
                dto.setFirstTime(row.firstTime);
                dto.setLastTime(row.lastTime);
                dto.setStatusCode(row.statusCode);
                dto.setExtra(row.extra);
                metroStationService.createMetroStation(dto, operatorRoleCode);
                successCount++;
            } catch (Exception e) {
                Map<String, Object> err = new HashMap<>();
                err.put("row", excelRow);
                err.put("stationName", row.stationName != null ? row.stationName : "");
                err.put("reasons", List.of(e.getMessage() != null ? e.getMessage() : "导入失败"));
                errors.add(err);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("totalCount", rawRows.size());
        result.put("errors", errors);
        return Result.success(result);
    }

    /**
     * 按城市名称查找城市 ID
     */
    private Long resolveCityByName(String cityName, Long countryId) {
        LambdaQueryWrapper<City> query = new LambdaQueryWrapper<City>()
                .eq(City::getCityName, cityName);
        if (countryId != null) {
            query.eq(City::getCountryId, countryId);
        }
        City city = cityService.getOne(query);
        return city != null ? city.getId() : null;
    }

    /**
     * 解析 Excel，列0=国家名称(字符串), 列1=城市名称(字符串)
     */
    private List<ExcelImportRow> parseExcelRows(InputStream is) throws Exception {
        List<ExcelImportRow> list = new ArrayList<>();
        Workbook workbook = new XSSFWorkbook(is);
        Sheet sheet = workbook.getSheetAt(0);

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            ExcelImportRow r = new ExcelImportRow();
            r.countryName = ExcelUtils.getCellString(row, 0);
            r.cityName = ExcelUtils.getCellString(row, 1);
            r.stationName = ExcelUtils.getCellString(row, 2);
            r.stationNameEn = ExcelUtils.getCellString(row, 3);
            r.stationAlias = ExcelUtils.getCellString(row, 4);
            r.longitude = ExcelUtils.getCellBigDecimal(row, 5);
            r.latitude = ExcelUtils.getCellBigDecimal(row, 6);
            r.isTransfer = ExcelUtils.getCellInt(row, 7);
            r.lineIds = ExcelUtils.getCellString(row, 8);
            r.lineNames = ExcelUtils.getCellString(row, 9);
            r.exitCount = ExcelUtils.getCellInt(row, 10);
            r.hasToilet = ExcelUtils.getCellInt(row, 11);
            r.stationType = ExcelUtils.getCellInt(row, 12);
            r.openDate = ExcelUtils.getCellString(row, 13);
            r.firstTime = ExcelUtils.getCellString(row, 14);
            r.lastTime = ExcelUtils.getCellString(row, 15);
            r.statusCode = ExcelUtils.getCellInt(row, 16);
            r.extra = ExcelUtils.getCellString(row, 17);

            if (r.stationName != null && !r.stationName.isEmpty()) {
                list.add(r);
            }
        }

        workbook.close();
        return list;
    }

    /** Excel 导入行的中间表示（列0/1为国家/城市名称） */
    private static class ExcelImportRow {
        String countryName;
        String cityName;
        String stationName;
        String stationNameEn;
        String stationAlias;
        java.math.BigDecimal longitude;
        java.math.BigDecimal latitude;
        Integer isTransfer;
        String lineIds;
        String lineNames;
        Integer exitCount;
        Integer hasToilet;
        Integer stationType;
        String openDate;
        String firstTime;
        String lastTime;
        Integer statusCode;
        String extra;
    }
}
