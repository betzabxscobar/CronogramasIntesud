package ec.edu.intesud.crono.schedules;

import static ec.edu.intesud.crono.common.AuditService.data;

import ec.edu.intesud.crono.auth.AuthenticatedUser;
import ec.edu.intesud.crono.careers.CareerLookup.CareerOption;
import ec.edu.intesud.crono.common.ApiDates;
import ec.edu.intesud.crono.common.ApiException;
import ec.edu.intesud.crono.common.AuditService;
import ec.edu.intesud.crono.common.Db;
import ec.edu.intesud.crono.domain.AcademicCourse;
import ec.edu.intesud.crono.domain.AcademicPeriod;
import ec.edu.intesud.crono.domain.AcademicPeriodStatus;
import ec.edu.intesud.crono.domain.AcademicSpace;
import ec.edu.intesud.crono.domain.Career;
import ec.edu.intesud.crono.domain.DayOfWeek;
import ec.edu.intesud.crono.domain.Level;
import ec.edu.intesud.crono.domain.Professor;
import ec.edu.intesud.crono.domain.RecordStatus;
import ec.edu.intesud.crono.domain.Schedule;
import ec.edu.intesud.crono.domain.ScheduleBlock;
import ec.edu.intesud.crono.domain.ScheduleModule;
import ec.edu.intesud.crono.domain.ScheduleStatus;
import ec.edu.intesud.crono.domain.ScheduleVersion;
import ec.edu.intesud.crono.domain.Shift;
import ec.edu.intesud.crono.domain.SpaceStatus;
import ec.edu.intesud.crono.domain.Subject;
import ec.edu.intesud.crono.domain.User;
import ec.edu.intesud.crono.professors.ProfessionalTitles;
import ec.edu.intesud.crono.schedules.ScheduleDtos.BlockCount;
import ec.edu.intesud.crono.schedules.ScheduleDtos.BlockResponse;
import ec.edu.intesud.crono.schedules.ScheduleDtos.BlockSpace;
import ec.edu.intesud.crono.schedules.ScheduleDtos.BlockSubject;
import ec.edu.intesud.crono.schedules.ScheduleDtos.CatalogSpace;
import ec.edu.intesud.crono.schedules.ScheduleDtos.CatalogSubject;
import ec.edu.intesud.crono.schedules.ScheduleDtos.Catalogs;
import ec.edu.intesud.crono.schedules.ScheduleDtos.ContextCourse;
import ec.edu.intesud.crono.schedules.ScheduleDtos.ContextPeriod;
import ec.edu.intesud.crono.schedules.ScheduleDtos.CourseInfo;
import ec.edu.intesud.crono.schedules.ScheduleDtos.CreateScheduleRequest;
import ec.edu.intesud.crono.schedules.ScheduleDtos.CreatedSchedule;
import ec.edu.intesud.crono.schedules.ScheduleDtos.LevelInfo;
import ec.edu.intesud.crono.schedules.ScheduleDtos.ListCourse;
import ec.edu.intesud.crono.schedules.ScheduleDtos.ListLevel;
import ec.edu.intesud.crono.schedules.ScheduleDtos.ListVersion;
import ec.edu.intesud.crono.schedules.ScheduleDtos.ModuleResponse;
import ec.edu.intesud.crono.schedules.ScheduleDtos.PeriodInfo;
import ec.edu.intesud.crono.schedules.ScheduleDtos.ProfessorInfo;
import ec.edu.intesud.crono.schedules.ScheduleDtos.PublishResult;
import ec.edu.intesud.crono.schedules.ScheduleDtos.PublishedVersion;
import ec.edu.intesud.crono.schedules.ScheduleDtos.SaveBlockRequest;
import ec.edu.intesud.crono.schedules.ScheduleDtos.ScheduleDetail;
import ec.edu.intesud.crono.schedules.ScheduleDtos.ScheduleListItem;
import ec.edu.intesud.crono.schedules.ScheduleDtos.UpdateModuleRequest;
import ec.edu.intesud.crono.schedules.ScheduleDtos.VersionDetail;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Horarios por curso y jornada. Cada horario tiene versiones: solo se edita el borrador; al
 * publicarlo queda congelado y se abre un borrador nuevo con las mismas clases.
 */
@Service
@Transactional
public class SchedulesService {

  private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
  private static final List<ScheduleStatus> LIVE =
      List.of(ScheduleStatus.DRAFT, ScheduleStatus.PUBLISHED);

  /** Horario con su borrador editable. */
  private record BlockContext(Schedule schedule, ScheduleVersion version) {}

  private final Db db;
  private final AuditService audit;

  public SchedulesService(Db db, AuditService audit) {
    this.db = db;
    this.audit = audit;
  }

  /** Periodos abiertos con los cursos en los que el usuario puede armar horarios. */
  @Transactional(readOnly = true)
  public List<ContextPeriod> context(AuthenticatedUser actor) {
    List<AcademicPeriod> periods =
        db.list(
            "select p from AcademicPeriod p where p.status in ?1 order by p.startDate desc",
            AcademicPeriod.class,
            List.of(AcademicPeriodStatus.ACTIVE, AcademicPeriodStatus.DRAFT));
    List<ContextPeriod> result = new ArrayList<>();
    for (AcademicPeriod period : periods) {
      List<AcademicCourse> courses;
      if (actor.isAdmin()) {
        courses =
            db.list(
                "select c from AcademicCourse c join fetch c.level l join fetch l.career k"
                    + " where c.period.id = ?1 and c.status = ?2 order by k.name, l.sortOrder",
                AcademicCourse.class,
                period.getId(),
                RecordStatus.ACTIVE);
      } else if (actor.careerIds().isEmpty()) {
        courses = List.of();
      } else {
        courses =
            db.list(
                "select c from AcademicCourse c join fetch c.level l join fetch l.career k"
                    + " where c.period.id = ?1 and c.status = ?2 and k.id in ?3"
                    + " order by k.name, l.sortOrder",
                AcademicCourse.class,
                period.getId(),
                RecordStatus.ACTIVE,
                actor.careerIds());
      }
      result.add(
          new ContextPeriod(
              period.getId(),
              period.getName(),
              period.getStatus(),
              courses.stream()
                  .map(
                      course ->
                          new ContextCourse(
                              course.getId(),
                              course.getName(),
                              course.getStudentCount(),
                              levelInfo(course.getLevel())))
                  .toList()));
    }
    return result;
  }

  @Transactional(readOnly = true)
  public List<ScheduleListItem> list(UUID periodId, AuthenticatedUser actor) {
    String select =
        "select s from Schedule s join fetch s.course c join fetch c.level l join fetch l.career k"
            + " where c.period.id = ?1";
    String order = " order by k.name, l.sortOrder, s.shift";
    List<Schedule> schedules;
    if (actor.isAdmin()) {
      schedules = db.list(select + order, Schedule.class, periodId);
    } else if (actor.careerIds().isEmpty()) {
      schedules = List.of();
    } else {
      schedules =
          db.list(select + " and k.id in ?2" + order, Schedule.class, periodId, actor.careerIds());
    }
    return schedules.stream()
        .map(
            schedule -> {
              AcademicCourse course = schedule.getCourse();
              Level level = course.getLevel();
              ScheduleVersion latest = latestVersion(schedule.getId(), null);
              List<ListVersion> versions =
                  latest == null
                      ? List.of()
                      : List.of(
                          new ListVersion(
                              latest.getId(),
                              latest.getVersion(),
                              latest.getStatus(),
                              ApiDates.instant(latest.getUpdatedAt()),
                              new BlockCount(
                                  db.count(
                                      "select count(b) from ScheduleBlock b where b.version.id = ?1",
                                      latest.getId()))));
              return new ScheduleListItem(
                  schedule.getId(),
                  schedule.getShift(),
                  ApiDates.instant(schedule.getUpdatedAt()),
                  new ListCourse(
                      course.getId(),
                      course.getName(),
                      new ListLevel(level.getName(), level.getSortOrder(), career(level))),
                  versions);
            })
        .toList();
  }

  /** Todo lo que necesita la pantalla del horario: clases, catálogos y estado de publicación. */
  @Transactional(readOnly = true)
  public ScheduleDetail detail(UUID id, AuthenticatedUser actor) {
    Schedule schedule = findSchedule(id, actor);
    AcademicCourse course = schedule.getCourse();
    Level level = course.getLevel();
    UUID careerId = level.getCareer().getId();

    ScheduleVersion latest = latestVersion(id, null);
    VersionDetail version = null;
    if (latest != null) {
      List<BlockResponse> blocks =
          db
              .list(
                  "select b from ScheduleBlock b join fetch b.subject join fetch b.professor"
                      + " left join fetch b.space where b.version.id = ?1"
                      + " order by b.dayOfWeek, b.startTime",
                  ScheduleBlock.class,
                  latest.getId())
              .stream()
              .map(block -> blockResponse(block, true))
              .toList();
      version = new VersionDetail(latest.getId(), latest.getVersion(), latest.getStatus(), blocks);
    }

    List<CatalogSubject> subjects =
        db
            .list(
                "select s from Subject s where s.career.id = ?1 and s.level.id = ?2"
                    + " and s.status = ?3 order by s.name",
                Subject.class,
                careerId,
                level.getId(),
                RecordStatus.ACTIVE)
            .stream()
            .map(
                subject ->
                    new CatalogSubject(subject.getId(), subject.getName(), subject.getWeeklyHours()))
            .toList();
    List<ProfessorInfo> professors =
        db
            .list(
                "select p from Professor p where p.status = ?1 and exists (select 1 from"
                    + " ProfessorCareer pc where pc.id.professorId = p.id and pc.id.careerId = ?2"
                    + " and pc.status = ?1) order by p.lastNames, p.firstNames",
                Professor.class,
                RecordStatus.ACTIVE,
                careerId)
            .stream()
            .map(SchedulesService::professorInfo)
            .toList();
    List<CatalogSpace> spaces =
        db
            .list(
                "select a from AcademicSpace a where a.status = ?1 and exists (select 1 from"
                    + " SpaceCareer sc where sc.id.spaceId = a.id and sc.id.careerId = ?2)"
                    + " order by a.type, a.name",
                AcademicSpace.class,
                SpaceStatus.AVAILABLE,
                careerId)
            .stream()
            .map(
                space ->
                    new CatalogSpace(
                        space.getId(),
                        space.getName(),
                        space.getCode(),
                        space.getLocation(),
                        space.getType(),
                        space.getPeopleCapacity()))
            .toList();

    List<ModuleResponse> modules =
        schedule.getShift() == Shift.NIGHT
            ? db
                .list(
                    "select m from ScheduleModule m where m.schedule.id = ?1 order by m.moduleNumber",
                    ScheduleModule.class,
                    id)
                .stream()
                .map(SchedulesService::moduleResponse)
                .toList()
            : List.of();

    // La versión publicada es la que se exporta; se informa aparte para saber si ya se puede
    // descargar el horario.
    ScheduleVersion published = latestVersion(id, ScheduleStatus.PUBLISHED);
    AcademicPeriod period = course.getPeriod();
    return new ScheduleDetail(
        schedule.getId(),
        schedule.getShift(),
        new CourseInfo(
            course.getId(),
            course.getName(),
            course.getStudentCount(),
            new PeriodInfo(
                period.getId(),
                period.getName(),
                ApiDates.date(period.getStartDate()),
                ApiDates.date(period.getEndDate())),
            levelInfo(level)),
        modules,
        published == null
            ? null
            : new PublishedVersion(
                published.getVersion(), ApiDates.instant(published.getPublishedAt())),
        version,
        new Catalogs(subjects, professors, spaces),
        List.of(DayOfWeek.values()));
  }

  /** Abre el horario de un curso y jornada (o devuelve el que ya existe) con su borrador. */
  public CreatedSchedule create(CreateScheduleRequest input, AuthenticatedUser actor) {
    AcademicCourse course = db.find(AcademicCourse.class, input.courseId());
    if (course == null) throw ApiException.notFound("Curso no encontrado");
    assertCareer(course.getLevel().getCareer().getId(), actor);

    Schedule schedule =
        first(
            db.list(
                "select s from Schedule s where s.course.id = ?1 and s.shift = ?2",
                Schedule.class,
                input.courseId(),
                input.shift()));
    if (schedule == null) {
      schedule = new Schedule();
      schedule.setCourse(course);
      schedule.setShift(input.shift());
      db.persist(schedule);
      db.flush();
    }
    if (input.shift() == Shift.NIGHT) {
      ensureNightModules(schedule, course.getPeriod().getStartDate());
    }

    ScheduleVersion version = latestVersion(schedule.getId(), ScheduleStatus.DRAFT);
    if (version == null) {
      ScheduleVersion latest = latestVersion(schedule.getId(), null);
      version = newDraft(schedule, latest == null ? 1 : latest.getVersion() + 1, null, actor.id());
      audit.log(
          actor.id(),
          "SCHEDULE_DRAFT_CREATED",
          "Schedule",
          schedule.getId(),
          null,
          data(
              "courseId", input.courseId(),
              "shift", input.shift(),
              "version", version.getVersion()));
    }
    return new CreatedSchedule(schedule.getId(), version.getId());
  }

  public BlockResponse addBlock(UUID scheduleId, SaveBlockRequest input, AuthenticatedUser actor) {
    BlockContext context = blockContext(scheduleId, actor);
    validateBlock(context, input, null);

    ScheduleBlock block = new ScheduleBlock();
    block.setVersion(context.version());
    applyBlock(block, context, input);
    db.persist(block);
    touch(context.version(), actor.id());
    db.flush();

    audit.log(
        actor.id(),
        "SCHEDULE_BLOCK_CREATED",
        "ScheduleBlock",
        block.getId(),
        null,
        blockAudit(scheduleId, input));
    return blockResponse(block, false);
  }

  public BlockResponse updateBlock(
      UUID scheduleId, UUID blockId, SaveBlockRequest input, AuthenticatedUser actor) {
    BlockContext context = blockContext(scheduleId, actor);
    ScheduleBlock block = findBlock(blockId, context);
    validateBlock(context, input, blockId);
    var previous =
        data(
            "subjectId", block.getSubject().getId(),
            "professorId", block.getProfessor().getId(),
            "spaceId", block.getSpace() == null ? null : block.getSpace().getId(),
            "dayOfWeek", block.getDayOfWeek());

    applyBlock(block, context, input);
    touch(context.version(), actor.id());
    db.flush();

    var next = blockAudit(scheduleId, input);
    next.remove("scheduleId");
    audit.log(actor.id(), "SCHEDULE_BLOCK_UPDATED", "ScheduleBlock", blockId, previous, next);
    return blockResponse(block, false);
  }

  public void removeBlock(UUID scheduleId, UUID blockId, AuthenticatedUser actor) {
    BlockContext context = blockContext(scheduleId, actor);
    ScheduleBlock block = findBlock(blockId, context);
    audit.log(
        actor.id(),
        "SCHEDULE_BLOCK_DELETED",
        "ScheduleBlock",
        blockId,
        data(
            "scheduleId", scheduleId,
            "subjectId", block.getSubject().getId(),
            "professorId", block.getProfessor().getId(),
            "spaceId", block.getSpace() == null ? null : block.getSpace().getId()),
        null);
    db.remove(block);
  }

  /** Solo se puede eliminar un horario que nunca se publicó. */
  public void remove(UUID scheduleId, AuthenticatedUser actor) {
    Schedule schedule = findSchedule(scheduleId, actor);
    if (db.exists(
        "select count(v) from ScheduleVersion v where v.schedule.id = ?1 and v.status <> ?2",
        scheduleId,
        ScheduleStatus.DRAFT)) {
      throw ApiException.conflict(
          "No se puede eliminar porque el horario ya fue publicado o cerrado");
    }
    Level level = schedule.getCourse().getLevel();
    audit.log(
        actor.id(),
        "SCHEDULE_DRAFT_DELETED",
        "Schedule",
        scheduleId,
        data(
            "career", level.getCareer().getName(),
            "level", level.getName(),
            "shift", schedule.getShift()),
        null);
    db.flush();
    // Versiones, clases y módulos se borran en cascada en la base de datos.
    db.execute("delete from Schedule s where s.id = ?1", scheduleId);
  }

  /**
   * Publica el borrador actual y abre el siguiente con las mismas clases.
   *
   * <p>Publicar no deja al usuario sin horario: se congela la versión que se reparte y se abre la
   * siguiente como borrador, ya que el sistema solo edita borradores.
   */
  public PublishResult publish(UUID scheduleId, AuthenticatedUser actor) {
    Schedule schedule = findSchedule(scheduleId, actor);
    ScheduleVersion draft = latestVersion(scheduleId, ScheduleStatus.DRAFT);
    if (draft == null) {
      throw ApiException.conflict("El horario no tiene un borrador para publicar");
    }
    List<ScheduleBlock> blocks =
        db.list(
            "select b from ScheduleBlock b where b.version.id = ?1",
            ScheduleBlock.class,
            draft.getId());
    if (blocks.isEmpty()) {
      throw ApiException.conflict("No se puede publicar un horario sin clases");
    }

    Instant now = Instant.now();
    // Solo puede haber una versión vigente por horario.
    db.execute(
        "update ScheduleVersion v set v.status = ?1, v.unpublishedAt = ?2, v.updatedAt = ?2"
            + " where v.schedule.id = ?3 and v.status = ?4",
        ScheduleStatus.UNPUBLISHED,
        now,
        scheduleId,
        ScheduleStatus.PUBLISHED);
    draft.setStatus(ScheduleStatus.PUBLISHED);
    draft.setPublishedBy(db.ref(User.class, actor.id()));
    draft.setPublishedAt(now);
    draft.setReason("Publicado desde el horario");
    db.flush();

    ScheduleVersion next =
        newDraft(
            schedule, draft.getVersion() + 1, "Borrador abierto tras la publicación", actor.id());
    for (ScheduleBlock block : blocks) {
      ScheduleBlock copy = new ScheduleBlock();
      copy.setVersion(next);
      copy.setSubject(block.getSubject());
      copy.setProfessor(block.getProfessor());
      copy.setSpace(block.getSpace());
      copy.setDayOfWeek(block.getDayOfWeek());
      copy.setStartTime(block.getStartTime());
      copy.setEndTime(block.getEndTime());
      copy.setValidFrom(block.getValidFrom());
      copy.setValidUntil(block.getValidUntil());
      copy.setModuleNumber(block.getModuleNumber());
      copy.setNotes(block.getNotes());
      db.persist(copy);
    }

    Level level = schedule.getCourse().getLevel();
    audit.log(
        actor.id(),
        "SCHEDULE_PUBLISHED",
        "Schedule",
        scheduleId,
        null,
        data(
            "career", level.getCareer().getName(),
            "level", level.getName(),
            "shift", schedule.getShift(),
            "publishedVersion", draft.getVersion(),
            "blocks", blocks.size(),
            "newDraftVersion", next.getVersion()));
    return new PublishResult(draft.getVersion(), next.getVersion(), blocks.size());
  }

  /** Cambia las fechas de un módulo nocturno y las de las clases del borrador en ese módulo. */
  public ModuleResponse updateModule(
      UUID scheduleId, String moduleNumberText, UpdateModuleRequest input, AuthenticatedUser actor) {
    Schedule schedule = findSchedule(scheduleId, actor);
    if (schedule.getShift() != Shift.NIGHT) {
      throw ApiException.conflict("Los módulos corresponden únicamente a horarios nocturnos");
    }
    int moduleNumber = parseModuleNumber(moduleNumberText);
    if (input.endDate().isBefore(input.startDate())) {
      throw ApiException.conflict("La fecha final debe ser igual o posterior a la fecha inicial");
    }
    ScheduleModule module = findModule(scheduleId, moduleNumber);
    if (module == null) throw ApiException.notFound("Módulo no encontrado");
    module.setStartDate(input.startDate());
    module.setEndDate(input.endDate());
    db.flush();

    ScheduleVersion draft = latestVersion(scheduleId, ScheduleStatus.DRAFT);
    if (draft != null) {
      db.execute(
          "update ScheduleBlock b set b.validFrom = ?1, b.validUntil = ?2, b.updatedAt = ?3"
              + " where b.version.id = ?4 and b.moduleNumber = ?5",
          input.startDate(),
          input.endDate(),
          Instant.now(),
          draft.getId(),
          moduleNumber);
    }
    audit.log(
        actor.id(),
        "SCHEDULE_MODULE_DATES_UPDATED",
        "ScheduleModule",
        module.getId(),
        null,
        data(
            "scheduleId", scheduleId,
            "moduleNumber", moduleNumber,
            "startDate", input.startDate().toString(),
            "endDate", input.endDate().toString()));
    return moduleResponse(module);
  }

  // ----- Validación de una clase -----

  private void validateBlock(BlockContext context, SaveBlockRequest input, UUID excludeId) {
    LocalTime startTime = LocalTime.parse(input.startTime());
    LocalTime endTime = LocalTime.parse(input.endTime());
    if (!endTime.isAfter(startTime)) {
      throw ApiException.conflict("La hora de finalización debe ser posterior a la hora de inicio");
    }
    Schedule schedule = context.schedule();
    AcademicCourse course = schedule.getCourse();
    Level level = course.getLevel();
    UUID careerId = level.getCareer().getId();
    boolean night = schedule.getShift() == Shift.NIGHT;

    ScheduleModule module =
        night && input.moduleNumber() != null
            ? findModule(schedule.getId(), input.moduleNumber())
            : null;
    if (night && module == null) {
      throw ApiException.conflict("Selecciona un módulo nocturno válido");
    }

    if (!db.exists(
        "select count(s) from Subject s where s.id = ?1 and s.career.id = ?2 and s.level.id = ?3"
            + " and s.status = ?4",
        input.subjectId(),
        careerId,
        level.getId(),
        RecordStatus.ACTIVE)) {
      throw ApiException.conflict("La materia no pertenece al nivel seleccionado");
    }
    if (!db.exists(
        "select count(p) from Professor p where p.id = ?1 and p.status = ?2 and exists (select 1"
            + " from ProfessorCareer pc where pc.id.professorId = p.id and pc.id.careerId = ?3"
            + " and pc.status = ?2)",
        input.professorId(),
        RecordStatus.ACTIVE,
        careerId)) {
      throw ApiException.conflict("El profesor no está vinculado con esta carrera");
    }
    AcademicSpace space =
        input.spaceId() == null
            ? null
            : first(
                db.list(
                    "select a from AcademicSpace a where a.id = ?1 and a.status = ?2 and exists"
                        + " (select 1 from SpaceCareer sc where sc.id.spaceId = a.id"
                        + " and sc.id.careerId = ?3)",
                    AcademicSpace.class,
                    input.spaceId(),
                    SpaceStatus.AVAILABLE,
                    careerId));
    if (!night && space == null) {
      throw ApiException.conflict("El aula o laboratorio no está disponible para esta carrera");
    }
    Integer capacity = space == null ? null : space.getPeopleCapacity();
    Integer students = course.getStudentCount();
    if (capacity != null && capacity > 0 && students != null && students > capacity) {
      throw ApiException.conflict(
          "La capacidad del espacio es insuficiente: "
              + capacity
              + " lugares para "
              + students
              + " estudiantes");
    }

    // Choques con otras clases del mismo periodo: en nocturno se compara dentro del módulo; en
    // diurno, dentro del día.
    List<Object> parameters = new ArrayList<>();
    StringBuilder overlap = new StringBuilder("select count(b) from ScheduleBlock b where ");
    if (module != null) {
      parameters.add(input.moduleNumber());
      overlap.append("b.moduleNumber = ?1");
    } else {
      parameters.add(input.dayOfWeek());
      overlap.append("b.dayOfWeek = ?1");
    }
    parameters.add(endTime);
    parameters.add(startTime);
    parameters.add(LIVE);
    overlap.append(" and b.startTime < ?2 and b.endTime > ?3 and b.version.status in ?4");
    if (excludeId != null) {
      parameters.add(excludeId);
      overlap.append(" and b.id <> ?5");
    }
    int next = parameters.size() + 1;

    List<String> conflicts = new ArrayList<>();
    if (overlaps(overlap + " and b.version.schedule.course.id = ?" + next, parameters, course.getId())) {
      conflicts.add("El curso ya tiene una clase en ese horario");
    }
    String samePeriod = " and b.version.schedule.course.period.id = ?" + next;
    List<Object> periodParameters = new ArrayList<>(parameters);
    periodParameters.add(course.getPeriod().getId());
    if (overlaps(
        overlap + samePeriod + " and b.professor.id = ?" + (next + 1),
        periodParameters,
        input.professorId())) {
      conflicts.add("El profesor ya está asignado en ese horario");
    }
    if (input.spaceId() != null
        && overlaps(
            overlap + samePeriod + " and b.space.id = ?" + (next + 1),
            periodParameters,
            input.spaceId())) {
      conflicts.add("El aula o laboratorio ya está ocupado en ese horario");
    }
    if (!conflicts.isEmpty()) {
      throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, conflicts);
    }
  }

  private boolean overlaps(String jpql, List<Object> parameters, Object lastParameter) {
    List<Object> all = new ArrayList<>(parameters);
    all.add(lastParameter);
    return db.exists(jpql, all.toArray());
  }

  private void applyBlock(ScheduleBlock block, BlockContext context, SaveBlockRequest input) {
    Schedule schedule = context.schedule();
    ScheduleModule module =
        schedule.getShift() == Shift.NIGHT && input.moduleNumber() != null
            ? findModule(schedule.getId(), input.moduleNumber())
            : null;
    block.setSubject(db.ref(Subject.class, input.subjectId()));
    block.setProfessor(db.ref(Professor.class, input.professorId()));
    block.setSpace(db.ref(AcademicSpace.class, input.spaceId()));
    block.setDayOfWeek(input.dayOfWeek());
    block.setStartTime(LocalTime.parse(input.startTime()));
    block.setEndTime(LocalTime.parse(input.endTime()));
    block.setModuleNumber(input.moduleNumber());
    block.setValidFrom(module == null ? null : module.getStartDate());
    block.setValidUntil(module == null ? null : module.getEndDate());
    block.setNotes(null);
  }

  private static java.util.Map<String, Object> blockAudit(UUID scheduleId, SaveBlockRequest input) {
    return data(
        "scheduleId", scheduleId,
        "subjectId", input.subjectId(),
        "professorId", input.professorId(),
        "spaceId", input.spaceId(),
        "dayOfWeek", input.dayOfWeek(),
        "startTime", input.startTime(),
        "endTime", input.endTime());
  }

  // ----- Búsquedas -----

  private BlockContext blockContext(UUID scheduleId, AuthenticatedUser actor) {
    Schedule schedule = findSchedule(scheduleId, actor);
    ScheduleVersion version = latestVersion(scheduleId, ScheduleStatus.DRAFT);
    if (version == null) throw ApiException.conflict("El horario no tiene un borrador editable");
    return new BlockContext(schedule, version);
  }

  private Schedule findSchedule(UUID id, AuthenticatedUser actor) {
    Schedule schedule = db.find(Schedule.class, id);
    if (schedule == null) throw ApiException.notFound("Horario no encontrado");
    assertCareer(schedule.getCourse().getLevel().getCareer().getId(), actor);
    return schedule;
  }

  private ScheduleBlock findBlock(UUID blockId, BlockContext context) {
    ScheduleBlock block = db.find(ScheduleBlock.class, blockId);
    if (block == null || !block.getVersion().getId().equals(context.version().getId())) {
      throw ApiException.notFound("Bloque de horario no encontrado");
    }
    return block;
  }

  private ScheduleModule findModule(UUID scheduleId, int moduleNumber) {
    return first(
        db.list(
            "select m from ScheduleModule m where m.schedule.id = ?1 and m.moduleNumber = ?2",
            ScheduleModule.class,
            scheduleId,
            moduleNumber));
  }

  /** La versión más reciente del horario; si se indica un estado, la más reciente en ese estado. */
  private ScheduleVersion latestVersion(UUID scheduleId, ScheduleStatus status) {
    List<ScheduleVersion> versions =
        status == null
            ? db.list(
                "select v from ScheduleVersion v where v.schedule.id = ?1 order by v.version desc",
                ScheduleVersion.class,
                scheduleId)
            : db.list(
                "select v from ScheduleVersion v where v.schedule.id = ?1 and v.status = ?2"
                    + " order by v.version desc",
                ScheduleVersion.class,
                scheduleId,
                status);
    return first(versions);
  }

  private static void assertCareer(UUID careerId, AuthenticatedUser actor) {
    if (!actor.canAccessCareer(careerId)) {
      throw ApiException.forbidden("No puedes administrar esta carrera");
    }
  }

  private static int parseModuleNumber(String text) {
    try {
      int moduleNumber = Integer.parseInt(text.strip());
      if (moduleNumber >= 1 && moduleNumber <= 6) return moduleNumber;
    } catch (NumberFormatException exception) {
      // cae en el error común de abajo
    }
    throw ApiException.conflict("El módulo debe estar entre 1 y 6");
  }

  // ----- Escritura -----

  private ScheduleVersion newDraft(Schedule schedule, int number, String reason, UUID actorId) {
    ScheduleVersion version = new ScheduleVersion();
    version.setSchedule(schedule);
    version.setVersion(number);
    version.setStatus(ScheduleStatus.DRAFT);
    version.setReason(reason);
    version.setCreatedBy(db.ref(User.class, actorId));
    version.setModifiedBy(db.ref(User.class, actorId));
    db.persist(version);
    db.flush();
    return version;
  }

  /** Anota quién modificó el borrador por última vez. */
  private void touch(ScheduleVersion version, UUID actorId) {
    version.setModifiedBy(db.ref(User.class, actorId));
    version.setUpdatedAt(Instant.now());
  }

  /** Un horario nocturno tiene 6 módulos de 3 semanas, contados desde el inicio del periodo. */
  private void ensureNightModules(Schedule schedule, LocalDate periodStart) {
    for (int moduleNumber = 1; moduleNumber <= 6; moduleNumber++) {
      if (findModule(schedule.getId(), moduleNumber) != null) continue;
      LocalDate startDate = periodStart.plusDays((moduleNumber - 1) * 21L);
      ScheduleModule module = new ScheduleModule();
      module.setSchedule(schedule);
      module.setModuleNumber(moduleNumber);
      module.setStartDate(startDate);
      module.setEndDate(startDate.plusDays(18));
      db.persist(module);
    }
    db.flush();
  }

  // ----- Respuestas -----

  private static CareerOption career(Level level) {
    Career career = level.getCareer();
    return new CareerOption(career.getId(), career.getName());
  }

  private static LevelInfo levelInfo(Level level) {
    return new LevelInfo(level.getId(), level.getName(), level.getSortOrder(), career(level));
  }

  private static ProfessorInfo professorInfo(Professor professor) {
    return new ProfessorInfo(
        professor.getId(),
        professor.getFirstNames(),
        professor.getLastNames(),
        professor.getProfessionalTitle(),
        professor.getScheduleColor(),
        ProfessionalTitles.prefix(professor.getProfessionalTitle()));
  }

  private static ModuleResponse moduleResponse(ScheduleModule module) {
    return new ModuleResponse(
        module.getId(),
        module.getSchedule().getId(),
        module.getModuleNumber(),
        ApiDates.date(module.getStartDate()),
        ApiDates.date(module.getEndDate()),
        ApiDates.instant(module.getCreatedAt()),
        ApiDates.instant(module.getUpdatedAt()));
  }

  /**
   * En el detalle las horas van como "08:00"; al crear o editar una clase se devuelven como las
   * enviaba la API anterior ("1970-01-01T08:00:00.000Z").
   */
  private static BlockResponse blockResponse(ScheduleBlock block, boolean shortTimes) {
    AcademicSpace space = block.getSpace();
    return new BlockResponse(
        block.getId(),
        block.getDayOfWeek(),
        shortTimes ? HOUR.format(block.getStartTime()) : ApiDates.time(block.getStartTime()),
        shortTimes ? HOUR.format(block.getEndTime()) : ApiDates.time(block.getEndTime()),
        ApiDates.date(block.getValidFrom()),
        ApiDates.date(block.getValidUntil()),
        block.getModuleNumber(),
        block.getNotes(),
        new BlockSubject(block.getSubject().getId(), block.getSubject().getName()),
        professorInfo(block.getProfessor()),
        space == null
            ? null
            : new BlockSpace(
                space.getId(),
                space.getName(),
                space.getCode(),
                space.getLocation(),
                space.getType()));
  }

  private static <T> T first(List<T> items) {
    return items.isEmpty() ? null : items.get(0);
  }
}
