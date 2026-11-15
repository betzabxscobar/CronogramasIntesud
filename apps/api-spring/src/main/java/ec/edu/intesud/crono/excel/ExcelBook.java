package ec.edu.intesud.crono.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Capa fina sobre Apache POI para escribir libros con formato celda por celda, como se hacía
 * con exceljs: cada celda guarda su letra, fondo, alineación y borde, y al terminar se crean los
 * estilos de Excel reutilizando los que se repiten. Filas y columnas se numeran desde 1.
 */
public final class ExcelBook {

  /** Color fijo ("FFRRGGBB") o del tema de Excel (con su matiz). */
  public record Ink(String argb, Integer theme, Double tint) {
    public static Ink argb(String argb) {
      return new Ink(argb, null, null);
    }

    /** "#AABBCC" -> color fijo opaco. */
    public static Ink hex(String hex) {
      return new Ink("FF" + hex.substring(1), null, null);
    }

    public static Ink theme(int theme) {
      return new Ink(null, theme, null);
    }

    public static Ink theme(int theme, double tint) {
      return new Ink(null, theme, tint);
    }
  }

  public record FontSpec(String name, int size, boolean bold) {
    public FontSpec withSize(int newSize) {
      return new FontSpec(name, newSize, bold);
    }
  }

  /** Alineación; siempre centrada en vertical. */
  public record Align(boolean center, boolean wrap, int rotation) {
    public static final Align LEFT = new Align(false, false, 0);
    public static final Align CENTER = new Align(true, false, 0);
  }

  private record Format(FontSpec font, Ink fontColor, Ink fill, Align align, boolean border) {
    static final Format EMPTY = new Format(null, null, null, null, false);
  }

  private record FontKey(FontSpec font, Ink color) {}

  private final XSSFWorkbook workbook = new XSSFWorkbook();
  private final List<Sheet> sheets = new ArrayList<>();
  private final Map<Format, XSSFCellStyle> styles = new HashMap<>();
  private final Map<FontKey, XSSFFont> fonts = new HashMap<>();

  public ExcelBook(String creator) {
    workbook.getProperties().getCoreProperties().setCreator(creator);
  }

  public Sheet sheet(String name) {
    Sheet sheet = new Sheet(workbook.createSheet(name));
    sheets.add(sheet);
    return sheet;
  }

  /** Aplica los formatos y devuelve el archivo .xlsx. */
  public byte[] toBytes() {
    for (Sheet sheet : sheets) sheet.applyFormats();
    try (workbook;
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      workbook.write(output);
      return output.toByteArray();
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private XSSFColor color(Ink ink) {
    if (ink.argb() != null) {
      String hex = ink.argb().substring(ink.argb().length() - 6);
      byte[] rgb = {
        (byte) Integer.parseInt(hex.substring(0, 2), 16),
        (byte) Integer.parseInt(hex.substring(2, 4), 16),
        (byte) Integer.parseInt(hex.substring(4, 6), 16)
      };
      return new XSSFColor(rgb, null);
    }
    XSSFColor color = new XSSFColor(workbook.getStylesSource().getIndexedColors());
    color.setTheme(ink.theme());
    if (ink.tint() != null) color.setTint(ink.tint());
    return color;
  }

  private XSSFFont font(FontKey key) {
    return fonts.computeIfAbsent(
        key,
        ignored -> {
          XSSFFont font = workbook.createFont();
          font.setFontName(key.font().name());
          font.setFontHeightInPoints((short) key.font().size());
          font.setBold(key.font().bold());
          if (key.color() != null) font.setColor(color(key.color()));
          return font;
        });
  }

  private XSSFCellStyle style(Format format) {
    return styles.computeIfAbsent(
        format,
        ignored -> {
          XSSFCellStyle style = workbook.createCellStyle();
          if (format.font() != null) {
            style.setFont(font(new FontKey(format.font(), format.fontColor())));
          }
          if (format.fill() != null) {
            style.setFillForegroundColor(color(format.fill()));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
          }
          if (format.align() != null) {
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setAlignment(
                format.align().center() ? HorizontalAlignment.CENTER : HorizontalAlignment.LEFT);
            style.setWrapText(format.align().wrap());
            style.setRotation((short) format.align().rotation());
          }
          if (format.border()) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
          }
          return style;
        });
  }

  /** Una hoja del libro. */
  public final class Sheet {

    private final XSSFSheet sheet;
    private final Map<Long, Format> formats = new HashMap<>();

    private Sheet(XSSFSheet sheet) {
      this.sheet = sheet;
    }

    public Cell cell(int row, int column) {
      return new Cell(this, row, column);
    }

    /** Ancho en caracteres, la misma unidad que muestra Excel. */
    public void columnWidth(int column, double width) {
      sheet.setColumnWidth(column - 1, (int) Math.round(width * 256));
    }

    public void rowHeight(int row, double points) {
      row(row).setHeightInPoints((float) points);
    }

    /** Combina el rango; todas sus celdas toman el formato de la primera. */
    public void merge(int firstRow, int firstColumn, int lastRow, int lastColumn) {
      Format master = formats.getOrDefault(key(firstRow, firstColumn), Format.EMPTY);
      for (int row = firstRow; row <= lastRow; row++) {
        for (int column = firstColumn; column <= lastColumn; column++) {
          formats.put(key(row, column), master);
        }
      }
      sheet.addMergedRegion(
          new CellRangeAddress(firstRow - 1, lastRow - 1, firstColumn - 1, lastColumn - 1));
    }

    private XSSFRow row(int row) {
      XSSFRow existing = sheet.getRow(row - 1);
      return existing == null ? sheet.createRow(row - 1) : existing;
    }

    private XSSFCell poiCell(int row, int column) {
      XSSFRow poiRow = row(row);
      XSSFCell existing = poiRow.getCell(column - 1);
      return existing == null ? poiRow.createCell(column - 1) : existing;
    }

    private void applyFormats() {
      formats.forEach(
          (key, format) -> {
            if (!format.equals(Format.EMPTY)) {
              poiCell((int) (key >> 32), (int) (long) key).setCellStyle(style(format));
            }
          });
    }

    private static long key(int row, int column) {
      return ((long) row << 32) | column;
    }
  }

  /** Una celda: los métodos devuelven la misma celda para encadenarlos. */
  public static final class Cell {

    private final Sheet sheet;
    private final int row;
    private final int column;

    private Cell(Sheet sheet, int row, int column) {
      this.sheet = sheet;
      this.row = row;
      this.column = column;
    }

    /** Texto de la celda; null o vacío la deja sin valor. */
    public Cell text(String value) {
      if (value != null && !value.isEmpty()) sheet.poiCell(row, column).setCellValue(value);
      return this;
    }

    public Cell number(Integer value) {
      if (value != null) sheet.poiCell(row, column).setCellValue(value.doubleValue());
      return this;
    }

    public Cell font(FontSpec font, Ink color) {
      Format current = format();
      return set(new Format(font, color, current.fill(), current.align(), current.border()));
    }

    public Cell fill(Ink fill) {
      Format current = format();
      return set(
          new Format(current.font(), current.fontColor(), fill, current.align(), current.border()));
    }

    public Cell align(Align align) {
      Format current = format();
      return set(
          new Format(current.font(), current.fontColor(), current.fill(), align, current.border()));
    }

    /** Borde fino en los cuatro lados. */
    public Cell border() {
      Format current = format();
      return set(
          new Format(current.font(), current.fontColor(), current.fill(), current.align(), true));
    }

    private Format format() {
      return sheet.formats.getOrDefault(Sheet.key(row, column), Format.EMPTY);
    }

    private Cell set(Format format) {
      sheet.formats.put(Sheet.key(row, column), format);
      return this;
    }
  }
}
