package ec.edu.intesud.crono.exports;

import ec.edu.intesud.crono.common.ApiException;
import ec.edu.intesud.crono.common.Db;
import ec.edu.intesud.crono.domain.AcademicPeriod;
import ec.edu.intesud.crono.domain.Career;
import ec.edu.intesud.crono.domain.Level;
import ec.edu.intesud.crono.domain.Professor;
import ec.edu.intesud.crono.domain.RecordStatus;
import ec.edu.intesud.crono.domain.Schedule;
import ec.edu.intesud.crono.domain.ScheduleBlock;
import ec.edu.intesud.crono.domain.ScheduleModule;
import ec.edu.intesud.crono.domain.ScheduleStatus;
import ec.edu.intesud.crono.domain.ScheduleVersion;
import ec.edu.intesud.crono.domain.Shift;
import ec.edu.intesud.crono.domain.Subject;
import ec.edu.intesud.crono.domain.User;
import ec.edu.intesud.crono.exports.ExportModel.Catalog;
import ec.edu.intesud.crono.exports.ExportModel.ExportBlock;
import ec.edu.intesud.crono.exports.ExportModel.ExportLevel;
import ec.edu.intesud.crono.exports.ExportModel.ExportModule;
import ec.edu.intesud.crono.exports.ExportModel.ExportSubject;
import ec.edu.intesud.crono.exports.ExportModel.Signature;
import ec.edu.intesud.crono.professors.ProfessionalTitles;
import java.text.Collator;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Genera el libro de horarios de una carrera en un periodo.
 *
 * <p>Solo se exporta lo publicado: un borrador puede tener huecos y cruces, y repartirlo es peor
 * que no repartirlo. Por eso se exige que exista al menos una versión publicada.
 */
@Service
@Transactional(readOnly = true)
public class ExportsService {

  /** Archivo listo para descargar. */
  public record ExportedWorkbook(byte[] content, String fileName) {}

  private static final Logger log = LoggerFactory.getLogger(ExportsService.class);
  private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

  private final Db db;

  public ExportsService(Db db) {
    this.db = db;
  }

  public ExportedWorkbook buildWorkbook(UUID periodId, UUID careerId) {
    AcademicPeriod period = db.find(AcademicPeriod.class, periodId);
    if (period == null) throw ApiException.notFound("El periodo académico indicado no existe.");
    Career career = db.find(Career.class, careerId);
    if (career == null) throw ApiException.notFound("La carrera indicada no existe.");

    List<Schedule> schedules =
        db.list(
            "select s from Schedule s join fetch s.course c join fetch c.level l"
                + " where c.period.id = ?1 and l.career.id = ?2 order by l.sortOrder, s.shift",
            Schedule.class,
            periodId,
            careerId);
    // De cada horario, su versión publicada más reciente.
    Map<Schedule, ScheduleVersion> published = new java.util.LinkedHashMap<>();
    List<String> unpublished = new ArrayList<>();
    for (Schedule schedule : schedules) {
      List<ScheduleVersion> versions =
          db.list(
              "select v from ScheduleVersion v where v.schedule.id = ?1 and v.status = ?2"
                  + " order by v.version desc",
              ScheduleVersion.class,
              schedule.getId(),
              ScheduleStatus.PUBLISHED);
      if (versions.isEmpty()) {
        unpublished.add(schedule.getCourse().getLevel().getName());
      } else {
        published.put(schedule, versions.get(0));
      }
    }
    if (published.isEmpty()) {
      throw ApiException.badRequest(
          "No hay ningún horario publicado para esta carrera en este periodo. Publica al menos un"
              + " horario antes de exportar.");
    }
    if (!unpublished.isEmpty()) {
      log.warn(
          "La exportación de {} omitió los horarios sin publicar: {}.",
          career.getName(),
          String.join(", ", unpublished));
    }

    ExportModel model = buildModel(period, career, published);
    String fileName =
        ("Horarios " + shortCareer(career.getName()) + " " + period.getName() + ".xlsx")
            .replaceAll("\\s+", " ")
            .strip();
    return new ExportedWorkbook(ScheduleWorkbookWriter.write(model), fileName);
  }

  private ExportModel buildModel(
      AcademicPeriod period, Career career, Map<Schedule, ScheduleVersion> published) {
    UUID careerId = career.getId();
    List<Level> levels =
        db.list(
            "select l from Level l where l.career.id = ?1 and l.status = ?2 order by l.sortOrder",
            Level.class,
            careerId,
            RecordStatus.ACTIVE);
    Map<UUID, Integer> levelOrderById =
        levels.stream().collect(Collectors.toMap(Level::getId, Level::getSortOrder));

    List<ExportBlock> dayBlocks = new ArrayList<>();
    List<ExportBlock> nightBlocks = new ArrayList<>();
    List<ExportModule> nightModules = List.of();
    for (Map.Entry<Schedule, ScheduleVersion> entry : published.entrySet()) {
      Schedule schedule = entry.getKey();
      boolean night = schedule.getShift() == Shift.NIGHT;
      Integer levelOrder = levelOrderById.get(schedule.getCourse().getLevel().getId());
      List<ScheduleBlock> blocks =
          db.list(
              "select b from ScheduleBlock b join fetch b.subject join fetch b.professor"
                  + " left join fetch b.space where b.version.id = ?1"
                  + " order by b.dayOfWeek, b.startTime",
              ScheduleBlock.class,
              entry.getValue().getId());
      for (ScheduleBlock block : blocks) {
        (night ? nightBlocks : dayBlocks)
            .add(
                new ExportBlock(
                    levelOrder,
                    night ? null : block.getDayOfWeek().ordinal() + 1,
                    HOUR.format(block.getStartTime()),
                    HOUR.format(block.getEndTime()),
                    block.getModuleNumber(),
                    block.getSubject().getName(),
                    // El color sale del docente: el mismo que se ve en la plataforma.
                    block.getProfessor().getScheduleColor(),
                    professorName(block.getProfessor()),
                    block.getSpace() == null ? null : block.getSpace().getName()));
      }
      if (night && nightModules.isEmpty()) {
        nightModules =
            db
                .list(
                    "select m from ScheduleModule m where m.schedule.id = ?1"
                        + " order by m.moduleNumber",
                    ScheduleModule.class,
                    schedule.getId())
                .stream()
                .map(m -> new ExportModule(m.getModuleNumber(), m.getStartDate(), m.getEndDate()))
                .toList();
      }
    }

    // Catálogos de asignaturas de todas las carreras activas, igual que el libro compartido.
    List<Catalog> catalogs = new ArrayList<>();
    for (Career other :
        db.list(
            "select c from Career c where c.status = ?1 order by c.name",
            Career.class,
            RecordStatus.ACTIVE)) {
      List<ExportSubject> subjects =
          db
              .list(
                  "select s from Subject s left join fetch s.level where s.career.id = ?1"
                      + " and s.status = ?2",
                  Subject.class,
                  other.getId(),
                  RecordStatus.ACTIVE)
              .stream()
              .map(
                  subject ->
                      new ExportSubject(
                          subject.getName(),
                          subject.getCode(),
                          subject.getLevel() == null ? null : subject.getLevel().getSortOrder(),
                          subject.getWeeklyHours()))
              .toList();
      catalogs.add(new Catalog(other.getName(), subjects));
    }

    List<String> professors =
        db
            .list(
                "select p from ProfessorCareer pc join pc.professor p where pc.id.careerId = ?1"
                    + " and pc.status = ?2 and p.status = ?2 order by p.lastNames, p.firstNames",
                Professor.class,
                careerId,
                RecordStatus.ACTIVE)
            .stream()
            .map(ExportsService::professorName)
            .toList();
    List<String> spaces =
        new ArrayList<>(
            db.list(
                "select sc.space.name from SpaceCareer sc where sc.id.careerId = ?1",
                String.class,
                careerId));
    spaces.sort(Collator.getInstance(Locale.forLanguageTag("es")));
    List<User> coordinators =
        db.list(
            "select u from CoordinatorCareer cc join cc.user u where cc.id.careerId = ?1"
                + " order by u.createdAt",
            User.class,
            careerId);
    String coordinator =
        coordinators.isEmpty()
            ? null
            : coordinators.get(0).getFirstNames() + " " + coordinators.get(0).getLastNames();

    return new ExportModel(
        career.getName(),
        period.getName(),
        coordinator,
        // El sistema no guarda quién revisó o aprobó: se deja en blanco para llenar al imprimir.
        List.of(
            new Signature("Revisado por:", null),
            new Signature("Aprobado por:", null),
            new Signature("Recursos Humanos:", null)),
        levels.stream().map(l -> new ExportLevel(l.getSortOrder(), l.getName())).toList(),
        catalogs,
        professors,
        spaces,
        dayBlocks,
        nightBlocks,
        nightModules);
  }

  /** "Ing. Torres Ana": abreviatura del título, apellidos y nombres. */
  private static String professorName(Professor professor) {
    return Stream.of(
            ProfessionalTitles.prefix(professor.getProfessionalTitle()),
            professor.getLastNames(),
            professor.getFirstNames())
        .filter(part -> part != null && !part.isEmpty())
        .collect(Collectors.joining(" "));
  }

  /** "Desarrollo de Software" -> "DDS", como en el nombre del archivo original. */
  static String shortCareer(String name) {
    String[] words = name.strip().split("\\s+");
    if (words.length == 1) {
      return words[0].substring(0, Math.min(12, words[0].length())).toUpperCase(Locale.ROOT);
    }
    StringBuilder initials = new StringBuilder();
    for (String word : words) initials.append(word.charAt(0));
    return initials.substring(0, Math.min(4, initials.length())).toUpperCase(Locale.ROOT);
  }
}
