package com.mu.transitmap.util;

import org.apache.poi.ss.usermodel.*;

import java.math.BigDecimal;

public class ExcelUtils {

    /**
     * 安全地获取单元格字符串值，兼容所有单元格类型
     * 替代已废弃的 cell.setCellType(CellType.STRING) 方式
     */
    public static String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case STRING:
                String val = cell.getStringCellValue();
                return val != null ? val.trim() : null;
            case NUMERIC:
                // 数字转字符串：避免 "1.0" 问题，整数转 "1"
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    return String.valueOf((long) d);
                }
                return String.valueOf(d);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    String formulaVal = cell.getStringCellValue();
                    if (formulaVal != null) return formulaVal.trim();
                } catch (Exception ignored) {}
                try {
                    double formulaNum = cell.getNumericCellValue();
                    return String.valueOf(formulaNum);
                } catch (Exception ignored) {}
                return null;
            case BLANK:
            case ERROR:
            default:
                return null;
        }
    }

    public static Integer getCellInt(Row row, int col) {
        String val = getCellString(row, col);
        if (val == null || val.isEmpty()) return null;
        try {
            return Integer.parseInt(val);
        } catch (Exception e) {
            return null;
        }
    }

    public static Long getCellLong(Row row, int col) {
        String val = getCellString(row, col);
        if (val == null || val.isEmpty()) return null;
        try {
            return Long.parseLong(val);
        } catch (Exception e) {
            return null;
        }
    }

    public static BigDecimal getCellBigDecimal(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case NUMERIC:
                return BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING:
                String val = cell.getStringCellValue();
                if (val == null || val.trim().isEmpty()) return null;
                try {
                    return new BigDecimal(val.trim());
                } catch (Exception e) {
                    return null;
                }
            case FORMULA:
                try {
                    return BigDecimal.valueOf(cell.getNumericCellValue());
                } catch (Exception ignored) {
                    return null;
                }
            default:
                return null;
        }
    }
}
