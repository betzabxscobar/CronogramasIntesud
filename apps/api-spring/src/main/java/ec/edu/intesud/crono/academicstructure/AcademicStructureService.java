package ec.edu.intesud.crono.academicstructure;

import static ec.edu.intesud.crono.common.AuditService.data;

import ec.edu.intesud.crono.academicstructure.StructureDtos.CreateLevelRequest;
import ec.edu.intesud.crono.academicstructure.StructureDtos.CreateSubjectRequest;
import ec.edu.intesud.crono.academicstructure.StructureDtos.LevelCount;
import ec.edu.intesud.crono.academicstructure.StructureDtos.LevelItem;
import ec.edu.intesud.crono.academicstructure.StructureDtos.LevelResponse;
import ec.edu.intesud.crono.academicstructure.StructureDtos.StructureResponse;
import ec.edu.intesud.crono.academicstructure.StructureDtos.SubjectCount;
import ec.edu.intesud.crono.academicstructure.StructureDtos.SubjectItem;
import ec.edu.intesud.crono.academicstructure.StructureDtos.SubjectResponse;
import ec.edu.intesud.crono.academicstructure.StructureDtos.UpdateLevelRequest;
import ec.edu.intesud.crono.academicstructure.StructureDtos.UpdateSubjectRequest;
import ec.edu.intesud.crono.auth.AuthenticatedUser;
import ec.edu.intesud.crono.careers.CareerLookup;
import ec.edu.intesud.crono.careers.CareerLookup.CareerOption;
import ec.edu.intesud.crono.common.ApiException;
import ec.edu.intesud.crono.common.AuditService;
import ec.edu.intesud.crono.common.Db;
import ec.edu.intesud.crono.common.Texts;
import ec.edu.intesud.crono.domain.Career;
import ec.edu.intesud.crono.domain.Level;
import ec.edu.intesud.crono.domain.Subject;
import ec.edu.intesud.crono.repository.LevelRepository;
import ec.edu.intesud.crono.repository.SubjectRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AcademicStructureService {

  private final LevelRepository levels;
  private final SubjectRepository subjects;
  private final CareerLookup careerLookup;
  private final Db db;
  private final AuditService audit;

  public AcademicStructureService(
      LevelRepository levels,
      SubjectRepository subjects,
      CareerLookup careerLookup,
      Db db,
      AuditService audit) {
    this.levels = levels;
    this.subjects = subjects;
    this.careerLookup = careerLookup;
    this.db = db;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<CareerOption> listCareers(AuthenticatedUser actor) {
    return careerLookup.activeFor(actor);
  }

  /** Niveles de una carrera (por orden) con sus materias (por nombre). */
  @Transactional(readOnly = true)
  public StructureResponse detail(UUID careerId, AuthenticatedUser actor) {
    assertCareerAccess(careerId, actor);
    Career career = db.find(Career.class, careerId);

    Map<UUID, Long> blocksBySubject =
        db.countByKey(
            "select b.subject.id, count(b) from ScheduleBlock b"
                + " where b.subject.career.id = ?1 group by b.subject.id",
            careerId);
    Map<UUID, Long> coursesByLevel =
        db.countByKey(
            "select c.level.id, count(c) from AcademicCourse c"
                + " where c.level.career.id = ?1 group by c.level.id",
            careerId);
    Map<UUID, List<SubjectItem>> subjectsByLevel = new LinkedHashMap<>();
    for (Subject subject :
        db.list(
            "select s from Subject s where s.career.id = ?1 and s.level is not null order by s.name",
            Subject.class,
            careerId)) {
      subjectsByLevel
          .computeIfAbsent(subject.getLevel().getId(), key -> new ArrayList<>())
          .add(
              new SubjectItem(
                  subject.getId(),
                  subject.getName(),
                  subject.getCode(),
                  subject.getWeeklyHours(),
                  subject.isRequiresIndividualComputer(),
                  subject.getStatus(),
                  new SubjectCount(blocksBySubject.getOrDefault(subject.getId(), 0L))));
    }

    List<LevelItem> levelItems =
        db
            .list(
                "select l from Level l where l.career.id = ?1 order by l.sortOrder",
                Level.class,
                careerId)
            .stream()
            .map(
                level -> {
                  List<SubjectItem> items = subjectsByLevel.getOrDefault(level.getId(), List.of());
                  return new LevelItem(
                      level.getId(),
                      level.getName(),
                      level.getSortOrder(),
                      level.getStatus(),
                      items,
                      new LevelCount(
                          items.size(), coursesByLevel.getOrDefault(level.getId(), 0L)));
                })
            .toList();
    return new StructureResponse(career.getId(), career.getName(), levelItems);
  }

  public LevelResponse createLevel(CreateLevelRequest input, AuthenticatedUser actor) {
    assertCareerAccess(input.careerId(), actor);
    Level level = new Level();
    level.setCareer(db.ref(Career.class, input.careerId()));
    level.setName(Texts.clean(input.name()));
    level.setSortOrder(input.order());
    save(level);
    audit.log(
        actor.id(),
        "LEVEL_CREATED",
        "Level",
        level.getId(),
        null,
        levelData(level, input.careerId()));
    return LevelResponse.of(level);
  }

  public LevelResponse updateLevel(UUID id, UpdateLevelRequest input, AuthenticatedUser actor) {
    Level level = findLevel(id);
    UUID careerId = level.getCareer().getId();
    if (input.careerId() != null && !input.careerId().equals(careerId)) {
      throw ApiException.conflict("No se puede trasladar un nivel a otra carrera");
    }
    assertCareerAccess(careerId, actor);
    Map<String, Object> previous = levelData(level, careerId);

    if (input.name() != null && !input.name().isEmpty()) level.setName(Texts.clean(input.name()));
    if (input.order() != null) level.setSortOrder(input.order());
    save(level);
    audit.log(actor.id(), "LEVEL_UPDATED", "Level", id, previous, levelData(level, careerId));
    return LevelResponse.of(level);
  }

  public void removeLevel(UUID id, AuthenticatedUser actor) {
    Level level = findLevel(id);
    UUID careerId = level.getCareer().getId();
    assertCareerAccess(careerId, actor);
    long content =
        db.count("select count(s) from Subject s where s.level.id = ?1", id)
            + db.count("select count(c) from AcademicCourse c where c.level.id = ?1", id);
    if (content > 0) {
      throw ApiException.conflict("No se puede eliminar un nivel que contiene materias o cursos");
    }
    audit.log(actor.id(), "LEVEL_DELETED", "Level", id, levelData(level, careerId), null);
    levels.delete(level);
  }

  public SubjectResponse createSubject(CreateSubjectRequest input, AuthenticatedUser actor) {
    assertCareerAccess(input.careerId(), actor);
    assertLevelCareer(input.levelId(), input.careerId());

    Subject subject = new Subject();
    subject.setCareer(db.ref(Career.class, input.careerId()));
    subject.setLevel(db.ref(Level.class, input.levelId()));
    subject.setName(Texts.clean(input.name()));
    subject.setCode(normalizeCode(input.code()));
    subject.setWeeklyHours(input.weeklyHours());
    subject.setRequiresIndividualComputer(Boolean.TRUE.equals(input.requiresIndividualComputer()));
    save(subject);
    audit.log(
        actor.id(),
        "SUBJECT_CREATED",
        "Subject",
        subject.getId(),
        null,
        data(
            "careerId", input.careerId(),
            "levelId", input.levelId(),
            "name", subject.getName(),
            "code", subject.getCode()));
    return SubjectResponse.of(subject);
  }

  public SubjectResponse updateSubject(
      UUID id, UpdateSubjectRequest input, AuthenticatedUser actor) {
    Subject subject = findSubject(id);
    UUID careerId = subject.getCareer().getId();
    if (input.careerId() != null && !input.careerId().equals(careerId)) {
      throw ApiException.conflict("No se puede trasladar una materia a otra carrera");
    }
    UUID currentLevelId = subject.getLevel() == null ? null : subject.getLevel().getId();
    UUID levelId = input.levelId() != null ? input.levelId() : currentLevelId;
    if (levelId == null) throw ApiException.conflict("La materia debe pertenecer a un nivel");
    assertCareerAccess(careerId, actor);
    assertLevelCareer(levelId, careerId);
    Map<String, Object> previous =
        data("name", subject.getName(), "code", subject.getCode(), "levelId", currentLevelId);

    subject.setLevel(db.ref(Level.class, levelId));
    if (input.name() != null && !input.name().isEmpty()) {
      subject.setName(Texts.clean(input.name()));
    }
    if (input.code() != null) subject.setCode(normalizeCode(input.code()));
    if (input.weeklyHours() != null) subject.setWeeklyHours(input.weeklyHours());
    if (input.requiresIndividualComputer() != null) {
      subject.setRequiresIndividualComputer(input.requiresIndividualComputer());
    }
    save(subject);
    audit.log(
        actor.id(),
        "SUBJECT_UPDATED",
        "Subject",
        id,
        previous,
        data("name", subject.getName(), "code", subject.getCode(), "levelId", levelId));
    return SubjectResponse.of(subject);
  }

  public void removeSubject(UUID id, AuthenticatedUser actor) {
    Subject subject = findSubject(id);
    assertCareerAccess(subject.getCareer().getId(), actor);
    if (db.exists("select count(b) from ScheduleBlock b where b.subject.id = ?1", id)) {
      throw ApiException.conflict("No se puede eliminar una materia que ya tiene horarios");
    }
    audit.log(
        actor.id(),
        "SUBJECT_DELETED",
        "Subject",
        id,
        data(
            "name", subject.getName(),
            "code", subject.getCode(),
            "levelId", subject.getLevel() == null ? null : subject.getLevel().getId()),
        null);
    subjects.delete(subject);
  }

  /** Un coordinador solo administra sus carreras, y la carrera debe estar activa. */
  private void assertCareerAccess(UUID careerId, AuthenticatedUser actor) {
    if (!actor.canAccessCareer(careerId)) {
      throw ApiException.forbidden("No puedes administrar esta carrera");
    }
    if (!careerLookup.isActive(careerId)) {
      throw ApiException.notFound("Carrera no encontrada o inactiva");
    }
  }

  private void assertLevelCareer(UUID levelId, UUID careerId) {
    if (!db.exists(
        "select count(l) from Level l where l.id = ?1 and l.career.id = ?2", levelId, careerId)) {
      throw ApiException.conflict("El nivel no pertenece a la carrera seleccionada");
    }
  }

  private Level findLevel(UUID id) {
    return levels.findById(id).orElseThrow(() -> ApiException.notFound("Nivel no encontrado"));
  }

  private Subject findSubject(UUID id) {
    return subjects.findById(id).orElseThrow(() -> ApiException.notFound("Materia no encontrada"));
  }

  private static String normalizeCode(String code) {
    String trimmed = Texts.trimToNull(code);
    return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
  }

  private static Map<String, Object> levelData(Level level, UUID careerId) {
    return data("careerId", careerId, "name", level.getName(), "order", level.getSortOrder());
  }

  private void save(Level level) {
    try {
      levels.saveAndFlush(level);
    } catch (DataIntegrityViolationException exception) {
      throw ApiException.conflict("Ya existe un nivel con ese nombre u orden en la carrera");
    }
  }

  private void save(Subject subject) {
    try {
      subjects.saveAndFlush(subject);
    } catch (DataIntegrityViolationException exception) {
      throw ApiException.conflict("El código de la materia ya está registrado en esta carrera");
    }
  }
}
