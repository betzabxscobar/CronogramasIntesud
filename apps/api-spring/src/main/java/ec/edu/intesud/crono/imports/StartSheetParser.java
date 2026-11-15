package ec.edu.intesud.crono.imports;

import static ec.edu.intesud.crono.excel.ImportText.comparable;
import static ec.edu.intesud.crono.excel.SheetReader.columnLetter;

import ec.edu.intesud.crono.domain.SpaceType;
import ec.edu.intesud.crono.excel.ImportText;
import ec.edu.intesud.crono.excel.ImportText.ExtractedTitle;
import ec.edu.intesud.crono.excel.ImportText.SplitPerson;
import ec.edu.intesud.crono.excel.SheetReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Lector de la hoja "Inicio" del libro de horarios.
 *
 * <p>A la izquierda están las listas maestras: la carrera activa en A2, los docentes en D, las
 * aulas en F y los niveles en H. A la derecha hay un catálogo de asignaturas por carrera, que se
 * localiza por la etiqueta "Asignaturas ..." de la fila 2 y ocupa seis columnas:
 *
 * <pre>
 *   col+0  Asignatura   (fila 1 = carrera, fila 2 = "Asignaturas XX:")
 *   col+2  Créditos
 *   col+3  Código
 *   col+4  Nivel
 * </pre>
 */
public final class StartSheetParser {

  private static final int COL_CAREER = 1; // A
  private static final int ROW_CAREER_VALUE = 2;
  private static final int COL_PROFESSOR = 4; // D
  private static final int COL_ROOM = 6; // F
  private static final int COL_LEVEL = 8; // H
  private static final int ROW_LIST_START = 2;
  private static final int ROW_PROFESSOR_START = 3;
  private static final int CATALOG_HEADER_ROW = 2;
  private static final int CATALOG_FIRST_DATA_ROW = 4;
  /** Techo de seguridad: ninguna lista real del libro pasa de 60 filas. */
  private static final int MAX_LIST_ROWS = 60;

  public record DraftLevel(int order, String name, int sourceRow) {}

  public record DraftSubject(
      String name,
      String code,
      Integer levelOrder,
      Double credits,
      int sourceRow,
      int blockColumn,
      List<String> notes) {}

  public record DraftProfessor(
      String firstNames,
      String lastNames,
      String professionalTitle,
      int sourceRow,
      List<String> notes) {}

  public record DraftSpace(
      String name, String code, SpaceType type, int sourceRow, List<String> notes) {}

  /** Catálogo de asignaturas encontrado: su columna, la carrera y cuántas materias trae. */
  public record CatalogBlock(int column, String career, int subjects) {}

  /** Celda que no se importó, con el motivo. */
  public record SkippedCell(String cell, String value, String reason) {}

  /** Resultado de leer la hoja. */
  public static final class Result {
    public String sheetName;
    public String activeCareer;
    public final List<DraftLevel> levels = new ArrayList<>();
    public List<DraftSubject> subjects = new ArrayList<>();
    public final List<DraftProfessor> professors = new ArrayList<>();
    public final List<DraftSpace> spaces = new ArrayList<>();
    public final List<CatalogBlock> catalogBlocks = new ArrayList<>();
    public final List<SkippedCell> skipped = new ArrayList<>();
    public final List<String> notes = new ArrayList<>();
  }

  private StartSheetParser() {}

  /**
   * @param catalogColumn columna del catálogo de asignaturas a importar; si es null se usa el de
   *     la carrera que el libro tiene seleccionada.
   */
  public static Result parse(SheetReader sheet, Integer catalogColumn) {
    Result result = new Result();
    result.sheetName = sheet.name();

    // --- carrera activa ---
    String activeCareer = sheet.text(ROW_CAREER_VALUE, COL_CAREER);
    if (!ImportText.isFiller(activeCareer)) {
      result.activeCareer = activeCareer;
    } else {
      result.notes.add(
          "No se encontró la carrera activa en " + columnLetter(COL_CAREER) + ROW_CAREER_VALUE + ".");
    }

    // --- niveles (columna H) ---
    readRun(
        sheet,
        COL_LEVEL,
        ROW_LIST_START,
        result.skipped,
        (row, value) -> {
          Integer order = ImportText.levelLabelToOrder(value);
          if (order == null) {
            result.skipped.add(
                new SkippedCell(
                    columnLetter(COL_LEVEL) + row, value, "No se pudo deducir el número de nivel"));
            return;
          }
          result.levels.add(new DraftLevel(order, value, row));
        });
    if (result.levels.isEmpty()) result.notes.add("La lista de niveles está vacía.");

    // --- docentes (columna D) ---
    readRun(
        sheet,
        COL_PROFESSOR,
        ROW_PROFESSOR_START,
        result.skipped,
        (row, value) -> {
          ExtractedTitle title = ImportText.extractTitle(value);
          SplitPerson person = ImportText.splitPersonName(title.rest());
          if (person.firstNames().isEmpty() || person.lastNames().isEmpty()) {
            // No se puede registrar tal cual: queda a la vista para que una persona decida.
            result.skipped.add(
                new SkippedCell(
                    columnLetter(COL_PROFESSOR) + row,
                    value,
                    person.reason() == null
                        ? "No se pudo separar nombre y apellido"
                        : person.reason()));
            return;
          }
          List<String> notes = new ArrayList<>();
          if (person.ambiguous()) {
            notes.add(person.reason() == null ? "Nombre ambiguo" : person.reason());
          }
          result.professors.add(
              new DraftProfessor(
                  person.firstNames(), person.lastNames(), title.title(), row, notes));
        });
    if (result.professors.isEmpty()) {
      result.notes.add("No se encontró ninguna lista de docentes.");
    }

    // --- aulas (columna F) ---
    Set<String> takenCodes = new HashSet<>();
    readRun(
        sheet,
        COL_ROOM,
        ROW_LIST_START,
        result.skipped,
        (row, value) ->
            result.spaces.add(
                new DraftSpace(
                    value,
                    ImportText.spaceCodeFromName(value, takenCodes),
                    ImportText.spaceTypeFromName(value),
                    row,
                    new ArrayList<>())));
    if (result.spaces.isEmpty()) result.notes.add("No se encontró ninguna lista de aulas.");

    // --- catálogos de asignaturas ---
    for (int base = 1; base <= Math.max(sheet.columns(), 1); base++) {
      if (!comparable(sheet.text(CATALOG_HEADER_ROW, base)).startsWith("asignaturas")) continue;
      String career = sheet.text(1, base);
      int count = 0;
      int lastRow = Math.min(sheet.rows(), CATALOG_FIRST_DATA_ROW + 400);
      for (int row = CATALOG_FIRST_DATA_ROW; row <= lastRow; row++) {
        String name = sheet.text(row, base);
        if (name.isEmpty()) break; // celda vacía: fin del bloque
        if (ImportText.isFiller(name)) {
          // Separadores entre niveles y al final del bloque: se omiten, no cortan.
          result.skipped.add(
              new SkippedCell(columnLetter(base) + row, name, "Separador del catálogo"));
          continue;
        }
        List<String> notes = new ArrayList<>();
        String code = sheet.text(row, base + 3);
        if (code.isEmpty()) code = null;
        Integer levelOrder = ImportText.levelLabelToOrder(sheet.text(row, base + 4));
        if (levelOrder == null) levelOrder = ImportText.levelFromSubjectCode(code);
        if (levelOrder == null) notes.add("El libro no indica el nivel de esta materia.");
        if (code == null) notes.add("La materia no tiene código en el libro.");
        result.subjects.add(
            new DraftSubject(
                name, code, levelOrder, sheet.number(row, base + 2), row, base, notes));
        count++;
      }
      result.catalogBlocks.add(new CatalogBlock(base, career, count));
    }
    if (result.catalogBlocks.isEmpty()) {
      result.notes.add("No se encontró ningún bloque \"Asignaturas\" en la hoja Inicio.");
    }

    chooseCatalog(result, catalogColumn);
    return result;
  }

  /**
   * Si no se indicó bloque, se usa el de la carrera que el propio libro tiene seleccionada. Sin
   * esto se importarían los catálogos de todas las carreras contra una sola, que es un error
   * fácil de cometer y difícil de detectar después.
   */
  private static void chooseCatalog(Result result, Integer requestedColumn) {
    CatalogBlock byName =
        result.activeCareer == null
            ? null
            : result.catalogBlocks.stream()
                .filter(b -> comparable(b.career()).equals(comparable(result.activeCareer)))
                .findFirst()
                .orElse(null);
    CatalogBlock byColumn =
        requestedColumn == null
            ? null
            : result.catalogBlocks.stream()
                .filter(b -> b.column() == requestedColumn)
                .findFirst()
                .orElse(null);

    if (byColumn != null && byName != null && byColumn != byName) {
      keepOnly(result, byColumn);
      result.notes.add(
          "Pediste el catálogo «"
              + byColumn.career()
              + "», pero el libro tiene seleccionada «"
              + result.activeCareer
              + "». Se importó el que pediste.");
    } else if (byColumn != null || byName != null) {
      CatalogBlock chosen = byColumn != null ? byColumn : byName;
      int discarded = result.catalogBlocks.size() - 1;
      keepOnly(result, chosen);
      String detail =
          "«"
              + chosen.career()
              + "» ("
              + chosen.subjects()
              + " materias) y se descartaron "
              + discarded
              + " bloques de otras carreras.";
      result.notes.add(
          requestedColumn == null
              ? "El libro tiene seleccionada la carrera «"
                  + chosen.career()
                  + "»: se importó solo su catálogo ("
                  + chosen.subjects()
                  + " materias) y se descartaron "
                  + discarded
                  + " bloques de otras carreras."
              : "Se conservó el catálogo " + detail);
    } else if (result.activeCareer != null) {
      // Ni la columna pedida ni la carrera activa están en la hoja: se leen todos y se avisa.
      result.notes.add(
          "No se encontró el catálogo de «"
              + result.activeCareer
              + "» ni en la columna "
              + requestedColumn
              + ". Se leyeron los "
              + result.catalogBlocks.size()
              + " bloques del libro y las materias de todas las carreras quedaron mezcladas:"
              + " revísalas antes de confirmar.");
    } else if (requestedColumn != null) {
      result.notes.add(
          "La columna "
              + requestedColumn
              + " no corresponde a un bloque de asignaturas y el libro no indica la carrera activa:"
              + " se leyeron todos los bloques.");
    }
  }

  private static void keepOnly(Result result, CatalogBlock chosen) {
    result.subjects =
        new ArrayList<>(
            result.subjects.stream().filter(s -> s.blockColumn() == chosen.column()).toList());
  }

  /**
   * Recorre una lista vertical. Los rellenos ("- Sin Profesor -", "Aula XX") aparecen dentro de
   * las listas, así que se omiten y se sigue leyendo; la lista termina en la primera celda vacía.
   */
  private static void readRun(
      SheetReader sheet,
      int column,
      int startRow,
      List<SkippedCell> skipped,
      BiConsumer<Integer, String> onValue) {
    int limit = Math.min(sheet.rows(), startRow + MAX_LIST_ROWS);
    for (int row = startRow; row <= limit; row++) {
      String raw = sheet.text(row, column);
      if (raw.isEmpty()) return;
      if (ImportText.isFiller(raw)) {
        skipped.add(
            new SkippedCell(
                columnLetter(column) + row, raw, "Celda de relleno del libro, no es un dato"));
        continue;
      }
      onValue.accept(row, raw);
    }
  }
}
