package ec.edu.intesud.crono.excel;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;

/**
 * Lee celdas de una hoja sin importar cómo se guardaron: texto, número, fórmula con resultado o
 * celda combinada (todas las celdas del rango devuelven el valor de la primera). Filas y
 * columnas se numeran desde 1.
 */
public final class SheetReader {

  private final Sheet sheet;
  private final int rows;
  private final int columns;
  /** Celdas combinadas: posición -> posición de la celda que guarda el valor. */
  private final Map<Long, Long> mergedInto = new HashMap<>();

  public SheetReader(Sheet sheet) {
    this.sheet = sheet;
    this.rows = sheet.getLastRowNum() + 1;
    int width = 0;
    for (Row row : sheet) width = Math.max(width, row.getLastCellNum());
    this.columns = width;
    for (CellRangeAddress range : sheet.getMergedRegions()) {
      long master = key(range.getFirstRow() + 1, range.getFirstColumn() + 1);
      for (int row = range.getFirstRow(); row <= range.getLastRow(); row++) {
        for (int column = range.getFirstColumn(); column <= range.getLastColumn(); column++) {
          mergedInto.put(key(row + 1, column + 1), master);
        }
      }
    }
  }

  public String name() {
    return sheet.getSheetName();
  }

  /** Número de la última fila con contenido. */
  public int rows() {
    return rows;
  }

  /** Número de la última columna con contenido. */
  public int columns() {
    return columns;
  }

  /** Texto de la celda, limpio de espacios raros; "" si está vacía. */
  public String text(int row, int column) {
    Cell cell = cell(row, column);
    if (cell == null) return "";
    CellType type =
        cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
    return switch (type) {
      case STRING -> ImportText.tidy(cell.getStringCellValue());
      case NUMERIC ->
          DateUtil.isCellDateFormatted(cell)
              ? String.valueOf(cell.getLocalDateTimeCellValue())
              : numberText(cell.getNumericCellValue());
      case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
      default -> "";
    };
  }

  /** El número de la celda, o null si no lo es. Acepta decimales escritos con coma. */
  public Double number(int row, int column) {
    Cell cell = cell(row, column);
    if (cell == null) return null;
    CellType type =
        cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
    if (type == CellType.NUMERIC) return cell.getNumericCellValue();
    String text = text(row, column).replace(",", ".");
    if (text.isEmpty()) return null;
    try {
      double parsed = Double.parseDouble(text);
      return Double.isFinite(parsed) ? parsed : null;
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  /** Referencia legible de una celda, por ejemplo "AD42". */
  public static String cellRef(int row, int column) {
    return columnLetter(column) + row;
  }

  /** Letra de columna: 1 -> A, 27 -> AA. */
  public static String columnLetter(int column) {
    StringBuilder letters = new StringBuilder();
    int n = column;
    while (n > 0) {
      int rest = (n - 1) % 26;
      letters.insert(0, (char) ('A' + rest));
      n = (n - rest - 1) / 26;
    }
    return letters.toString();
  }

  /** 5.0 -> "5"; 3.3333 -> "3.3333". */
  public static String numberText(double value) {
    if (!Double.isFinite(value)) return "";
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
  }

  private Cell cell(int row, int column) {
    Long master = mergedInto.get(key(row, column));
    if (master != null) {
      row = (int) (master >> 32);
      column = (int) (long) master;
    }
    Row poiRow = sheet.getRow(row - 1);
    return poiRow == null ? null : poiRow.getCell(column - 1);
  }

  private static long key(int row, int column) {
    return ((long) row << 32) | column;
  }
}
