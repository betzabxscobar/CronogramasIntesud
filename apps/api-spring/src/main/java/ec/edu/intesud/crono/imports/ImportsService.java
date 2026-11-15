package ec.edu.intesud.crono.imports;

import static ec.edu.intesud.crono.common.AuditService.data;
import static ec.edu.intesud.crono.excel.ImportText.asText;
import static ec.edu.intesud.crono.excel.ImportText.comparable;
import static ec.edu.intesud.crono.imports.BlockMatcher.PENDING_ID;

import ec.edu.intesud.crono.auth.AuthenticatedUser;
import ec.edu.intesud.crono.careers.CareerLookup.CareerOption;
import ec.edu.intesud.crono.common.ApiDates;
import ec.edu.intesud.crono.common.ApiException;
import ec.edu.intesud.crono.common.AuditService;
import ec.edu.intesud.crono.common.Db;
import ec.edu.intesud.crono.domain.AcademicCourse;
import ec.edu.intesud.crono.domain.AcademicPeriod;
import ec.edu.intesud.crono.domain.AcademicSpace;
import ec.edu.intesud.crono.domain.Career;
import ec.edu.intesud.crono.domain.DayOfWeek;
import ec.edu.intesud.crono.domain.ImportJob;
import ec.edu.intesud.crono.domain.ImportRow;
import ec.edu.intesud.crono.domain.ImportRowStatus;
import ec.edu.intesud.crono.domain.ImportStatus;
import ec.edu.intesud.crono.domain.Level;
import ec.edu.intesud.crono.domain.Professor;
import ec.edu.intesud.crono.domain.ProfessorCareer;
import ec.edu.intesud.crono.domain.ProfessorCareerId;
import ec.edu.intesud.crono.domain.RecordStatus;
import ec.edu.intesud.crono.domain.Schedule;
import ec.edu.intesud.crono.domain.ScheduleBlock;
import ec.edu.intesud.crono.domain.ScheduleModule;
import ec.edu.intesud.crono.domain.ScheduleStatus;
import ec.edu.intesud.crono.domain.ScheduleVersion;
import ec.edu.intesud.crono.domain.Shift;
import ec.edu.intesud.crono.domain.SpaceCareer;
import ec.edu.intesud.crono.domain.SpaceCareerId;
import ec.edu.intesud.crono.domain.SpaceStatus;
import ec.edu.intesud.crono.domain.SpaceType;
import ec.edu.intesud.crono.domain.Subject;
import ec.edu.intesud.crono.domain.User;
import ec.edu.intesud.crono.excel.SheetReader;
import ec.edu.intesud.crono.exports.ScheduleWorkbookWriter;
import ec.edu.intesud.crono.imports.BlockMatcher.Catalog;
import ec.edu.intesud.crono.imports.BlockMatcher.Conflict;
import ec.edu.intesud.crono.imports.BlockMatcher.Entry;
import ec.edu.intesud.crono.imports.BlockMatcher.ProfessorRef;
import ec.edu.intesud.crono.imports.BlockMatcher.Resolved;
import ec.edu.intesud.crono.imports.BlockMatcher.ResolvedKeys;
import ec.edu.intesud.crono.imports.BlockMatcher.SpaceRef;
import ec.edu.intesud.crono.imports.BlockMatcher.SubjectRef;
import ec.edu.intesud.crono.imports.ImportDtos.Applied;
import ec.edu.intesud.crono.imports.ImportDtos.BlockPreview;
import ec.edu.intesud.crono.imports.ImportDtos.CancelResult;
import ec.edu.intesud.crono.imports.ImportDtos.CommitResult;
import ec.edu.intesud.crono.imports.ImportDtos.Counts;
import ec.edu.intesud.crono.imports.ImportDtos.ImportPreview;
import ec.edu.intesud.crono.imports.ImportDtos.JobDetail;
import ec.edu.intesud.crono.imports.ImportDtos.JobListItem;
import ec.edu.intesud.crono.imports.ImportDtos.JobRow;
import ec.edu.intesud.crono.imports.ImportDtos.JobSummary;
import ec.edu.intesud.crono.imports.ImportDtos.ReviewRow;
import ec.edu.intesud.crono.imports.ImportDtos.ShiftSummary;
import ec.edu.intesud.crono.imports.ScheduleSheetParser.DraftBlock;
import ec.edu.intesud.crono.imports.StartSheetParser.DraftLevel;
import ec.edu.intesud.crono.imports.StartSheetParser.DraftProfessor;
import ec.edu.intesud.crono.imports.StartSheetParser.DraftSpace;
import ec.edu.intesud.crono.imports.StartSheetParser.DraftSubject;
import ec.edu.intesud.crono.imports.StartSheetParser.SkippedCell;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Importación del libro de horarios (.xlsx) en dos pasos:
 *
 * <ol>
 *   <li><b>Vista previa</b>: lee el libro, valida cada fila y deja el resultado en una ImportJob.
 *       No escribe nada en las tablas de dominio.
 *   <li><b>Confirmación</b>: aplica las filas válidas o con advertencias (nunca las que tienen
 *       error): niveles, materias, docentes, aulas y, al final, las clases del horario.
 * </ol>
 */
@Service
@Transactional
public class ImportsService {

  private static final Logger log = LoggerFactory.getLogger(ImportsService.class);
  private static final int MAX_FILE_BYTES = 15 * 1024 * 1024;
  private static final String NOT_FOUND = "La importación no existe.";

  /** Fila de la importación antes de guardarla. */
  private record DraftRow(
      String entity,
      String sheetName,
      int rowNumber,
      Map<String, Object> payload,
      List<String> messages,
      ImportRowStatus status) {}

  /** Clase leída del libro junto con su turno. */
  private record ShiftBlock(DraftBlock block, boolean night) {}

  private record BlockDrafts(List<DraftRow> drafts, BlockPreview summary) {}

  private final Db db;
  private final AuditService audit;
  private final ObjectMapper objectMapper;

  public ImportsService(Db db, AuditService audit, ObjectMapper objectMapper) {
    this.db = db;
    this.audit = audit;
    this.objectMapper = objectMapper;
  }

  // ------------------------------------------------------------------ vista previa

  /**
   * @param catalogColumn columna del catálogo de asignaturas a importar (opcional).
   * @param shift "ALL", "DAY" o "NIGHT": qué hojas de horario leer cuando includeSchedule.
   */
  public ImportPreview preview(
      byte[] content,
      String fileName,
      AuthenticatedUser actor,
      UUID careerId,
      Integer catalogColumn,
      boolean includeSchedule,
      UUID periodId,
      String shift) {
    if (content == null || content.length == 0) {
      throw ApiException.badRequest("No se recibió ningún archivo.");
    }
    if (content.length > MAX_FILE_BYTES) {
      throw ApiException.badRequest("El archivo supera el máximo permitido (15 MB).");
    }
    Career career = db.find(Career.class, careerId);
    if (career == null) throw ApiException.notFound("La carrera indicada no existe.");

    AcademicPeriod period = null;
    if (includeSchedule) {
      if (periodId == null) {
        throw ApiException.badRequest(
            "Para importar bloques de horario hay que elegir el periodo académico al que"
                + " pertenecen.");
      }
      period = db.find(AcademicPeriod.class, periodId);
      if (period == null) throw ApiException.notFound("El periodo académico indicado no existe.");
    }

    StartSheetParser.Result parse;
    ScheduleSheetParser.Result dayParse = null;
    ScheduleSheetParser.Result nightParse = null;
    List<String> sheets = new ArrayList<>();
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
      for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
        sheets.add(workbook.getSheetName(index));
      }
      XSSFSheet start = workbook.getSheet(ScheduleWorkbookWriter.SHEET_START);
      if (start == null) {
        throw ApiException.badRequest(
            "El libro no contiene la hoja \""
                + ScheduleWorkbookWriter.SHEET_START
                + "\". Hojas encontradas: "
                + (sheets.isEmpty() ? "ninguna" : String.join(", ", sheets))
                + ".");
      }
      parse = StartSheetParser.parse(new SheetReader(start), catalogColumn);

      if (includeSchedule) {
        if (!"NIGHT".equals(shift)) {
          XSSFSheet daySheet = workbook.getSheet(ScheduleWorkbookWriter.SHEET_DAY);
          if (daySheet == null) {
            throw ApiException.badRequest(
                "El libro no contiene la hoja \""
                    + ScheduleWorkbookWriter.SHEET_DAY
                    + "\", que hace falta para los bloques de horario.");
          }
          dayParse = ScheduleSheetParser.parseDay(new SheetReader(daySheet));
        }
        if (!"DAY".equals(shift)) {
          XSSFSheet nightSheet = workbook.getSheet(ScheduleWorkbookWriter.SHEET_NIGHT);
          if (nightSheet != null) {
            nightParse = ScheduleSheetParser.parseNight(new SheetReader(nightSheet));
          } else if ("NIGHT".equals(shift)) {
            throw ApiException.badRequest(
                "El libro no contiene la hoja \""
                    + ScheduleWorkbookWriter.SHEET_NIGHT
                    + "\" del horario nocturno.");
          }
        }
      }
    } catch (ApiException exception) {
      throw exception;
    } catch (Exception exception) {
      log.error("No se pudo leer el archivo", exception);
      throw ApiException.badRequest(
          "No se pudo leer el archivo. Verifica que sea un .xlsx válido y no esté protegido con"
              + " contraseña.");
    }

    List<DraftRow> drafts = buildDrafts(parse, careerId);
    BlockPreview blockSummary = null;
    if (dayParse != null || nightParse != null) {
      BlockDrafts built =
          buildBlockDrafts(careerId, drafts, dayParse, nightParse, period.getName());
      drafts.addAll(built.drafts());
      blockSummary = built.summary();
    }

    int validRows = count(drafts, ImportRowStatus.VALID);
    int warningRows = count(drafts, ImportRowStatus.WARNING);
    int errorRows = count(drafts, ImportRowStatus.ERROR);
    ImportStatus status =
        errorRows > 0 || validRows + warningRows == 0 ? ImportStatus.INVALID : ImportStatus.READY;

    ImportJob job = new ImportJob();
    job.setUser(db.ref(User.class, actor.id()));
    job.setCareer(career);
    job.setPeriod(periodId == null ? null : db.ref(AcademicPeriod.class, periodId));
    job.setFileName(fileName == null || fileName.isBlank() ? "libro.xlsx" : fileName);
    job.setStatus(status);
    job.setTotalRows(drafts.size());
    job.setValidRows(validRows);
    job.setWarningRows(warningRows);
    job.setErrorRows(errorRows);
    db.persist(job);
    for (DraftRow draft : drafts) {
      ImportRow row = new ImportRow();
      row.setImportJob(job);
      row.setSheetName(draft.sheetName());
      row.setRowNumber(draft.rowNumber());
      row.setStatus(draft.status());
      row.setRawData(objectMapper.writeValueAsString(draft.payload()));
      row.setMessages(
          objectMapper.writeValueAsString(
              data(
                  "entity", draft.entity(),
                  "messages", draft.messages(),
                  "skipped", parse.skipped.size(),
                  "sheets", String.join(" | ", sheets))));
      db.persist(row);
    }
    db.flush();

    List<SkippedCell> skipped = new ArrayList<>(parse.skipped);
    List<String> notes = new ArrayList<>(parse.notes);
    int blocks = 0;
    for (ScheduleSheetParser.Result schedule : new ScheduleSheetParser.Result[] {dayParse, nightParse}) {
      if (schedule == null) continue;
      skipped.addAll(schedule.skipped);
      notes.addAll(schedule.notes);
      blocks += schedule.blocks.size();
    }

    return new ImportPreview(
        new JobSummary(
            job.getId(), job.getFileName(), status, drafts.size(), validRows, warningRows, errorRows),
        sheets,
        parse.activeCareer,
        parse.catalogBlocks,
        new Counts(
            parse.subjects.size(),
            parse.professors.size(),
            parse.spaces.size(),
            parse.levels.size(),
            blocks),
        blockSummary,
        drafts.stream()
            .filter(row -> row.status() != ImportRowStatus.VALID)
            .limit(200)
            .map(
                row -> {
                  String name = asText(row.payload().get("name"));
                  return new ReviewRow(
                      row.sheetName(),
                      row.rowNumber(),
                      row.entity(),
                      row.status(),
                      name.isEmpty() ? asText(row.payload().get("label")) : name,
                      row.messages());
                })
            .toList(),
        skipped.subList(0, Math.min(200, skipped.size())),
        notes,
        status == ImportStatus.READY);
  }

  /** Convierte lo leído en la hoja Inicio en filas con estado y mensajes. */
  private List<DraftRow> buildDrafts(StartSheetParser.Result parse, UUID careerId) {
    Set<String> subjectCodes = new HashSet<>();
    Set<String> subjectNames = new HashSet<>();
    for (Subject subject : subjectsOf(careerId)) {
      String code = comparable(subject.getCode());
      if (!code.isEmpty()) subjectCodes.add(code);
      subjectNames.add(comparable(subject.getName()));
    }
    Set<String> professorNames = new HashSet<>();
    for (Professor professor : db.list("select p from Professor p", Professor.class)) {
      professorNames.add(comparable(professor.getLastNames() + " " + professor.getFirstNames()));
    }
    List<AcademicSpace> existingSpaces = db.list("select a from AcademicSpace a", AcademicSpace.class);
    Set<String> spaceCodes = new HashSet<>();
    for (AcademicSpace space : existingSpaces) spaceCodes.add(comparable(space.getCode()));

    List<DraftRow> drafts = new ArrayList<>();

    for (DraftLevel level : parse.levels) {
      drafts.add(
          new DraftRow(
              "level",
              parse.sheetName,
              level.sourceRow(),
              data("entity", "level", "order", level.order(), "name", level.name()),
              List.of(),
              ImportRowStatus.VALID));
    }

    for (DraftSubject subject : parse.subjects) {
      List<String> messages = new ArrayList<>(subject.notes());
      ImportRowStatus status = ImportRowStatus.VALID;

      if (subject.levelOrder() == null) {
        // El nivel es opcional: la materia entra sin nivel y se asigna después en Estructura.
        messages.add(
            "El libro no indica el nivel de esta materia: se importará sin nivel y podrás"
                + " asignarlo después.");
        status = ImportRowStatus.WARNING;
      }
      if (subject.code() != null) {
        if (!subjectCodes.add(comparable(subject.code()))) {
          messages.add("Ya existe una materia con este código: se actualizará.");
        }
      } else if (subjectNames.contains(comparable(subject.name()))) {
        messages.add("Ya existe una materia con este nombre: se actualizará.");
        status = ImportRowStatus.WARNING;
      }
      subjectNames.add(comparable(subject.name()));

      // El libro guarda los créditos con decimales (3,3333) porque son la equivalencia en horas.
      // El sistema solo admite horas enteras: se redondea y se avisa.
      Integer weeklyHours = null;
      if (subject.credits() != null && Double.isFinite(subject.credits())) {
        weeklyHours = (int) Math.round(subject.credits());
        if (Math.abs(weeklyHours - subject.credits()) > 0.001) {
          messages.add(
              "El libro indica "
                  + String.format(Locale.ROOT, "%.2f", subject.credits()).replace('.', ',')
                  + " créditos y se guardó como "
                  + weeklyHours
                  + (weeklyHours == 1 ? " hora." : " horas."));
          status = ImportRowStatus.WARNING;
        }
      }
      if (!messages.isEmpty()) status = ImportRowStatus.WARNING;

      drafts.add(
          new DraftRow(
              "subject",
              parse.sheetName,
              subject.sourceRow(),
              data(
                  "entity", "subject",
                  "name", subject.name(),
                  "code", subject.code(),
                  "levelOrder", subject.levelOrder(),
                  "credits", subject.credits(),
                  "weeklyHours", weeklyHours),
              messages,
              status));
    }

    for (DraftProfessor professor : parse.professors) {
      List<String> messages = new ArrayList<>(professor.notes());
      ImportRowStatus status =
          messages.isEmpty() ? ImportRowStatus.VALID : ImportRowStatus.WARNING;
      if (!professorNames.add(comparable(professor.lastNames() + " " + professor.firstNames()))) {
        messages.add("Ya existe un docente con ese nombre: se actualizará.");
      }
      String title = professor.professionalTitle();
      drafts.add(
          new DraftRow(
              "professor",
              parse.sheetName,
              professor.sourceRow(),
              data(
                  "entity", "professor",
                  "firstNames", professor.firstNames(),
                  "lastNames", professor.lastNames(),
                  "professionalTitle", title,
                  "label",
                      (title == null ? "" : title + " ")
                          + professor.lastNames()
                          + " "
                          + professor.firstNames()),
              messages,
              status));
    }

    for (DraftSpace space : parse.spaces) {
      List<String> messages = new ArrayList<>(space.notes());
      ImportRowStatus status = ImportRowStatus.VALID;

      // El libro nombra las aulas con un sufijo que la base no tiene ("Aula 3-A Laboratorio 01"
      // frente a "Aula 3-A"). Si ya existe una con el nombre más corto, es la misma: se reutiliza.
      AcademicSpace registered = sameSpace(existingSpaces, space.name());
      if (registered != null && !comparable(registered.getName()).equals(comparable(space.name()))) {
        messages.add(
            "El libro escribe «"
                + space.name()
                + "» y esa aula ya está registrada como «"
                + registered.getName()
                + "» ("
                + registered.getCode()
                + "). Se usará la existente y no se creará otra.");
        status = ImportRowStatus.WARNING;
      } else if (spaceCodes.contains(comparable(space.code()))) {
        messages.add("Ya existe un espacio con ese código: se actualizará.");
        status = ImportRowStatus.WARNING;
      }
      spaceCodes.add(comparable(space.code()));

      drafts.add(
          new DraftRow(
              "space",
              parse.sheetName,
              space.sourceRow(),
              data(
                  "entity", "space",
                  "name", space.name(),
                  "code", space.code(),
                  "type", space.type().name(),
                  // Si el aula ya existe con otro nombre, se enlaza esa en vez de crear otra.
                  "reuseName", registered == null ? null : registered.getName()),
              messages,
              status));
    }
    return drafts;
  }

  /** Valida las clases de las hojas de horario y las convierte en filas de la importación. */
  private BlockDrafts buildBlockDrafts(
      UUID careerId,
      List<DraftRow> masterDrafts,
      ScheduleSheetParser.Result dayParse,
      ScheduleSheetParser.Result nightParse,
      String periodName) {
    List<ShiftBlock> entries = new ArrayList<>();
    if (dayParse != null) dayParse.blocks.forEach(block -> entries.add(new ShiftBlock(block, false)));
    if (nightParse != null) {
      nightParse.blocks.forEach(block -> entries.add(new ShiftBlock(block, true)));
    }

    Catalog catalog = buildCatalog(careerId, masterDrafts);
    // El horario puede usar una materia que el catálogo no lista: se registra como pendiente
    // para que esas clases no bloqueen toda la importación por un detalle del catálogo.
    for (ShiftBlock entry : entries) {
      String name = entry.block().subjectName();
      String key = comparable(name);
      if (key.isEmpty() || catalog.subjects().stream().anyMatch(row -> row.key().equals(key))) {
        continue;
      }
      catalog.subjects().add(BlockMatcher.subjectEntry(PENDING_ID, name));
    }

    // Los choques se detectan por turno: en el nocturno lo que separa dos clases es el módulo.
    Map<String, ResolvedKeys> cache = new HashMap<>();
    Map<DraftBlock, List<Conflict>> conflicts = new java.util.IdentityHashMap<>();
    int conflictCount = 0;
    for (boolean night : new boolean[] {false, true}) {
      List<DraftBlock> group =
          entries.stream().filter(e -> e.night() == night).map(ShiftBlock::block).toList();
      Map<Integer, List<Conflict>> found =
          BlockMatcher.detectConflicts(
              group,
              night,
              block ->
                  cache.computeIfAbsent(
                      block.professorName() + "|" + block.spaceName(),
                      ignored -> {
                        ProfessorRef professor =
                            BlockMatcher.resolveProfessor(block.professorName(), catalog).item();
                        SpaceRef space = BlockMatcher.resolveSpace(block.spaceName(), catalog).item();
                        return new ResolvedKeys(
                            professor == null
                                ? null
                                : comparable(professor.lastNames() + " " + professor.firstNames()),
                            space == null ? null : comparable(space.name()));
                      }));
      for (Map.Entry<Integer, List<Conflict>> entry : found.entrySet()) {
        conflicts.put(group.get(entry.getKey()), entry.getValue());
        conflictCount += entry.getValue().size();
      }
    }

    List<DraftRow> drafts = new ArrayList<>();
    for (ShiftBlock entry : entries) {
      DraftBlock block = entry.block();
      List<String> messages = new ArrayList<>(block.notes());
      ImportRowStatus status = ImportRowStatus.VALID;

      Resolved<SubjectRef> subject = BlockMatcher.resolveSubject(block.subjectName(), catalog);
      if (subject.item() == null) {
        messages.add(subject.detail());
        status = ImportRowStatus.ERROR;
      } else if (PENDING_ID.equals(subject.item().id())
          && !isMasterSubject(masterDrafts, block.subjectName())) {
        messages.add(
            "La materia «"
                + block.subjectName()
                + "» se usa en el horario pero no está en el catálogo de la carrera: se creará sin"
                + " nivel para que puedas asignárselo después.");
        status = ImportRowStatus.WARNING;
      }

      Resolved<ProfessorRef> professor = BlockMatcher.resolveProfessor(block.professorName(), catalog);
      if (professor.item() == null) {
        messages.add(professor.detail());
        status = ImportRowStatus.ERROR;
      } else if (!professor.detail().isEmpty()) {
        messages.add(professor.detail());
        status = ImportRowStatus.WARNING;
      }

      if (!block.spaceName().isEmpty()) {
        Resolved<SpaceRef> space = BlockMatcher.resolveSpace(block.spaceName(), catalog);
        if (space.item() == null) {
          messages.add(space.detail());
          status = ImportRowStatus.ERROR;
        } else if (!space.detail().isEmpty()) {
          messages.add(space.detail());
          if (status != ImportRowStatus.ERROR) status = ImportRowStatus.WARNING;
        }
      }

      if (block.levelOrder() == null) {
        messages.add("El libro no indica a qué nivel pertenece esta clase.");
        status = ImportRowStatus.ERROR;
      }
      for (Conflict conflict : conflicts.getOrDefault(block, List.of())) {
        messages.add(conflict.message());
        status = ImportRowStatus.ERROR;
      }
      if (!messages.isEmpty() && status == ImportRowStatus.VALID) status = ImportRowStatus.WARNING;

      drafts.add(
          new DraftRow(
              "block",
              entry.night() ? ScheduleWorkbookWriter.SHEET_NIGHT : ScheduleWorkbookWriter.SHEET_DAY,
              block.sourceRow(),
              data(
                  "entity", "block",
                  "shift", entry.night() ? "NIGHT" : "DAY",
                  "levelOrder", block.levelOrder(),
                  "dayOfWeek", block.dayOfWeek(),
                  "startTime", block.startTime(),
                  "endTime", block.endTime(),
                  "moduleNumber", block.moduleNumber(),
                  "subjectName", block.subjectName(),
                  "professorName", block.professorName(),
                  "spaceName", block.spaceName(),
                  "label",
                      block.subjectName()
                          + " · "
                          + block.startTime().substring(0, 5)
                          + "-"
                          + block.endTime().substring(0, 5)),
              messages,
              status));
    }

    List<ShiftSummary> shifts = new ArrayList<>();
    for (boolean night : new boolean[] {false, true}) {
      List<ShiftBlock> group = entries.stream().filter(e -> e.night() == night).toList();
      if (group.isEmpty()) continue;
      Map<String, Integer> byLevel = new TreeMap<>();
      for (ShiftBlock entry : group) {
        Integer order = entry.block().levelOrder();
        byLevel.merge(order == null ? "?" : order.toString(), 1, Integer::sum);
      }
      ScheduleSheetParser.Result parse = night ? nightParse : dayParse;
      shifts.add(
          new ShiftSummary(
              night ? "NIGHT" : "DAY",
              night ? ScheduleWorkbookWriter.SHEET_NIGHT : ScheduleWorkbookWriter.SHEET_DAY,
              group.size(),
              byLevel,
              parse.professorBlocksIgnored,
              night ? parse.placeholderCount : 0,
              night
                  ? List.of(
                      "El horario nocturno del libro no tiene días: las clases se aplicaron a"
                          + " lunes-viernes y se repetirán en cada módulo.")
                  : List.of(),
              0));
    }
    return new BlockDrafts(drafts, new BlockPreview(periodName, shifts, conflictCount));
  }

  /** true si la materia viene en el catálogo de la hoja Inicio de esta misma importación. */
  private static boolean isMasterSubject(List<DraftRow> masterDrafts, String name) {
    String key = comparable(name);
    return masterDrafts.stream()
        .anyMatch(
            row ->
                row.entity().equals("subject")
                    && comparable(asText(row.payload().get("name"))).equals(key));
  }

  /**
   * Catálogo contra el que se resuelven las clases: lo que ya está en la base y lo que esta
   * misma importación va a crear. Sin lo segundo, un docente de la hoja Inicio que todavía no
   * está registrado se reportaría como inexistente.
   */
  private Catalog buildCatalog(UUID careerId, List<DraftRow> masterDrafts) {
    Catalog catalog = databaseCatalog(careerId);
    List<Entry<SpaceRef>> existingSpaces = List.copyOf(catalog.spaces());
    for (DraftRow row : masterDrafts) {
      Map<String, Object> payload = row.payload();
      switch (row.entity()) {
        case "subject" -> {
          String name = asText(payload.get("name"));
          if (!name.isEmpty()) catalog.subjects().add(BlockMatcher.subjectEntry(PENDING_ID, name));
        }
        case "professor" -> {
          String firstNames = asText(payload.get("firstNames"));
          String lastNames = asText(payload.get("lastNames"));
          if (!firstNames.isEmpty() && !lastNames.isEmpty()) {
            catalog.professors().add(BlockMatcher.professorEntry(PENDING_ID, firstNames, lastNames));
          }
        }
        case "space" -> {
          String name = asText(payload.get("name"));
          String key = comparable(name);
          // Si el aula ya existe con un nombre más corto no se agrega como pendiente: así la
          // resolución por prefijo cuenta la misma historia que la confirmación.
          boolean exists =
              existingSpaces.stream()
                  .anyMatch(
                      item ->
                          item.key().equals(key)
                              || (!item.key().isEmpty() && key.startsWith(item.key())));
          if (!name.isEmpty() && !exists) {
            catalog
                .spaces()
                .add(BlockMatcher.spaceEntry(PENDING_ID, name, asText(payload.get("code"))));
          }
        }
        default -> {}
      }
    }
    return catalog;
  }

  /** Materias de la carrera y todos los docentes y aulas registrados. */
  private Catalog databaseCatalog(UUID careerId) {
    List<Entry<SubjectRef>> subjects = new ArrayList<>();
    for (Subject subject : subjectsOf(careerId)) {
      subjects.add(BlockMatcher.subjectEntry(subject.getId().toString(), subject.getName()));
    }
    List<Entry<ProfessorRef>> professors = new ArrayList<>();
    for (Professor professor : db.list("select p from Professor p", Professor.class)) {
      professors.add(
          BlockMatcher.professorEntry(
              professor.getId().toString(), professor.getFirstNames(), professor.getLastNames()));
    }
    List<Entry<SpaceRef>> spaces = new ArrayList<>();
    for (AcademicSpace space : db.list("select a from AcademicSpace a", AcademicSpace.class)) {
      spaces.add(BlockMatcher.spaceEntry(space.getId().toString(), space.getName(), space.getCode()));
    }
    return new Catalog(subjects, professors, spaces);
  }

  private List<Subject> subjectsOf(UUID careerId) {
    return db.list("select s from Subject s where s.career.id = ?1", Subject.class, careerId);
  }

  /** El aula registrada cuyo nombre es igual al del libro o es su prefijo. */
  private static AcademicSpace sameSpace(List<AcademicSpace> spaces, String bookName) {
    String bookKey = comparable(bookName);
    for (AcademicSpace space : spaces) {
      String key = comparable(space.getName());
      if (key.equals(bookKey) || (!key.isEmpty() && bookKey.startsWith(key))) return space;
    }
    return null;
  }

  private static int count(List<DraftRow> drafts, ImportRowStatus status) {
    return (int) drafts.stream().filter(row -> row.status() == status).count();
  }

  // ------------------------------------------------------------------ confirmación

  /** Aplica las filas válidas y con advertencias de la importación. */
  public CommitResult commit(UUID jobId, AuthenticatedUser actor) {
    ImportJob job = findJobEntity(jobId);
    switch (job.getStatus()) {
      case CANCELLED -> throw ApiException.badRequest("Esta importación fue cancelada.");
      case IMPORTED -> throw ApiException.badRequest("Esta importación ya fue aplicada.");
      case INVALID ->
          throw ApiException.badRequest(
              "La importación tiene errores. Corrígelos en el archivo y vuelve a subirlo.");
      default -> {}
    }
    Career career = job.getCareer();

    // Solo se aplican filas válidas o con advertencias: un ERROR nunca se escribe. Los niveles
    // van primero porque las materias los necesitan; las clases, al final.
    List<Map<String, Object>> rows = new ArrayList<>();
    for (ImportRow row :
        db.list(
            "select r from ImportRow r where r.importJob.id = ?1 and r.status <> ?2"
                + " order by r.rowNumber",
            ImportRow.class,
            jobId,
            ImportRowStatus.ERROR)) {
      rows.add(json(row.getRawData()));
    }
    rows.sort(Comparator.comparingInt(row -> "level".equals(row.get("entity")) ? 0 : 1));

    int levels = 0;
    int subjects = 0;
    int professors = 0;
    int spaces = 0;
    List<Map<String, Object>> blockRows = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      switch (String.valueOf(row.get("entity"))) {
        case "level" -> levels += applyLevel(career, row) ? 1 : 0;
        case "subject" -> subjects += applySubject(career, row) ? 1 : 0;
        case "professor" -> professors += applyProfessor(career, row) ? 1 : 0;
        case "space" -> spaces += applySpace(career, row) ? 1 : 0;
        case "block" -> blockRows.add(row);
        default -> {}
      }
    }
    db.flush();

    int blocks = 0;
    if (!blockRows.isEmpty()) {
      if (job.getPeriod() == null) {
        throw ApiException.badRequest(
            "Esta importación trae bloques de horario pero no tiene periodo académico asociado."
                + " Vuelve a analizarla indicando el periodo.");
      }
      blocks = applyBlocks(career, job.getPeriod(), blockRows, actor);
    }

    job.setStatus(ImportStatus.IMPORTED);
    Applied applied = new Applied(levels, subjects, professors, spaces, blocks);
    audit.log(
        actor.id(),
        "IMPORT_APPLIED",
        "ImportJob",
        jobId,
        null,
        data(
            "careerId", career.getId(),
            "career", career.getName(),
            "fileName", job.getFileName(),
            "applied", applied));
    log.info("Importación {} aplicada en {}: {}", jobId, career.getName(), applied);
    return new CommitResult(jobId, career.getName(), applied);
  }

  private boolean applyLevel(Career career, Map<String, Object> row) {
    Integer order = integer(row.get("order"));
    String name = asText(row.get("name"));
    if (order == null || name.isEmpty()) return false;
    Level level = levelByOrder(career.getId(), order);
    if (level == null) {
      level = new Level();
      level.setCareer(career);
      level.setSortOrder(order);
      level.setName(name);
      db.persist(level);
    }
    level.setName(name);
    db.flush();
    return true;
  }

  private boolean applySubject(Career career, Map<String, Object> row) {
    String name = asText(row.get("name"));
    if (name.isEmpty()) return false;
    Integer order = integer(row.get("levelOrder"));
    Level level = order == null ? null : levelByOrder(career.getId(), order);
    String code = asText(row.get("code"));
    Subject subject =
        first(
            code.isEmpty()
                ? db.list(
                    "select s from Subject s where s.career.id = ?1 and lower(s.name) = lower(?2)",
                    Subject.class,
                    career.getId(),
                    name)
                : db.list(
                    "select s from Subject s where s.career.id = ?1 and lower(s.code) = lower(?2)",
                    Subject.class,
                    career.getId(),
                    code));
    boolean isNew = subject == null;
    if (isNew) {
      subject = new Subject();
      subject.setCareer(career);
    }
    subject.setName(name);
    subject.setCode(code.isEmpty() ? null : code);
    subject.setLevel(level);
    subject.setStatus(RecordStatus.ACTIVE);
    // null significa que el libro no traía la columna, no que valga 0.
    Integer weeklyHours = integer(row.get("weeklyHours"));
    if (weeklyHours != null) subject.setWeeklyHours(weeklyHours);
    if (isNew) db.persist(subject);
    db.flush();
    return true;
  }

  private boolean applyProfessor(Career career, Map<String, Object> row) {
    String firstNames = asText(row.get("firstNames"));
    String lastNames = asText(row.get("lastNames"));
    if (firstNames.isEmpty() || lastNames.isEmpty()) return false;
    String title = asText(row.get("professionalTitle"));
    Professor professor =
        first(
            db.list(
                "select p from Professor p where lower(p.firstNames) = lower(?1)"
                    + " and lower(p.lastNames) = lower(?2)",
                Professor.class,
                firstNames,
                lastNames));
    if (professor == null) {
      professor = new Professor();
      professor.setFirstNames(firstNames);
      professor.setLastNames(lastNames);
      professor.setPublicProfileEnabled(true);
      professor.setStatus(RecordStatus.ACTIVE);
      if (!title.isEmpty()) professor.setProfessionalTitle(title);
      db.persist(professor);
    }
    if (!title.isEmpty()) professor.setProfessionalTitle(title);
    professor.setStatus(RecordStatus.ACTIVE);
    db.flush();

    ProfessorCareerId linkId = new ProfessorCareerId(professor.getId(), career.getId());
    ProfessorCareer link = db.find(ProfessorCareer.class, linkId);
    if (link == null) {
      link = new ProfessorCareer();
      link.setId(linkId);
      link.setProfessor(professor);
      link.setCareer(career);
      db.persist(link);
    }
    link.setStatus(RecordStatus.ACTIVE);
    return true;
  }

  private boolean applySpace(Career career, Map<String, Object> row) {
    String name = asText(row.get("name"));
    String code = asText(row.get("code"));
    if (name.isEmpty() || code.isEmpty()) return false;
    SpaceType type =
        "LABORATORY".equals(row.get("type")) ? SpaceType.LABORATORY : SpaceType.CLASSROOM;

    // Si la vista previa detectó que el aula ya existe con otro nombre, se enlaza esa: crear una
    // copia dejaría dos aulas para el mismo lugar.
    String reuseName = asText(row.get("reuseName"));
    AcademicSpace space =
        reuseName.isEmpty()
            ? null
            : first(
                db.list(
                    "select a from AcademicSpace a where lower(a.name) = lower(?1)",
                    AcademicSpace.class,
                    reuseName));
    boolean isNew = false;
    if (space == null) {
      space =
          first(db.list("select a from AcademicSpace a where a.code = ?1", AcademicSpace.class, code));
      if (space == null) {
        isNew = true;
        space = new AcademicSpace();
        space.setCode(code);
      }
      space.setName(name);
    }
    space.setType(type);
    space.setStatus(SpaceStatus.AVAILABLE);
    if (isNew) db.persist(space);
    db.flush();

    SpaceCareerId linkId = new SpaceCareerId(space.getId(), career.getId());
    if (db.find(SpaceCareer.class, linkId) == null) {
      SpaceCareer link = new SpaceCareer();
      link.setId(linkId);
      link.setSpace(space);
      link.setCareer(career);
      db.persist(link);
    }
    return true;
  }

  /**
   * Crea las clases del horario.
   *
   * <p>Las clases cuelgan de una versión de horario, que pertenece a un curso (periodo + nivel)
   * y a un turno. Si no existen se crean; si ya hay un borrador se reutiliza, y no se inserta una
   * clase si ya hay otra igual en esa versión: subir el mismo archivo dos veces no duplica nada.
   */
  private int applyBlocks(
      Career career, AcademicPeriod period, List<Map<String, Object>> rows, AuthenticatedUser actor) {
    Catalog catalog = databaseCatalog(career.getId());
    Map<Integer, Level> levelByOrder = new HashMap<>();
    for (Level level :
        db.list("select l from Level l where l.career.id = ?1", Level.class, career.getId())) {
      levelByOrder.put(level.getSortOrder(), level);
    }

    // Cada turno y nivel es un horario distinto.
    Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
    for (Map<String, Object> row : rows) {
      String shift = "NIGHT".equals(asText(row.get("shift"))) ? "NIGHT" : "DAY";
      groups
          .computeIfAbsent(shift + ":" + integer(row.get("levelOrder")), ignored -> new ArrayList<>())
          .add(row);
    }

    int inserted = 0;
    for (Map.Entry<String, List<Map<String, Object>>> group : groups.entrySet()) {
      String[] key = group.getKey().split(":");
      Shift shift = "NIGHT".equals(key[0]) ? Shift.NIGHT : Shift.DAY;
      Level level = "null".equals(key[1]) ? null : levelByOrder.get(Integer.valueOf(key[1]));
      if (level == null) {
        log.warn("No se importaron las clases del nivel {}: la carrera no tiene ese nivel.", key[1]);
        continue;
      }

      AcademicCourse course =
          first(
              db.list(
                  "select c from AcademicCourse c where c.period.id = ?1 and c.level.id = ?2",
                  AcademicCourse.class,
                  period.getId(),
                  level.getId()));
      if (course == null) {
        course = new AcademicCourse();
        course.setPeriod(period);
        course.setLevel(level);
        course.setName(level.getName() + " " + Year.now().getValue());
        db.persist(course);
      }
      Schedule schedule =
          first(
              db.list(
                  "select s from Schedule s where s.course.id = ?1 and s.shift = ?2",
                  Schedule.class,
                  course.getId(),
                  shift));
      if (schedule == null) {
        schedule = new Schedule();
        schedule.setCourse(course);
        schedule.setShift(shift);
        db.persist(schedule);
      }
      db.flush();

      // El horario nocturno va por módulos con fecha: sin ellos las clases no tendrían vigencia.
      Map<Integer, ScheduleModule> moduleByNumber = new HashMap<>();
      if (shift == Shift.NIGHT) {
        for (ScheduleModule module :
            db.list(
                "select m from ScheduleModule m where m.schedule.id = ?1",
                ScheduleModule.class,
                schedule.getId())) {
          moduleByNumber.put(module.getModuleNumber(), module);
        }
        for (int number = 1; number <= 6; number++) {
          if (moduleByNumber.containsKey(number)) continue;
          LocalDate startDate = period.getStartDate().plusDays((number - 1) * 21L);
          ScheduleModule module = new ScheduleModule();
          module.setSchedule(schedule);
          module.setModuleNumber(number);
          module.setStartDate(startDate);
          module.setEndDate(startDate.plusDays(18));
          db.persist(module);
          moduleByNumber.put(number, module);
        }
      }

      // Se reutiliza el último borrador; si no hay, se abre una versión nueva.
      List<ScheduleVersion> versions =
          db.list(
              "select v from ScheduleVersion v where v.schedule.id = ?1 order by v.version desc",
              ScheduleVersion.class,
              schedule.getId());
      ScheduleVersion version =
          versions.stream().filter(v -> v.getStatus() == ScheduleStatus.DRAFT).findFirst().orElse(null);
      if (version == null) {
        version = new ScheduleVersion();
        version.setSchedule(schedule);
        version.setVersion(versions.isEmpty() ? 1 : versions.get(0).getVersion() + 1);
        version.setStatus(ScheduleStatus.DRAFT);
        version.setReason("Importado desde el libro de horarios");
        version.setCreatedBy(db.ref(User.class, actor.id()));
        version.setModifiedBy(db.ref(User.class, actor.id()));
        db.persist(version);
        db.flush();
      }

      Set<String> existing = new HashSet<>();
      for (ScheduleBlock block :
          db.list(
              "select b from ScheduleBlock b where b.version.id = ?1",
              ScheduleBlock.class,
              version.getId())) {
        existing.add(
            fingerprint(
                block.getSubject().getId(),
                block.getProfessor().getId(),
                block.getSpace() == null ? null : block.getSpace().getId(),
                block.getDayOfWeek(),
                block.getStartTime(),
                block.getEndTime(),
                block.getModuleNumber()));
      }

      for (Map<String, Object> row : group.getValue()) {
        String subjectName = asText(row.get("subjectName"));
        if (subjectName.isEmpty()) continue;
        ProfessorRef professor =
            BlockMatcher.resolveProfessor(asText(row.get("professorName")), catalog).item();
        if (professor == null) continue;
        Integer dayNumber = integer(row.get("dayOfWeek"));
        if (dayNumber == null || dayNumber < 1 || dayNumber > 6) continue;
        DayOfWeek dayOfWeek = DayOfWeek.values()[dayNumber - 1];

        SubjectRef subject = BlockMatcher.resolveSubject(subjectName, catalog).item();
        if (subject == null) {
          // El horario usa una materia que el catálogo no lista. La vista previa ya lo avisó;
          // aquí se crea para que el horario no quede incompleto.
          Subject created = new Subject();
          created.setCareer(career);
          created.setName(subjectName);
          created.setStatus(RecordStatus.ACTIVE);
          db.persist(created);
          db.flush();
          subject = new SubjectRef(created.getId().toString(), subjectName);
          catalog.subjects().add(BlockMatcher.subjectEntry(subject.id(), subjectName));
        }
        String spaceName = asText(row.get("spaceName"));
        SpaceRef space = spaceName.isEmpty() ? null : BlockMatcher.resolveSpace(spaceName, catalog).item();

        UUID subjectId = UUID.fromString(subject.id());
        UUID professorId = UUID.fromString(professor.id());
        UUID spaceId = space == null ? null : UUID.fromString(space.id());
        LocalTime startTime = LocalTime.parse(asText(row.get("startTime")));
        LocalTime endTime = LocalTime.parse(asText(row.get("endTime")));
        Integer moduleNumber = integer(row.get("moduleNumber"));
        if (moduleNumber != null && moduleNumber == 0) moduleNumber = null;
        if (!existing.add(
            fingerprint(
                subjectId, professorId, spaceId, dayOfWeek, startTime, endTime, moduleNumber))) {
          continue;
        }

        ScheduleModule module = moduleNumber == null ? null : moduleByNumber.get(moduleNumber);
        ScheduleBlock block = new ScheduleBlock();
        block.setVersion(version);
        block.setSubject(db.ref(Subject.class, subjectId));
        block.setProfessor(db.ref(Professor.class, professorId));
        block.setSpace(db.ref(AcademicSpace.class, spaceId));
        block.setDayOfWeek(dayOfWeek);
        block.setStartTime(startTime);
        block.setEndTime(endTime);
        block.setModuleNumber(moduleNumber);
        block.setValidFrom(module == null ? null : module.getStartDate());
        block.setValidUntil(module == null ? null : module.getEndDate());
        db.persist(block);
        inserted++;
      }
      db.flush();
    }
    return inserted;
  }

  private static String fingerprint(
      UUID subjectId,
      UUID professorId,
      UUID spaceId,
      DayOfWeek dayOfWeek,
      LocalTime startTime,
      LocalTime endTime,
      Integer moduleNumber) {
    return subjectId
        + "|"
        + professorId
        + "|"
        + (spaceId == null ? "" : spaceId)
        + "|"
        + dayOfWeek
        + "|"
        + startTime
        + "|"
        + endTime
        + "|"
        + (moduleNumber == null ? 0 : moduleNumber);
  }

  private Level levelByOrder(UUID careerId, int order) {
    return first(
        db.list(
            "select l from Level l where l.career.id = ?1 and l.sortOrder = ?2",
            Level.class,
            careerId,
            order));
  }

  // ------------------------------------------------------------------ consulta y cancelación

  /** La importación con sus primeras 500 filas. */
  @Transactional(readOnly = true)
  public JobDetail findJob(UUID jobId) {
    ImportJob job = findJobEntity(jobId);
    List<JobRow> rows =
        db
            .list(
                "select r from ImportRow r where r.importJob.id = ?1 order by r.rowNumber",
                ImportRow.class,
                jobId)
            .stream()
            .limit(500)
            .map(
                row ->
                    new JobRow(
                        row.getId(),
                        jobId,
                        row.getSheetName(),
                        row.getRowNumber(),
                        row.getStatus(),
                        json(row.getRawData()),
                        json(row.getMessages())))
            .toList();
    return new JobDetail(
        job.getId(),
        job.getUser().getId(),
        job.getCareer().getId(),
        job.getPeriod() == null ? null : job.getPeriod().getId(),
        job.getFileName(),
        job.getStatus(),
        job.getTotalRows(),
        job.getValidRows(),
        job.getWarningRows(),
        job.getErrorRows(),
        ApiDates.instant(job.getCreatedAt()),
        ApiDates.instant(job.getUpdatedAt()),
        rows);
  }

  public CancelResult cancel(UUID jobId, AuthenticatedUser actor) {
    ImportJob job = findJobEntity(jobId);
    if (job.getStatus() == ImportStatus.IMPORTED) {
      throw ApiException.badRequest("No se puede cancelar una importación ya aplicada.");
    }
    job.setStatus(ImportStatus.CANCELLED);
    audit.log(actor.id(), "IMPORT_CANCELLED", "ImportJob", jobId, null, null);
    return new CancelResult(jobId, ImportStatus.CANCELLED);
  }

  /** Las últimas 20 importaciones: todas para el administrador, las propias para los demás. */
  @Transactional(readOnly = true)
  public List<JobListItem> list(AuthenticatedUser actor) {
    List<ImportJob> jobs =
        actor.isAdmin()
            ? db.list(
                "select j from ImportJob j join fetch j.career order by j.createdAt desc",
                ImportJob.class)
            : db.list(
                "select j from ImportJob j join fetch j.career where j.user.id = ?1"
                    + " order by j.createdAt desc",
                ImportJob.class,
                actor.id());
    return jobs.stream()
        .limit(20)
        .map(
            job ->
                new JobListItem(
                    job.getId(),
                    job.getFileName(),
                    job.getStatus(),
                    job.getTotalRows(),
                    job.getValidRows(),
                    job.getWarningRows(),
                    job.getErrorRows(),
                    ApiDates.instant(job.getCreatedAt()),
                    new CareerOption(job.getCareer().getId(), job.getCareer().getName())))
        .toList();
  }

  // ------------------------------------------------------------------ utilidades

  private ImportJob findJobEntity(UUID jobId) {
    ImportJob job = db.find(ImportJob.class, jobId);
    if (job == null) throw ApiException.notFound(NOT_FOUND);
    return job;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> json(String value) {
    return objectMapper.readValue(value, Map.class);
  }

  /** Número entero guardado en el JSON de una fila; null si no lo es. */
  private static Integer integer(Object value) {
    if (!(value instanceof Number number)) return null;
    double asDouble = number.doubleValue();
    return asDouble == Math.rint(asDouble) ? (int) asDouble : null;
  }

  private static <T> T first(List<T> items) {
    return items.isEmpty() ? null : items.get(0);
  }
}
