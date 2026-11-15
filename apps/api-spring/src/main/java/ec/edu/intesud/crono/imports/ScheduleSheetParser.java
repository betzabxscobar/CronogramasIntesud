package ec.edu.intesud.crono.imports;

import static ec.edu.intesud.crono.excel.ImportText.comparable;
import static ec.edu.intesud.crono.excel.SheetReader.cellRef;

import ec.edu.intesud.crono.excel.ImportText;
import ec.edu.intesud.crono.excel.SheetReader;
import ec.edu.intesud.crono.imports.StartSheetParser.SkippedCell;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lectores de las hojas de horario del libro.
 *
 * <p>Las dos hojas repiten el mismo horario por nivel y por docente; solo se leen los bloques de
 * nivel, porque los de docente son la misma información vista desde otro lado.
 *
 * <p><b>Diurno</b>: cada bloque ocupa siete columnas ("Horas" y los días) y cada franja son tres
 * filas: materia, docente y aula. Varias franjas seguidas idénticas son una sola clase larga.
 *
 * <p><b>Nocturno</b>: no tiene días; el eje horizontal son las semanas, agrupadas de tres en
 * tres por módulo. Cada franja son dos filas (materia y docente) y no se asigna aula. Las clases
 * se guardan en lunes, que es la convención de la aplicación.
 */
public final class ScheduleSheetParser {

  /** Una clase leída del libro. Las horas van como "HH:mm:ss"; dayOfWeek: 1 = lunes. */
  public record DraftBlock(
      Integer levelOrder,
      Integer dayOfWeek,
      String startTime,
      String endTime,
      Integer moduleNumber,
      String subjectName,
      String professorName,
      String spaceName,
      int sourceRow,
      int sourceColumn,
      List<String> notes) {}

  /** Resultado de leer una hoja de horario. */
  public static final class Result {
    public final List<DraftBlock> blocks = new ArrayList<>();
    /** Bloques por docente detectados y descartados. */
    public int professorBlocksIgnored;
    /** Celdas del nocturno con la plantilla sin llenar ("Asignatura" en vez de una materia). */
    public int placeholderCount;
    public final List<String> notes = new ArrayList<>();
    public final List<SkippedCell> skipped = new ArrayList<>();
  }

  private record Slot(int row, int start, int end) {}

  private record DayCell(
      int row,
      int column,
      int start,
      int end,
      String subject,
      String professor,
      String space,
      List<String> notes) {}

  private static final Map<String, Integer> DAY_LABELS =
      Map.of("lunes", 1, "martes", 2, "miercoles", 3, "jueves", 4, "viernes", 5, "sabado", 6);
  private static final Pattern HOUR_RANGE =
      Pattern.compile(
          "(\\d{1,2}):(\\d{2})\\s*h?\\s*-\\s*(\\d{1,2}):(\\d{2})\\s*h?", Pattern.CASE_INSENSITIVE);
  private static final Pattern DAY_SLOT_LABEL = Pattern.compile("\\d{1,2}:\\d{2}\\s*h?\\s*-");
  private static final Pattern NIGHT_SLOT_LABEL =
      Pattern.compile("^\\d{1,2}:\\d{2}\\s*-\\s*\\d{1,2}:\\d{2}");
  private static final Pattern NIGHT_LEVELS =
      Pattern.compile("^(PRIMERO|SEGUNDO|TERCERO|CUARTO|QUINTO)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern PLACEHOLDER =
      Pattern.compile(
          "^(asignatura|docente|aula|lista de sus profesores|-+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern SMALL_NUMBER = Pattern.compile("^\\d{1,2}$");
  /** Ancho de un bloque del horario nocturno. */
  private static final int NIGHT_STRIDE = 23;

  private ScheduleSheetParser() {}

  // ----- Diurno -----

  public static Result parseDay(SheetReader sheet) {
    Result result = new Result();

    List<Integer> levelColumns = new ArrayList<>();
    for (int column = 1; column <= sheet.columns(); column++) {
      String cell = comparable(sheet.text(3, column));
      if (cell.startsWith("nivel:")) levelColumns.add(column);
      else if (cell.startsWith("profesor:")) result.professorBlocksIgnored++;
    }
    if (result.professorBlocksIgnored > 0) {
      result.notes.add(ignoredNote(result.professorBlocksIgnored));
    }
    if (levelColumns.isEmpty()) {
      result.notes.add("No se encontró ningún bloque \"Nivel:\" en la hoja.");
      return result;
    }

    for (int levelColumn : levelColumns) {
      String levelName = sheet.text(3, levelColumn + 1);
      Integer levelOrder = ImportText.levelLabelToOrder(levelName);
      if (levelOrder == null) {
        result.skipped.add(
            new SkippedCell(
                cellRef(3, levelColumn + 1), levelName, "No se pudo deducir el número de nivel"));
      }

      // Días del bloque: se descubren por la cabecera, no por posición fija.
      Map<Integer, Integer> dayByColumn = new LinkedHashMap<>();
      for (int offset = 1; offset <= 7; offset++) {
        int column = levelColumn + offset;
        if (column > sheet.columns()) break;
        Integer day = DAY_LABELS.get(comparable(sheet.text(4, column)));
        if (day != null) dayByColumn.put(column, day);
      }
      if (dayByColumn.isEmpty()) {
        result.notes.add("El bloque de nivel \"" + levelName + "\" no tiene días reconocibles.");
        continue;
      }

      List<Slot> slots = readSlots(sheet, levelColumn, DAY_SLOT_LABEL);
      if (slots.isEmpty()) {
        result.notes.add("El bloque de nivel \"" + levelName + "\" no tiene franjas horarias.");
        continue;
      }

      // Celdas con contenido, por día.
      Map<Integer, List<DayCell>> byDay = new LinkedHashMap<>();
      for (Slot slot : slots) {
        for (Map.Entry<Integer, Integer> entry : dayByColumn.entrySet()) {
          int column = entry.getKey();
          String subject = sheet.text(slot.row(), column);
          String professor = sheet.text(slot.row() + 1, column);
          String space = sheet.text(slot.row() + 2, column);
          if (subject.isEmpty() && professor.isEmpty() && space.isEmpty()) continue;
          if (ImportText.isFiller(subject)) {
            result.skipped.add(
                new SkippedCell(
                    cellRef(slot.row(), column),
                    subject,
                    "La celda de materia está vacía o es un separador"));
            continue;
          }
          List<String> notes = new ArrayList<>();
          if (professor.isEmpty()) {
            notes.add("La franja no indica docente: se importará sin docente asignado.");
          }
          if (professor.contains("/")) {
            notes.add(
                "La celda de docente contiene más de una persona separada por «/»: revísala antes"
                    + " de publicar.");
          }
          if (space.isEmpty()) notes.add("La franja no indica aula.");
          byDay
              .computeIfAbsent(entry.getValue(), ignored -> new ArrayList<>())
              .add(
                  new DayCell(
                      slot.row(), column, slot.start(), slot.end(), subject, professor, space, notes));
        }
      }

      // Franjas consecutivas idénticas son una sola clase más larga.
      for (Map.Entry<Integer, List<DayCell>> entry : byDay.entrySet()) {
        List<DayCell> group = new ArrayList<>();
        for (DayCell cell : entry.getValue()) {
          DayCell previous = group.isEmpty() ? null : group.get(group.size() - 1);
          boolean same =
              previous != null
                  && previous.subject().equals(cell.subject())
                  && previous.professor().equals(cell.professor())
                  && previous.space().equals(cell.space())
                  && previous.end() == cell.start();
          if (!same) {
            addDayBlock(result, group, levelOrder, entry.getKey());
            group = new ArrayList<>();
          }
          group.add(cell);
        }
        addDayBlock(result, group, levelOrder, entry.getKey());
      }
    }

    if (result.blocks.isEmpty()) {
      result.notes.add("No se encontró ninguna clase con materia en los bloques por nivel.");
    }
    return result;
  }

  private static void addDayBlock(Result result, List<DayCell> group, Integer levelOrder, int day) {
    if (group.isEmpty()) return;
    DayCell first = group.get(0);
    DayCell last = group.get(group.size() - 1);
    Set<String> unique = new LinkedHashSet<>();
    for (DayCell cell : group) unique.addAll(cell.notes());
    List<String> notes = new ArrayList<>(unique);
    if (group.size() > 1) {
      notes.add(
          "El libro repite la misma clase en "
              + group.size()
              + " franjas seguidas: se importó como una sola de "
              + SheetReader.numberText((last.end() - first.start()) / 60.0)
              + " horas.");
    }
    result.blocks.add(
        new DraftBlock(
            levelOrder,
            day,
            clock(first.start()),
            clock(last.end()),
            null,
            first.subject(),
            first.professor(),
            first.space(),
            first.row(),
            first.column(),
            notes));
  }

  // ----- Nocturno -----

  public static Result parseNight(SheetReader sheet) {
    Result result = new Result();

    // Los bloques se localizan por la etiqueta "SEMANAS:" de la fila 4.
    List<Integer> blockColumns = new ArrayList<>();
    for (int column = 1; column <= sheet.columns(); column++) {
      if (comparable(sheet.text(4, column)).startsWith("semanas")) blockColumns.add(column);
    }
    if (blockColumns.isEmpty()) {
      result.notes.add("No se encontró ningún bloque \"SEMANAS:\" en la hoja nocturna.");
      return result;
    }

    Set<String> reported = new HashSet<>();
    for (int blockColumn : blockColumns) {
      String blockName = sheet.text(3, blockColumn);
      if (!NIGHT_LEVELS.matcher(blockName.strip()).matches()) {
        result.professorBlocksIgnored++; // bloque por docente
        continue;
      }
      Integer levelOrder = ImportText.levelLabelToOrder(blockName);
      int limit = Math.min(sheet.columns(), blockColumn + NIGHT_STRIDE);

      // Columna -> número de semana (fila 5).
      Map<Integer, Integer> weeks = new LinkedHashMap<>();
      for (int column = blockColumn; column <= limit; column++) {
        String value = sheet.text(5, column);
        if (SMALL_NUMBER.matcher(value).matches()) weeks.put(column, Integer.parseInt(value));
      }
      if (weeks.isEmpty()) {
        result.notes.add("El bloque \"" + blockName + "\" no tiene semanas numeradas.");
        continue;
      }

      // Columna -> módulo (fila 6). El número solo está al inicio de cada módulo: se arrastra.
      Map<Integer, Integer> modules = new LinkedHashMap<>();
      Integer current = null;
      for (int column = blockColumn; column <= limit; column++) {
        String value = sheet.text(6, column);
        if (SMALL_NUMBER.matcher(value).matches()) current = Integer.parseInt(value);
        if (current != null) modules.put(column, current);
      }

      List<Slot> slots = readSlots(sheet, blockColumn, NIGHT_SLOT_LABEL);
      if (slots.isEmpty()) {
        result.notes.add("El bloque \"" + blockName + "\" no tiene franjas horarias.");
        continue;
      }

      record ModuleClass(String subject, String professor, int column) {}
      for (Slot slot : slots) {
        // Cada módulo tiene su propia materia: se toma la primera semana de cada uno.
        Map<Integer, ModuleClass> byModule = new LinkedHashMap<>();
        for (int column : weeks.keySet()) {
          String subject = sheet.text(slot.row(), column);
          if (subject.isEmpty()) continue;
          if (PLACEHOLDER.matcher(subject).matches() || ImportText.isFiller(subject)) {
            result.placeholderCount++;
            String ref = cellRef(slot.row(), column);
            if (reported.add(ref)) {
              result.skipped.add(
                  new SkippedCell(
                      ref,
                      subject,
                      "El libro todavía no tiene la materia de esta semana: la plantilla sigue"
                          + " vacía"));
            }
            continue;
          }
          byModule.putIfAbsent(
              modules.getOrDefault(column, 0),
              new ModuleClass(subject, sheet.text(slot.row() + 1, column), column));
        }

        for (Map.Entry<Integer, ModuleClass> entry : byModule.entrySet()) {
          ModuleClass data = entry.getValue();
          List<String> notes = new ArrayList<>();
          if (data.professor().isEmpty()) {
            notes.add("La franja no indica docente: se importará sin docente asignado.");
          } else if (PLACEHOLDER.matcher(data.professor()).matches()) {
            continue; // el libro todavía no tiene el docente de esta franja
          }
          if (data.professor().contains("/")) {
            notes.add("La celda de docente contiene más de una persona separada por «/».");
          }
          notes.add(
              "El horario nocturno no distingue días: se aplicó a lunes-viernes y se repetirá en"
                  + " cada módulo.");
          result.blocks.add(
              new DraftBlock(
                  levelOrder,
                  1,
                  clock(slot.start()),
                  clock(slot.end()),
                  entry.getKey() == 0 ? null : entry.getKey(),
                  data.subject(),
                  data.professor(),
                  "",
                  slot.row(),
                  data.column(),
                  notes));
        }
      }
    }

    if (result.professorBlocksIgnored > 0) {
      result.notes.add(ignoredNote(result.professorBlocksIgnored));
    }
    if (result.placeholderCount > 0) {
      result.notes.add(
          "La hoja nocturna tiene "
              + result.placeholderCount
              + " celdas con la plantilla sin diligenciar (la palabra \"Asignatura\" donde debería"
              + " ir la materia). No se importaron.");
    }
    if (result.blocks.isEmpty()) {
      result.notes.add("La hoja nocturna no tiene ninguna clase con materia y docente reales.");
    }
    return result;
  }

  // ----- Utilidades -----

  private static String ignoredNote(int count) {
    return "Se ignoraron "
        + count
        + " bloques por docente: repiten la misma información que los bloques por nivel.";
  }

  /**
   * Franjas de un bloque. La etiqueta de hora está combinada sobre las filas de la franja, así
   * que se repite en todas ellas: se conserva la primera de cada grupo.
   */
  private static List<Slot> readSlots(SheetReader sheet, int labelColumn, Pattern labelPattern) {
    List<Slot> slots = new ArrayList<>();
    for (int row = 5; row <= sheet.rows(); row++) {
      String label = sheet.text(row, labelColumn);
      if (!labelPattern.matcher(label).find()) continue;
      int[] range = parseHourRange(label);
      if (range == null) continue;
      Slot previous = slots.isEmpty() ? null : slots.get(slots.size() - 1);
      if (previous != null && previous.start() == range[0] && previous.end() == range[1]) continue;
      slots.add(new Slot(row, range[0], range[1]));
    }
    return slots;
  }

  /** "08:00h-09:00h" -> minutos de inicio y fin, o null si no es un rango válido. */
  static int[] parseHourRange(String label) {
    Matcher match = HOUR_RANGE.matcher(label);
    if (!match.find()) return null;
    int start = Integer.parseInt(match.group(1)) * 60 + Integer.parseInt(match.group(2));
    int end = Integer.parseInt(match.group(3)) * 60 + Integer.parseInt(match.group(4));
    return end <= start ? null : new int[] {start, end};
  }

  /** Minutos -> "HH:mm:00". */
  static String clock(int minutes) {
    return String.format("%02d:%02d:00", minutes / 60, minutes % 60);
  }
}
