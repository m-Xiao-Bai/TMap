package com.mu.transitmap.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.dto.MetroLineManageCreateDTO;
import com.mu.transitmap.dto.MetroLineManageQueryDTO;
import com.mu.transitmap.dto.MetroLineManageUpdateDTO;
import com.mu.transitmap.entity.MetroLine;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.impl.MetroLineServiceImpl;
import com.mu.transitmap.service.impl.MetroStationServiceImpl;
import com.mu.transitmap.util.ExcelUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/manage/metro-line")
public class MetroLineManageController {

    @Autowired
    private MetroLineServiceImpl metroLineService;

    @Autowired
    private MetroStationServiceImpl metroStationService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/list")
    public Result<Page<MetroLine>> getList(@Valid MetroLineManageQueryDTO dto) {
        Page<MetroLine> page = metroLineService.getMetroLinePage(dto);
        return Result.success(page);
    }

    @GetMapping("/{id}")
    public Result<MetroLine> getById(@PathVariable Long id) {
        MetroLine line = metroLineService.getById(id);
        if (line == null) {
            throw new BusinessException(ErrorCode.METRO_LINE_NOT_FOUND);
        }
        return Result.success(line);
    }

    @GetMapping("/{lineId}/stations-ordered")
    public Result<Map<String, Object>> getLineOrderedStations(@PathVariable Long lineId) {
        MetroLine line = metroLineService.getById(lineId);
        if (line == null) {
            throw new BusinessException(ErrorCode.METRO_LINE_NOT_FOUND);
        }
        Map<String, Object> data = metroStationService.getOrderedStationsByLineId(lineId);
        data.put("lineId", lineId);
        data.put("lineName", line.getLineName());
        data.put("lineColor", line.getLineColor());
        return Result.success(data);
    }

    @PostMapping
    public Result<Map<String, String>> create(@RequestBody @Valid MetroLineManageCreateDTO dto,
                                               HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        metroLineService.createMetroLine(dto, operatorRoleCode);
        return Result.success(null);
    }

    @PutMapping("/{id}")
    public Result<Map<String, String>> update(@PathVariable Long id,
                                               @RequestBody @Valid MetroLineManageUpdateDTO dto,
                                               HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        metroLineService.updateMetroLine(id, dto, operatorRoleCode);
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
            throw new BusinessException(ErrorCode.METRO_LINE_PERMISSION_DENIED);
        }
        metroLineService.deleteMetroLine(id);
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
        // 兼容 Python 生成的大 ID（String → Long）
        List<Long> ids = rawIds.stream()
                .map(obj -> obj instanceof Number ? ((Number) obj).longValue() : Long.parseLong(obj.toString()))
                .toList();
        metroLineService.batchDeleteMetroLines(ids, operatorRoleCode);
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

        List<MetroLineManageCreateDTO> dtoList;
        String lowerFilename = filename.toLowerCase();

        try (InputStream is = file.getInputStream()) {
            if (lowerFilename.endsWith(".json")) {
                dtoList = objectMapper.readValue(is,
                        new TypeReference<List<MetroLineManageCreateDTO>>() {});
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

        int successCount = metroLineService.batchImportMetroLines(dtoList, operatorRoleCode);
        return Result.success(Map.of("successCount", successCount, "totalCount", dtoList.size()));
    }

    private List<MetroLineManageCreateDTO> parseExcel(InputStream is) throws Exception {
        List<MetroLineManageCreateDTO> list = new ArrayList<>();
        Workbook workbook = new XSSFWorkbook(is);
        Sheet sheet = workbook.getSheetAt(0);

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            MetroLineManageCreateDTO dto = new MetroLineManageCreateDTO();
            dto.setCountryId(ExcelUtils.getCellLong(row, 0));
            dto.setCityId(ExcelUtils.getCellLong(row, 1));
            dto.setLineName(ExcelUtils.getCellString(row, 2));
            dto.setLineNo(ExcelUtils.getCellString(row, 3));
            dto.setLineColor(ExcelUtils.getCellString(row, 4));
            dto.setLineColorCn(ExcelUtils.getCellString(row, 5));
            dto.setTotalKm(ExcelUtils.getCellBigDecimal(row, 6));
            dto.setStationCount(ExcelUtils.getCellInt(row, 7));
            dto.setTransferLineCount(ExcelUtils.getCellInt(row, 8));
            dto.setTransferLines(ExcelUtils.getCellString(row, 9));
            dto.setTransferStationCount(ExcelUtils.getCellInt(row, 10));
            dto.setTransferStations(ExcelUtils.getCellString(row, 11));
            dto.setTrainCount(ExcelUtils.getCellInt(row, 12));
            dto.setAvgSpeed(ExcelUtils.getCellBigDecimal(row, 13));
            dto.setFirstTime(ExcelUtils.getCellString(row, 14));
            dto.setLastTime(ExcelUtils.getCellString(row, 15));
            dto.setFullTime(ExcelUtils.getCellInt(row, 16));
            dto.setOpenDate(ExcelUtils.getCellString(row, 17));
            dto.setStatusCode(ExcelUtils.getCellInt(row, 18));
            dto.setExtra(ExcelUtils.getCellString(row, 19));

            if (dto.getLineName() != null && !dto.getLineName().isEmpty()) {
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
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) Integer statusCode,
            @RequestParam(required = false) String ids) {

        List<MetroLine> lines;
        if (StringUtils.hasText(ids)) {
            List<Long> idList = Arrays.stream(ids.split(","))
                    .map(Long::parseLong).collect(Collectors.toList());
            lines = metroLineService.listByIds(idList);
        } else {
            LambdaQueryWrapper<MetroLine> wrapper = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(keyword)) {
                wrapper.and(w -> w
                        .like(MetroLine::getLineName, keyword)
                        .or().like(MetroLine::getLineNo, keyword)
                        .or().like(MetroLine::getCityName, keyword)
                );
            }
            if (countryId != null) wrapper.eq(MetroLine::getCountryId, countryId);
            if (cityId != null) wrapper.eq(MetroLine::getCityId, cityId);
            if (statusCode != null) wrapper.eq(MetroLine::getStatusCode, statusCode);
            wrapper.orderByDesc(MetroLine::getCreatedAt);
            lines = metroLineService.list(wrapper);
        }

        List<MetroLine> finalLines = lines;
        StreamingResponseBody body = outputStream -> {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String[] headers = {"ID", "国家", "城市", "线路名称", "线路编号", "线路颜色", "颜色中文", "里程(km)", "车站数", "换乘线路数", "列车数", "均速(km/h)", "首班车", "末班车", "全程(min)", "开通日期", "状态", "状态码", "创建时间"};

            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("地铁线路数据");
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
                for (MetroLine line : finalLines) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(line.getId() != null ? line.getId().doubleValue() : 0);
                    row.createCell(1).setCellValue(line.getCountryName() != null ? line.getCountryName() : "");
                    row.createCell(2).setCellValue(line.getCityName() != null ? line.getCityName() : "");
                    row.createCell(3).setCellValue(line.getLineName() != null ? line.getLineName() : "");
                    row.createCell(4).setCellValue(line.getLineNo() != null ? line.getLineNo() : "");
                    row.createCell(5).setCellValue(line.getLineColor() != null ? line.getLineColor() : "");
                    row.createCell(6).setCellValue(line.getLineColorCn() != null ? line.getLineColorCn() : "");
                    row.createCell(7).setCellValue(line.getTotalKm() != null ? line.getTotalKm().doubleValue() : 0);
                    row.createCell(8).setCellValue(line.getStationCount() != null ? line.getStationCount().doubleValue() : 0);
                    row.createCell(9).setCellValue(line.getTransferLineCount() != null ? line.getTransferLineCount().doubleValue() : 0);
                    row.createCell(10).setCellValue(line.getTrainCount() != null ? line.getTrainCount().doubleValue() : 0);
                    row.createCell(11).setCellValue(line.getAvgSpeed() != null ? line.getAvgSpeed().doubleValue() : 0);
                    row.createCell(12).setCellValue(line.getFirstTime() != null ? line.getFirstTime().toString() : "");
                    row.createCell(13).setCellValue(line.getLastTime() != null ? line.getLastTime().toString() : "");
                    row.createCell(14).setCellValue(line.getFullTime() != null ? line.getFullTime().doubleValue() : 0);
                    row.createCell(15).setCellValue(line.getOpenDate() != null ? line.getOpenDate().toString() : "");
                    row.createCell(16).setCellValue(line.getStatus() != null ? line.getStatus() : "");
                    row.createCell(17).setCellValue(line.getStatusCode() != null ? line.getStatusCode().doubleValue() : 0);
                    row.createCell(18).setCellValue(line.getCreatedAt() != null ? line.getCreatedAt().format(dtf) : "");
                }

                for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
                workbook.write(outputStream);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header("Content-Disposition", "attachment; filename=metro-lines.xlsx")
                .body(body);
    }
}
