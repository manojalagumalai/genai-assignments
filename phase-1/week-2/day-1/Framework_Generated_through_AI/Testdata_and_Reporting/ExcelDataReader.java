package com.api.framework.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * ExcelDataReader — Apache POI-based utility to read test data from .xlsx files.
 *
 * Supports:
 *  - Reading all rows from a named sheet as List<Map<String, String>>
 *  - Filtering rows by a column value (e.g. "runFlag" = "Y")
 *  - Converting sheet data to Object[][] for TestNG @DataProvider
 *  - Reading a single cell by sheet + row + column index
 *  - Auto-detecting cell types (string, numeric, boolean, formula, blank)
 *
 * Excel File Convention:
 *  - Row 1  → Header row (column names)
 *  - Row 2+ → Data rows
 *  - Column "runFlag" (optional) → set "Y" to include, "N" to skip
 *
 * Usage in TestNG DataProvider:
 *   @DataProvider(name = "createUserData")
 *   public Object[][] getUserData() {
 *       return ExcelDataReader.getDataProvider("testdata/testdata.xlsx", "CreateUser");
 *   }
 */
public class ExcelDataReader {

    private static final Logger log = LoggerFactory.getLogger(ExcelDataReader.class);
    private static final String RUN_FLAG_COLUMN = "runFlag";
    private static final String RUN_FLAG_YES    = "Y";

    // ─── Private Constructor (Utility Class) ──────────────────────────────
    private ExcelDataReader() {}

    // ═══════════════════════════════════════════════════════════════════════
    // PRIMARY PUBLIC METHODS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Reads all data rows from a sheet.
     * Returns a list where each entry is a Map of {columnName → cellValue}.
     *
     * @param filePath  classpath path to the .xlsx file
     * @param sheetName name of the Excel sheet to read
     * @return List of row maps (header keys → cell values)
     */
    public static List<Map<String, String>> getSheetData(String filePath, String sheetName) {
        log.info("Reading Excel → file: [{}] | sheet: [{}]", filePath, sheetName);

        try (Workbook workbook = openWorkbook(filePath)) {
            Sheet sheet = getSheet(workbook, sheetName);
            List<String> headers = extractHeaders(sheet);
            List<Map<String, String>> data = extractRows(sheet, headers, false);

            log.info("✅ Loaded {} rows from sheet [{}]", data.size(), sheetName);
            return data;

        } catch (IOException e) {
            throw new RuntimeException("❌ Failed to read Excel file: " + filePath, e);
        }
    }

    /**
     * Reads only rows where the 'runFlag' column = 'Y'.
     * Rows with runFlag = 'N' (or blank) are skipped.
     *
     * @param filePath  classpath path to the .xlsx file
     * @param sheetName name of the sheet
     * @return filtered list of row maps
     */
    public static List<Map<String, String>> getActiveRows(String filePath, String sheetName) {
        log.info("Reading active rows → file: [{}] | sheet: [{}]", filePath, sheetName);

        try (Workbook workbook = openWorkbook(filePath)) {
            Sheet sheet = getSheet(workbook, sheetName);
            List<String> headers = extractHeaders(sheet);
            List<Map<String, String>> allRows = extractRows(sheet, headers, false);

            List<Map<String, String>> activeRows = allRows.stream()
                    .filter(row -> RUN_FLAG_YES.equalsIgnoreCase(
                            row.getOrDefault(RUN_FLAG_COLUMN, RUN_FLAG_YES)))
                    .toList();

            log.info("✅ Active rows: {}/{} from sheet [{}]",
                    activeRows.size(), allRows.size(), sheetName);
            return activeRows;

        } catch (IOException e) {
            throw new RuntimeException("❌ Failed to read Excel file: " + filePath, e);
        }
    }

    /**
     * Converts sheet data into Object[][] for use with TestNG @DataProvider.
     * Each row becomes Object[]{Map<String, String>} so tests receive a Map.
     *
     * @param filePath  classpath path to the .xlsx file
     * @param sheetName name of the sheet
     * @return Object[][] for @DataProvider
     */
    public static Object[][] getDataProvider(String filePath, String sheetName) {
        List<Map<String, String>> rows = getActiveRows(filePath, sheetName);

        Object[][] data = new Object[rows.size()][1];
        for (int i = 0; i < rows.size(); i++) {
            data[i][0] = rows.get(i);
        }

        log.info("✅ DataProvider ready → {} test cases from sheet [{}]", data.length, sheetName);
        return data;
    }

    /**
     * Reads a specific cell value by sheet name, row index, and column index.
     *
     * @param filePath    classpath path to the .xlsx file
     * @param sheetName   name of the sheet
     * @param rowIndex    0-based row index (0 = header row)
     * @param columnIndex 0-based column index
     * @return cell value as String
     */
    public static String getCellValue(String filePath, String sheetName,
                                      int rowIndex, int columnIndex) {
        try (Workbook workbook = openWorkbook(filePath)) {
            Sheet sheet = getSheet(workbook, sheetName);
            Row row = sheet.getRow(rowIndex);

            if (row == null) {
                throw new RuntimeException(
                    String.format("❌ Row %d not found in sheet [%s]", rowIndex, sheetName));
            }

            Cell cell = row.getCell(columnIndex);
            return resolveCellValue(cell);

        } catch (IOException e) {
            throw new RuntimeException("❌ Failed to read Excel file: " + filePath, e);
        }
    }

    /**
     * Returns the names of all sheets in the workbook.
     * Useful for dynamic sheet discovery.
     */
    public static List<String> getSheetNames(String filePath) {
        try (Workbook workbook = openWorkbook(filePath)) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                names.add(workbook.getSheetName(i));
            }
            log.info("Sheets found in [{}]: {}", filePath, names);
            return names;
        } catch (IOException e) {
            throw new RuntimeException("❌ Failed to open workbook: " + filePath, e);
        }
    }

    /**
     * Returns the total number of data rows in a sheet (excludes header row).
     */
    public static int getRowCount(String filePath, String sheetName) {
        try (Workbook workbook = openWorkbook(filePath)) {
            Sheet sheet = getSheet(workbook, sheetName);
            int count = Math.max(0, sheet.getLastRowNum()); // lastRowNum is 0-based
            log.info("Row count in sheet [{}]: {}", sheetName, count);
            return count;
        } catch (IOException e) {
            throw new RuntimeException("❌ Failed to read workbook: " + filePath, e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════

    /** Opens the workbook from the classpath */
    private static Workbook openWorkbook(String filePath) throws IOException {
        InputStream inputStream = ExcelDataReader.class
                .getClassLoader()
                .getResourceAsStream(filePath);

        if (inputStream == null) {
            throw new RuntimeException(
                "❌ Excel file not found on classpath: [" + filePath + "]. " +
                "Ensure it exists under src/test/resources/");
        }
        return new XSSFWorkbook(inputStream);
    }

    /** Fetches a sheet by name; throws a clear error if not found */
    private static Sheet getSheet(Workbook workbook, String sheetName) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            List<String> available = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                available.add(workbook.getSheetName(i));
            }
            throw new RuntimeException(
                String.format("❌ Sheet [%s] not found. Available sheets: %s",
                        sheetName, available));
        }
        return sheet;
    }

    /** Reads header row (row 0) and returns column names as a list */
    private static List<String> extractHeaders(Sheet sheet) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            throw new RuntimeException("❌ Header row (row 0) is missing from sheet: "
                    + sheet.getSheetName());
        }

        List<String> headers = new ArrayList<>();
        for (Cell cell : headerRow) {
            headers.add(resolveCellValue(cell).trim());
        }

        log.debug("Headers found: {}", headers);
        return headers;
    }

    /** Reads all data rows (row 1 onwards) and maps them to header keys */
    private static List<Map<String, String>> extractRows(Sheet sheet,
                                                          List<String> headers,
                                                          boolean includeEmpty) {
        List<Map<String, String>> rows = new ArrayList<>();

        for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) continue;

            Map<String, String> rowMap = new LinkedHashMap<>();
            boolean hasData = false;

            for (int colNum = 0; colNum < headers.size(); colNum++) {
                Cell cell = row.getCell(colNum, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                String value = (cell != null) ? resolveCellValue(cell) : "";
                rowMap.put(headers.get(colNum), value);
                if (!value.isBlank()) hasData = true;
            }

            if (hasData || includeEmpty) {
                rows.add(rowMap);
            }
        }
        return rows;
    }

    /**
     * Resolves any cell type to a String value.
     * Handles: STRING, NUMERIC (int + decimal + date), BOOLEAN, FORMULA, BLANK
     */
    private static String resolveCellValue(Cell cell) {
        if (cell == null) return "";

        DataFormatter formatter = new DataFormatter();

        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                            ? formatter.formatCellValue(cell)
                            : formatNumeric(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> evaluateFormula(cell);
            case BLANK   -> "";
            default      -> "";
        };
    }

    /** Formats numeric values: removes trailing .0 for whole numbers */
    private static String formatNumeric(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /** Evaluates formula cells and returns the cached string value */
    private static String evaluateFormula(Cell cell) {
        try {
            return switch (cell.getCachedFormulaResultType()) {
                case STRING  -> cell.getStringCellValue();
                case NUMERIC -> formatNumeric(cell.getNumericCellValue());
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                default      -> "";
            };
        } catch (Exception e) {
            log.warn("Could not evaluate formula cell at row {}, col {} — returning empty",
                    cell.getRowIndex(), cell.getColumnIndex());
            return "";
        }
    }
}
