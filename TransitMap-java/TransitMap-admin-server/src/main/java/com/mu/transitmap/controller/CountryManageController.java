package com.mu.transitmap.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mu.transitmap.dto.CountryManageCreateDTO;
import com.mu.transitmap.dto.CountryManageQueryDTO;
import com.mu.transitmap.dto.CountryManageUpdateDTO;
import com.mu.transitmap.entity.Country;
import com.mu.transitmap.enums.CountryStatusEnum;
import com.mu.transitmap.enums.ErrorCode;
import com.mu.transitmap.exception.BusinessException;
import com.mu.transitmap.result.Result;
import com.mu.transitmap.service.impl.CountryServiceImpl;
import com.mu.transitmap.util.ExcelUtils;
import com.mu.transitmap.vo.CountrySelectIdNameVO;
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
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/manage/country")
public class CountryManageController {

    @Autowired
    private CountryServiceImpl countryService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/list")
    public Result<Page<Country>> getList(@Valid CountryManageQueryDTO dto) {
        Page<Country> page = countryService.getCountryPage(dto);
        return Result.success(page);
    }

    @GetMapping("/all")
    public Result<List<CountrySelectIdNameVO>> getAll() {
        return Result.success(countryService.allListId());
    }

    @GetMapping("/{id}")
    public Result<Country> getById(@PathVariable Long id) {
        Country country = countryService.getById(id);
        if (country == null) {
            throw new BusinessException(ErrorCode.COUNTRY_NOT_FOUND);
        }
        return Result.success(country);
    }

    @PostMapping
    public Result<Map<String, String>> create(@RequestBody @Valid CountryManageCreateDTO dto,
                                               HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        countryService.createCountry(dto, operatorRoleCode);
        return Result.success(null);
    }

    @PutMapping("/{id}")
    public Result<Map<String, String>> update(@PathVariable Long id,
                                               @RequestBody @Valid CountryManageUpdateDTO dto,
                                               HttpServletRequest request) {
        Integer operatorRoleCode = (Integer) request.getAttribute("roleCode");
        if (operatorRoleCode == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        countryService.updateCountry(id, dto, operatorRoleCode);
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
        countryService.deleteCountry(id);
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
        countryService.batchDeleteCountries(ids, operatorRoleCode);
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

        List<CountryManageCreateDTO> dtoList;
        String lowerFilename = filename.toLowerCase();

        try (InputStream is = file.getInputStream()) {
            if (lowerFilename.endsWith(".json")) {
                dtoList = objectMapper.readValue(is,
                        new TypeReference<List<CountryManageCreateDTO>>() {});
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

        int successCount = countryService.batchImportCountries(dtoList, operatorRoleCode);
        return Result.success(Map.of("successCount", successCount, "totalCount", dtoList.size()));
    }

    private List<CountryManageCreateDTO> parseExcel(InputStream is) throws Exception {
        List<CountryManageCreateDTO> list = new ArrayList<>();
        Workbook workbook = new XSSFWorkbook(is);
        Sheet sheet = workbook.getSheetAt(0);

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            CountryManageCreateDTO dto = new CountryManageCreateDTO();
            dto.setCountryName(ExcelUtils.getCellString(row, 0));
            dto.setCountryNameEn(ExcelUtils.getCellString(row, 1));
            dto.setCountryAlias(ExcelUtils.getCellString(row, 2));
            dto.setCityCount(ExcelUtils.getCellInt(row, 3));
            dto.setMetroLineCount(ExcelUtils.getCellInt(row, 4));
            dto.setMetroStationCount(ExcelUtils.getCellInt(row, 5));
            dto.setMetroKm(ExcelUtils.getCellBigDecimal(row, 6));
            dto.setHsrStationCount(ExcelUtils.getCellInt(row, 7));
            dto.setHsrKm(ExcelUtils.getCellBigDecimal(row, 8));
            dto.setStatusCode(ExcelUtils.getCellInt(row, 9));

            if (dto.getCountryName() != null && !dto.getCountryName().isEmpty()) {
                list.add(dto);
            }
        }

        workbook.close();
        return list;
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer statusCode,
            @RequestParam(required = false) Integer onlineStatusCode,
            @RequestParam(required = false) String ids) {

        List<Country> countries;
        if (StringUtils.hasText(ids)) {
            List<Long> idList = Arrays.stream(ids.split(","))
                    .map(Long::parseLong).collect(Collectors.toList());
            countries = countryService.listByIds(idList);
        } else {
            LambdaQueryWrapper<Country> wrapper = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(keyword)) {
                wrapper.and(w -> w
                        .like(Country::getCountryName, keyword)
                        .or().like(Country::getCountryNameEn, keyword)
                        .or().like(Country::getCountryAlias, keyword)
                );
            }
            if (statusCode != null) wrapper.eq(Country::getStatusCode, statusCode);
            if (onlineStatusCode != null) wrapper.eq(Country::getStatusCode, onlineStatusCode);
            wrapper.orderByDesc(Country::getCreatedAt);
            countries = countryService.list(wrapper);
        }

        List<Country> finalCountries = countries;
        StreamingResponseBody body = outputStream -> {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String[] headers = {"ID", "国家名称", "英文名称", "别称", "城市数", "地铁线路", "地铁站", "地铁里程(km)", "高铁站", "高铁里程(km)", "状态", "状态码", "创建时间"};

            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("国家数据");
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
                for (Country c : finalCountries) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(c.getId() != null ? c.getId().doubleValue() : 0);
                    row.createCell(1).setCellValue(c.getCountryName() != null ? c.getCountryName() : "");
                    row.createCell(2).setCellValue(c.getCountryNameEn() != null ? c.getCountryNameEn() : "");
                    row.createCell(3).setCellValue(c.getCountryAlias() != null ? c.getCountryAlias() : "");
                    row.createCell(4).setCellValue(c.getCityCount() != null ? c.getCityCount().doubleValue() : 0);
                    row.createCell(5).setCellValue(c.getMetroLineCount() != null ? c.getMetroLineCount().doubleValue() : 0);
                    row.createCell(6).setCellValue(c.getMetroStationCount() != null ? c.getMetroStationCount().doubleValue() : 0);
                    row.createCell(7).setCellValue(c.getMetroKm() != null ? c.getMetroKm().doubleValue() : 0);
                    row.createCell(8).setCellValue(c.getHsrStationCount() != null ? c.getHsrStationCount().doubleValue() : 0);
                    row.createCell(9).setCellValue(c.getHsrKm() != null ? c.getHsrKm().doubleValue() : 0);
                    row.createCell(10).setCellValue(c.getStatus() != null ? c.getStatus() : "");
                    row.createCell(11).setCellValue(c.getStatusCode() != null ? c.getStatusCode().doubleValue() : 0);
                    row.createCell(12).setCellValue(c.getCreatedAt() != null ? c.getCreatedAt().format(dtf) : "");
                }

                for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
                workbook.write(outputStream);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header("Content-Disposition", "attachment; filename=countries.xlsx")
                .body(body);
    }
}
