package ec.edu.intesud.crono.exports;

import ec.edu.intesud.crono.common.Texts;
import ec.edu.intesud.crono.excel.ExcelBook;
import ec.edu.intesud.crono.excel.ExcelBook.Align;
import ec.edu.intesud.crono.excel.ExcelBook.Cell;
import ec.edu.intesud.crono.excel.ExcelBook.FontSpec;
import ec.edu.intesud.crono.excel.ExcelBook.Ink;
import ec.edu.intesud.crono.excel.ExcelBook.Sheet;
import ec.edu.intesud.crono.excel.ScheduleColors;
import ec.edu.intesud.crono.exports.ExportModel.Catalog;
import ec.edu.intesud.crono.exports.ExportModel.ExportBlock;
import ec.edu.intesud.crono.exports.ExportModel.ExportLevel;
import ec.edu.intesud.crono.exports.ExportModel.ExportModule;
import ec.edu.intesud.crono.exports.ExportModel.ExportSubject;
import ec.edu.intesud.crono.exports.ExportModel.Signature;
import java.text.Collator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;

/**
 * Escribe el libro "Horarios" con la misma disposición que la aplicación sabe importar, y con el
 * aspecto del libro original para poder repartirlo tal cual:
 *
 * <ul>
 *   <li><b>Inicio</b>: A1 "CARRERA" y A2 la carrera; en D los docentes, en F las aulas, en H los
 *       niveles; desde la columna 30, un bloque de seis columnas por carrera con sus asignaturas.
 *   <li><b>HorariosClasesDiurno</b>: bloques de siete columnas ("Horas" y los cinco días), uno
 *       por nivel y uno por docente. Cada franja son tres filas: asignatura, docente y aula.
 *   <li><b>HorariosClasesNocturno</b>: bloques de veintitrés columnas con las dieciocho semanas
 *       agrupadas de tres en tres por módulo. Cada franja son dos filas.
 * </ul>
 */
public final class ScheduleWorkbookWriter {

  public static final String SHEET_START = "Inicio";
  public static final String SHEET_DAY = "HorariosClasesDiurno";
  public static final String SHEET_NIGHT = "HorariosClasesNocturno";

  /** Columnas (desde la etiqueta del bloque) de las 18 semanas del horario nocturno. */
  public static final int[] NIGHT_WEEK_OFFSETS = {
    1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 12, 13, 15, 16, 17, 18, 19, 20
  };

  public static final int NIGHT_WIDTH = 23;

  // ----- Estilo -----

  private static final FontSpec LABEL = new FontSpec("Times New Roman", 12, true);
  private static final FontSpec TITLE = new FontSpec("Times New Roman", 18, false);
  private static final FontSpec SMALL_LABEL = new FontSpec("Times New Roman", 11, true);
  private static final FontSpec CONTENT = new FontSpec("Calibri", 11, false);
  private static final FontSpec CONTENT_SMALL = new FontSpec("Calibri", 10, false);
  private static final FontSpec CONTENT_MINI = new FontSpec("Calibri", 9, false);
  private static final FontSpec MINI_BOLD = new FontSpec("Calibri", 9, true);

  private static final Ink LABEL_BLUE = Ink.argb("FF467B79");
  private static final Ink WHITE = Ink.theme(0);
  private static final Ink BLACK = Ink.theme(1);
  private static final Ink LINK_BLUE = Ink.argb("FF0070C0");
  private static final Ink FRAME_GRAY = Ink.argb("FF808080");
  /** Fondo de las cabeceras del horario nocturno. */
  private static final Ink NIGHT_HEADER = Ink.theme(0, -0.25);
  /** Franja lateral con el título escrito de punta. */
  private static final Ink DAY_WALL = Ink.argb("FFDAE3F3");
  private static final Ink NIGHT_WALL = Ink.argb("FFB4BBC3");

  private static final Align WRAP_LEFT = new Align(false, true, 0);
  private static final Align WRAP_CENTER = new Align(true, true, 0);
  private static final Align VERTICAL_TITLE = new Align(true, false, 90);

  private static final List<String> DAYS =
      List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes");
  private static final String[] MONTHS = {
    "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"
  };
  private static final Collator SPANISH = Collator.getInstance(Locale.forLanguageTag("es"));

  private ScheduleWorkbookWriter() {}

  public static byte[] write(ExportModel model) {
    ExcelBook book = new ExcelBook("CRONO-INTESUD");
    writeStart(book.sheet(SHEET_START), model);
    writeDay(book.sheet(SHEET_DAY), model);
    writeNight(book.sheet(SHEET_NIGHT), model);
    return book.toBytes();
  }

  /** Rótulo de la hoja Inicio: letra blanca sobre el azul del libro. */
  private static void label(Cell cell, FontSpec font) {
    cell.font(font, Ink.argb("FFFFFFFF")).fill(LABEL_BLUE).align(Align.LEFT).border();
  }

  /** Rótulo de las hojas de horario: texto negro sin fondo. */
  private static void plainLabel(Cell cell, FontSpec font) {
    cell.font(font, BLACK).align(Align.LEFT).border();
  }

  private static void content(Cell cell, FontSpec font, Ink color, Ink fill, Align align) {
    cell.font(font, color).align(align).border();
    if (fill != null) cell.fill(fill);
  }

  // ----- Hoja Inicio -----

  private static void writeStart(Sheet sheet, ExportModel model) {
    double[] widths = {39.1, 9.7, 9.7, 39.1, 9.7, 29.4, 9.7, 11.4, 11.4};
    for (int column = 1; column <= widths.length; column++) {
      sheet.columnWidth(column, widths[column - 1]);
    }
    for (int row = 7; row <= 12; row++) sheet.rowHeight(row, 15.8);

    // Marco gris de fondo: las listas quedan como huecos claros sobre el gris.
    int reach =
        Math.max(
            12 + model.signatures().size() * 3,
            Math.max(model.professors().size() + 3, model.spaces().size() + 2));
    for (int column : new int[] {1, 2, 5, 7, 9}) {
      for (int row = 1; row <= reach; row++) sheet.cell(row, column).fill(FRAME_GRAY);
    }

    label(sheet.cell(1, 1).text("CARRERA"), SMALL_LABEL);
    // La carrera va marcada en rojo: es el campo que cambia quien llena el libro.
    sheet
        .cell(2, 1)
        .text(model.careerName().toUpperCase(Locale.ROOT))
        .font(CONTENT, WHITE)
        .fill(Ink.argb("FFFF0000"));

    List<Signature> signatures = new ArrayList<>();
    signatures.add(new Signature("Nombre del Coordinador", model.coordinator()));
    signatures.addAll(model.signatures());
    for (int index = 0; index < signatures.size(); index++) {
      Signature signature = signatures.get(index);
      int row = index == 0 ? 12 : 15 + (index - 1) * 3;
      label(sheet.cell(row, 1).text(signature.label()), SMALL_LABEL);
      content(
          sheet.cell(row + 1, 1).text(signature.name()),
          CONTENT,
          signature.name() == null ? Ink.argb("FF9C9C9C") : LINK_BLUE,
          null,
          Align.LEFT);
    }

    sheet.cell(2, 4).text("LISTA DE SUS PROFESORES");
    for (int index = 0; index < model.professors().size(); index++) {
      sheet
          .cell(index + 3, 3)
          .number(index + 1)
          .fill(LABEL_BLUE)
          .font(SMALL_LABEL, WHITE)
          .align(Align.CENTER)
          .border();
      content(
          sheet.cell(index + 3, 4).text(model.professors().get(index)),
          CONTENT,
          LINK_BLUE,
          null,
          Align.LEFT);
    }

    label(sheet.cell(1, 6).text("AULAS"), SMALL_LABEL);
    for (int index = 0; index < model.spaces().size(); index++) {
      content(
          sheet.cell(index + 2, 6).text(model.spaces().get(index)), CONTENT, BLACK, null, Align.LEFT);
    }

    label(sheet.cell(1, 8).text("NIVEL"), SMALL_LABEL);
    for (int index = 0; index < model.levels().size(); index++) {
      content(
          sheet.cell(index + 2, 8).text(model.levels().get(index).name()),
          CONTENT,
          BLACK,
          null,
          Align.LEFT);
    }

    // Catálogos: uno por carrera, seis columnas cada uno, en texto plano.
    for (int index = 0; index < model.catalogs().size(); index++) {
      Catalog catalog = model.catalogs().get(index);
      int base = 30 + index * 6;
      double[] catalogWidths = {34, 3, 9, 15, 8, 3};
      for (int offset = 0; offset < catalogWidths.length; offset++) {
        sheet.columnWidth(base + offset, catalogWidths[offset]);
      }

      content(
          sheet.cell(1, base).text(catalog.careerName().toUpperCase(Locale.ROOT)),
          CONTENT,
          BLACK,
          null,
          Align.LEFT);
      sheet.merge(1, base, 1, base + 3);
      content(
          sheet.cell(2, base).text(catalogTag(catalog.careerName())),
          CONTENT,
          BLACK,
          null,
          Align.LEFT);
      content(sheet.cell(2, base + 2).text("Créditos"), CONTENT, BLACK, null, Align.LEFT);
      content(sheet.cell(2, base + 3).text("Código"), CONTENT, BLACK, null, Align.LEFT);
      content(sheet.cell(2, base + 4).text("Nivel"), CONTENT, BLACK, null, Align.LEFT);

      List<ExportSubject> subjects = new ArrayList<>(catalog.subjects());
      subjects.sort(
          Comparator.comparingInt((ExportSubject s) -> s.levelOrder() == null ? 99 : s.levelOrder())
              .thenComparing(ExportSubject::name, SPANISH));
      for (int position = 0; position < subjects.size(); position++) {
        ExportSubject subject = subjects.get(position);
        int row = position + 4;
        content(sheet.cell(row, base).text(subject.name()), CONTENT, LINK_BLUE, null, Align.LEFT);
        content(
            sheet.cell(row, base + 2).number(subject.weeklyHours()),
            CONTENT,
            LINK_BLUE,
            null,
            Align.CENTER);
        content(
            sheet.cell(row, base + 3).text(subject.code()), CONTENT, LINK_BLUE, null, Align.LEFT);
        boolean hasLevel = subject.levelOrder() != null && subject.levelOrder() != 0;
        content(
            sheet.cell(row, base + 4).text(hasLevel ? subject.levelOrder() + "." : null),
            CONTENT,
            LINK_BLUE,
            null,
            Align.CENTER);
      }
    }
  }

  // ----- Horario diurno -----

  private record Slot(int from, int to) {}

  private static void writeDay(Sheet sheet, ExportModel model) {
    // La rejilla son las franjas de una hora que salen de las horas de inicio y fin de las clases.
    TreeSet<Integer> edges = new TreeSet<>();
    for (ExportBlock block : model.dayBlocks()) {
      edges.add(minutes(block.startTime()));
      edges.add(minutes(block.endTime()));
    }
    List<Slot> slots = new ArrayList<>();
    Integer previous = null;
    for (int mark : edges) {
      if (previous != null && mark - previous == 60) slots.add(new Slot(previous, mark));
      previous = mark;
    }

    // Una clase larga ocupa varias franjas: se anota en cada una, no solo en la de inicio.
    Map<String, ExportBlock> byLevel = new HashMap<>();
    Map<String, ExportBlock> byProfessor = new HashMap<>();
    for (ExportBlock block : model.dayBlocks()) {
      if (block.dayOfWeek() == null) continue;
      for (int mark = minutes(block.startTime()); mark < minutes(block.endTime()); mark += 60) {
        int start = mark;
        if (slots.stream().noneMatch(slot -> slot.from() == start)) continue;
        byLevel.put(block.levelOrder() + "|" + block.dayOfWeek() + "|" + mark, block);
        byProfessor.put(
            comparable(block.professor()) + "|" + block.dayOfWeek() + "|" + mark, block);
      }
    }

    final int step = 7;
    final int firstRow = 5;
    sheet.columnWidth(1, 12.7);
    sheet.columnWidth(2, 18.7);

    plainLabel(sheet.cell(2, 2).text("Carrera:"), LABEL);
    content(
        sheet.cell(2, 3).text(model.careerName().toUpperCase(Locale.ROOT)),
        CONTENT,
        BLACK,
        null,
        Align.LEFT);
    plainLabel(sheet.cell(2, 7).text("Sección Diurna"), LABEL);

    record Group(int column, String title, String name, String keyPrefix, boolean isLevel) {}
    List<Group> groups = new ArrayList<>();
    for (int index = 0; index < model.levels().size(); index++) {
      ExportLevel level = model.levels().get(index);
      groups.add(new Group(2 + index * step, "Nivel:", level.name(), level.order() + "|", true));
    }
    for (int index = 0; index < model.professors().size(); index++) {
      String name = model.professors().get(index);
      groups.add(
          new Group(
              2 + (model.levels().size() + index) * step,
              "Profesor:",
              name,
              comparable(name) + "|",
              false));
    }

    for (Group group : groups) {
      Map<String, ExportBlock> classes = group.isLevel() ? byLevel : byProfessor;
      // Un nivel sin clases conserva su bloque vacío para no correr las columnas de los demás;
      // los bloques de docentes sin clases sí se omiten.
      boolean hasClasses = classes.keySet().stream().anyMatch(k -> k.startsWith(group.keyPrefix()));
      if (!hasClasses && !group.isLevel()) continue;

      int column = group.column();
      sheet.columnWidth(column, 18.7);
      for (int day = 1; day <= 5; day++) sheet.columnWidth(column + day, 27.7);

      plainLabel(sheet.cell(3, column).text(group.title()), LABEL);
      plainLabel(sheet.cell(3, column + 1).text(group.name()), LABEL);
      plainLabel(sheet.cell(4, column).text("Horas"), LABEL);
      for (int day = 1; day <= 5; day++) {
        plainLabel(sheet.cell(4, column + day).text(DAYS.get(day - 1)), LABEL);
      }

      for (int index = 0; index < slots.size(); index++) {
        Slot slot = slots.get(index);
        int row = firstRow + index * 3;
        sheet.rowHeight(row, 60);
        sheet.rowHeight(row + 1, 21);
        sheet.rowHeight(row + 2, 21);

        sheet
            .cell(row, column)
            .text(clock(slot.from()) + "h-" + clock(slot.to()) + "h")
            .font(LABEL, BLACK)
            .align(Align.LEFT)
            .border()
            .fill(WHITE);
        sheet.merge(row, column, row + 2, column);

        for (int day = 1; day <= 5; day++) {
          ExportBlock block = classes.get(group.keyPrefix() + day + "|" + slot.from());
          // La franja entera (materia, docente y aula) lleva el color del docente, con el texto
          // claro u oscuro según el fondo. Las franjas libres van sin fondo.
          String hex = ScheduleColors.normalize(block == null ? null : block.color());
          Ink fill = hex == null ? null : Ink.hex(hex);
          Ink ink = hex == null ? Ink.argb("FF8A8A8A") : Ink.hex(ScheduleColors.readableInk(hex));
          content(
              sheet.cell(row, column + day).text(block == null ? null : block.subject()),
              CONTENT,
              ink,
              fill,
              WRAP_LEFT);
          content(
              sheet.cell(row + 1, column + day).text(block == null ? null : block.professor()),
              CONTENT_SMALL,
              ink,
              fill,
              WRAP_LEFT);
          content(
              sheet.cell(row + 2, column + day).text(block == null ? null : block.space()),
              CONTENT_SMALL,
              ink,
              fill,
              WRAP_LEFT);
        }
      }
    }

    int signaturesRow = firstRow + slots.size() * 3 + 2;
    plainLabel(sheet.cell(signaturesRow, 2).text("Elaborado por:"), SMALL_LABEL);
    plainLabel(sheet.cell(signaturesRow, 4).text("Revisado por:"), SMALL_LABEL);
    plainLabel(sheet.cell(signaturesRow, 6).text("Aprobado por:"), SMALL_LABEL);

    // Lateral izquierdo: el título del libro, escrito de punta.
    for (int row = 1; row <= signaturesRow + 2; row++) sheet.cell(row, 1).fill(DAY_WALL).border();
    sheet
        .cell(2, 1)
        .text("Horarios   de   clases   por   niveles")
        .font(new FontSpec("Times New Roman", 24, false), BLACK)
        .align(VERTICAL_TITLE);
    sheet.merge(2, 1, 19, 1);

    sheet.rowHeight(1, 21);
    sheet.rowHeight(2, 21);
    sheet.rowHeight(3, 21);
    sheet.rowHeight(4, 18);
  }

  // ----- Horario nocturno -----

  private static void writeNight(Sheet sheet, ExportModel model) {
    // Franjas: una por hora de inicio distinta, como "18:30 - 20:00".
    TreeMap<Integer, String> slotsByStart = new TreeMap<>();
    for (ExportBlock block : model.nightBlocks()) {
      slotsByStart.put(minutes(block.startTime()), block.startTime() + " - " + block.endTime());
    }
    List<Map.Entry<Integer, String>> slots = new ArrayList<>(slotsByStart.entrySet());
    // El libro tiene sitio para seis módulos de tres semanas.
    List<ExportModule> modules =
        model.nightModules().subList(0, Math.min(6, model.nightModules().size()));
    final int firstRow = 7;

    record Group(int column, String name, List<ExportBlock> blocks, boolean isLevel) {}
    List<Group> groups = new ArrayList<>();
    for (int index = 0; index < model.levels().size(); index++) {
      ExportLevel level = model.levels().get(index);
      groups.add(
          new Group(
              2 + index * NIGHT_WIDTH,
              level.name(),
              model.nightBlocks().stream()
                  .filter(b -> b.levelOrder() != null && b.levelOrder() == level.order())
                  .toList(),
              true));
    }
    for (int index = 0; index < model.professors().size(); index++) {
      String name = model.professors().get(index);
      groups.add(
          new Group(
              2 + (model.levels().size() + index) * NIGHT_WIDTH,
              name,
              model.nightBlocks().stream()
                  .filter(b -> comparable(b.professor()).equals(comparable(name)))
                  .toList(),
              false));
    }

    BiFunction<Cell, FontSpec, Cell> header =
        (cell, font) -> cell.font(font, WHITE).fill(NIGHT_HEADER).border();

    for (Group group : groups) {
      // Igual que en el diurno: los niveles vacíos se conservan y los docentes vacíos se omiten.
      if (group.blocks().isEmpty() && !group.isLevel()) continue;
      int column = group.column();
      sheet.columnWidth(column, 14.7);
      for (int offset : NIGHT_WEEK_OFFSETS) sheet.columnWidth(column + offset, 6.7);
      for (int gap : new int[] {7, 14, 21}) sheet.columnWidth(column + gap, 1);

      sheet
          .cell(2, column + 1)
          .text(model.careerName().toUpperCase(Locale.ROOT))
          .font(CONTENT.withSize(12), BLACK)
          .fill(NIGHT_HEADER)
          .border();
      sheet
          .cell(2, column + 19)
          .text("Sección Nocturna")
          .font(CONTENT.withSize(12), BLACK)
          .fill(NIGHT_HEADER)
          .border();
      header.apply(sheet.cell(3, column).text(group.name()), LABEL);
      header.apply(sheet.cell(4, column).text("SEMANAS:"), MINI_BOLD);

      for (int index = 0; index < modules.size(); index++) {
        ExportModule module = modules.get(index);
        int start = column + NIGHT_WEEK_OFFSETS[index * 3];
        // El rango de fechas cubre las tres semanas del módulo, con un tono propio.
        sheet
            .cell(4, start)
            .text(dateRange(module.startDate(), module.endDate()))
            .font(MINI_BOLD, WHITE)
            .fill(Ink.hex(ScheduleColors.MODULE_TONES.get(index % ScheduleColors.MODULE_TONES.size())))
            .border()
            .align(Align.CENTER);
        sheet.merge(4, start, 4, start + 2);

        header.apply(sheet.cell(6, start).number(module.moduleNumber()), MINI_BOLD).align(Align.CENTER);
        sheet.merge(6, start, 6, start + 2);
      }

      for (int week = 0; week < NIGHT_WEEK_OFFSETS.length; week++) {
        header
            .apply(sheet.cell(5, column + NIGHT_WEEK_OFFSETS[week]).number(week + 1), MINI_BOLD)
            .align(Align.CENTER);
      }
      header.apply(sheet.cell(5, column + 21).text(" --- "), MINI_BOLD);
      header.apply(sheet.cell(6, column).text("MÓDULOS:"), MINI_BOLD);

      Map<String, ExportBlock> byKey = new HashMap<>();
      for (ExportBlock block : group.blocks()) {
        int moduleNumber = block.moduleNumber() == null ? 1 : block.moduleNumber();
        byKey.put(minutes(block.startTime()) + "|" + moduleNumber, block);
      }

      for (int index = 0; index < slots.size(); index++) {
        int row = firstRow + index * 2;
        sheet.rowHeight(row, 47.1);
        sheet.rowHeight(row + 1, 27);

        header.apply(sheet.cell(row, column).text(slots.get(index).getValue()), MINI_BOLD);
        sheet.merge(row, column, row + 1, column);

        for (int position = 0; position < modules.size(); position++) {
          ExportBlock block = byKey.get(slots.get(index).getKey() + "|" + (position + 1));
          int start = column + NIGHT_WEEK_OFFSETS[position * 3];
          // Con clase, el color del docente; sin clase, el gris del encabezado.
          String hex = ScheduleColors.normalize(block == null ? null : block.color());
          Ink fill = hex == null ? NIGHT_HEADER : Ink.hex(hex);
          Ink ink = hex == null ? WHITE : Ink.hex(ScheduleColors.readableInk(hex));
          content(
              sheet.cell(row, start).text(block == null ? null : block.subject()),
              MINI_BOLD,
              ink,
              fill,
              WRAP_CENTER);
          content(
              sheet.cell(row + 1, start).text(block == null ? null : block.professor()),
              CONTENT_MINI,
              ink,
              fill,
              WRAP_CENTER);
          // Las tres semanas del módulo son una sola celda: el módulo dura tres semanas.
          if (block != null) {
            sheet.merge(row, start, row, start + 2);
            sheet.merge(row + 1, start, row + 1, start + 2);
          }
        }

        sheet
            .cell(row, column + 21)
            .text("Notas\nSupletarios")
            .font(CONTENT_MINI, Ink.argb("FFA6A6A6"))
            .fill(NIGHT_HEADER)
            .border();
      }
    }

    sheet.rowHeight(2, 21);
    sheet.rowHeight(3, 24);
    sheet.rowHeight(4, 27);
    sheet.rowHeight(5, 30);
    sheet.rowHeight(6, 24);
    sheet.columnWidth(1, 8.9);

    // Lateral izquierdo: mismo título que en el horario diurno.
    int lastRow = firstRow + slots.size() * 2 + 4;
    for (int row = 1; row <= lastRow; row++) sheet.cell(row, 1).fill(NIGHT_WALL).border();
    sheet
        .cell(2, 1)
        .text("Horarios  de  clases  por  niveles")
        .font(TITLE, BLACK)
        .align(VERTICAL_TITLE);
    sheet.merge(2, 1, 12, 1);
  }

  // ----- Utilidades -----

  /** "08:30" -> 510 minutos. */
  static int minutes(String value) {
    String[] parts = value.split(":");
    return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
  }

  static String clock(int minutes) {
    return String.format("%02d:%02d", minutes / 60, minutes % 60);
  }

  /** "05 oct. - 23 oct." */
  static String dateRange(LocalDate from, LocalDate to) {
    return String.format(
        "%02d %s. - %02d %s.",
        from.getDayOfMonth(),
        MONTHS[from.getMonthValue() - 1],
        to.getDayOfMonth(),
        MONTHS[to.getMonthValue() - 1]);
  }

  /** "Desarrollo de Software" -> "Asignaturas DDS:" (iniciales, hasta tres letras). */
  static String catalogTag(String careerName) {
    StringBuilder tag = new StringBuilder();
    for (String word : careerName.strip().split("\\s+")) {
      String letters = word.replaceAll("[^A-Za-zÁÉÍÓÚÑáéíóúñ]", "");
      if (letters.isEmpty()) {
        if (tag.isEmpty()) tag.append('X');
        continue;
      }
      tag.append(letters.substring(0, 1).toUpperCase(Locale.ROOT));
    }
    if (tag.isEmpty()) tag.append('X');
    return "Asignaturas " + tag.substring(0, Math.min(3, tag.length())) + ":";
  }

  /** Texto sin tildes ni mayúsculas, para comparar nombres. */
  static String comparable(String value) {
    return value == null ? "" : Texts.withoutAccents(value).toLowerCase(Locale.ROOT).strip();
  }
}
