package ec.edu.intesud.crono.academicperiods;

import static ec.edu.intesud.crono.common.AuditService.data;

import ec.edu.intesud.crono.academicperiods.PeriodDtos.CourseCount;
import ec.edu.intesud.crono.academicperiods.PeriodDtos.CourseItem;
import ec.edu.intesud.crono.academicperiods.PeriodDtos.CourseLevel;
import ec.edu.intesud.crono.academicperiods.PeriodDtos.CourseResponse;
import ec.edu.intesud.crono.academicperiods.PeriodDtos.CreatePeriodRequest;
import ec.edu.intesud.crono.academicperiods.PeriodDtos.PeriodCount;
import ec.edu.intesud.crono.academicperiods.PeriodDtos.PeriodDetail;
import ec.edu.intesud.crono.academicperiods.PeriodDtos.PeriodListItem;
import ec.edu.intesud.crono.academicperiods.PeriodDtos.PeriodResponse;
import ec.edu.intesud.crono.academicperiods.PeriodDtos.SyncCoursesResponse;
import ec.edu.intesud.crono.academicperiods.PeriodDtos.UpdatePeriodRequest;
import ec.edu.intesud.crono.auth.AuthenticatedUser;
import ec.edu.intesud.crono.careers.CareerLookup.CareerOption;
import ec.edu.intesud.crono.common.ApiDates;
import ec.edu.intesud.crono.common.ApiException;
import ec.edu.intesud.crono.common.AuditService;
import ec.edu.intesud.crono.common.Db;
import ec.edu.intesud.crono.common.Texts;
import ec.edu.intesud.crono.domain.AcademicCourse;
import ec.edu.intesud.crono.domain.AcademicPeriod;
import ec.edu.intesud.crono.domain.AcademicPeriodStatus;
import ec.edu.intesud.crono.domain.Level;
import ec.edu.intesud.crono.domain.RecordStatus;
import ec.edu.intesud.crono.repository.AcademicCourseRepository;
import ec.edu.intesud.crono.repository.AcademicPeriodRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AcademicPeriodsService {

  private final AcademicPeriodRepository periods;
  private final AcademicCourseRepository courses;
  private final Db db;
  private final AuditService audit;

  public AcademicPeriodsService(
      AcademicPeriodRepository periods,
      AcademicCourseRepository courses,
      Db db,
      AuditService audit) {
    this.periods = periods;
    this.courses = courses;
    this.db = db;
    this.audit = audit;
  }

  /** Un coordinador solo cuenta y ve los cursos de sus carreras. */
  @Transactional(readOnly = true)
  public List<PeriodListItem> list(AuthenticatedUser actor) {
    Map<UUID, Long> courseCounts =
        actor.isAdmin()
            ? db.countByKey(
                "select c.period.id, count(c) from AcademicCourse c group by c.period.id")
            : actor.careerIds().isEmpty()
                ? Map.of()
                : db.countByKey(
                    "select c.period.id, count(c) from AcademicCourse c"
                        + " where c.level.career.id in ?1 group by c.period.id",
                    actor.careerIds());
    return db
        .list(
            "select p from AcademicPeriod p order by p.status, p.startDate desc",
            AcademicPeriod.class)
        .stream()
        .map(
            period ->
                new PeriodListItem(
                    period.getId(),
                    period.getName(),
                    ApiDates.date(period.getStartDate()),
                    ApiDates.date(period.getEndDate()),
                    period.getStatus(),
                    ApiDates.instant(period.getCreatedAt()),
                    new PeriodCount(courseCounts.getOrDefault(period.getId(), 0L))))
        .toList();
  }

  @Transactional(readOnly = true)
  public PeriodDetail detail(UUID id, AuthenticatedUser actor) {
    AcademicPeriod period = find(id);
    String query =
        "select c from AcademicCourse c join fetch c.level l join fetch l.career k"
            + " where c.period.id = ?1";
    List<AcademicCourse> found;
    if (actor.isAdmin()) {
      found = db.list(query + " order by k.name, l.sortOrder", AcademicCourse.class, id);
    } else if (actor.careerIds().isEmpty()) {
      found = List.of();
    } else {
      found =
          db.list(
              query + " and k.id in ?2 order by k.name, l.sortOrder",
              AcademicCourse.class,
              id,
              actor.careerIds());
    }
    Map<UUID, Long> schedules =
        db.countByKey(
            "select s.course.id, count(s) from Schedule s"
                + " where s.course.period.id = ?1 group by s.course.id",
            id);

    List<CourseItem> items =
        found.stream()
            .map(
                course -> {
                  Level level = course.getLevel();
                  return new CourseItem(
                      course.getId(),
                      course.getName(),
                      course.getStudentCount(),
                      course.getStatus(),
                      new CourseLevel(
                          level.getId(),
                          level.getName(),
                          level.getSortOrder(),
                          new CareerOption(level.getCareer().getId(), level.getCareer().getName())),
                      new CourseCount(schedules.getOrDefault(course.getId(), 0L)));
                })
            .toList();
    return new PeriodDetail(
        period.getId(),
        period.getName(),
        ApiDates.date(period.getStartDate()),
        ApiDates.date(period.getEndDate()),
        period.getStatus(),
        items,
        List.of("DAY", "NIGHT"));
  }

  public PeriodResponse create(CreatePeriodRequest input, UUID actorId) {
    validateDates(input.startDate(), input.endDate());
    AcademicPeriod period = new AcademicPeriod();
    period.setName(Texts.clean(input.name()));
    period.setStartDate(input.startDate());
    period.setEndDate(input.endDate());
    save(period);
    createMissingCourses(period);
    audit.log(
        actorId,
        "ACADEMIC_PERIOD_CREATED",
        "AcademicPeriod",
        period.getId(),
        null,
        data(
            "name", period.getName(),
            "startDate", input.startDate().toString(),
            "endDate", input.endDate().toString()));
    return PeriodResponse.of(period);
  }

  public PeriodResponse update(UUID id, UpdatePeriodRequest input, UUID actorId) {
    AcademicPeriod period = find(id);
    LocalDate startDate = input.startDate() != null ? input.startDate() : period.getStartDate();
    LocalDate endDate = input.endDate() != null ? input.endDate() : period.getEndDate();
    validateDates(startDate, endDate);
    Map<String, Object> previous = periodData(period);

    if (input.name() != null && !input.name().isEmpty()) period.setName(Texts.clean(input.name()));
    period.setStartDate(startDate);
    period.setEndDate(endDate);
    save(period);
    audit.log(
        actorId, "ACADEMIC_PERIOD_UPDATED", "AcademicPeriod", id, previous, periodData(period));
    return PeriodResponse.of(period);
  }

  /** Solo puede haber un periodo activo: al activar uno, los demás activos se cierran. */
  public PeriodResponse updateStatus(UUID id, AcademicPeriodStatus status, UUID actorId) {
    AcademicPeriod period = find(id);
    AcademicPeriodStatus previous = period.getStatus();
    if (status == AcademicPeriodStatus.ACTIVE) {
      db.execute(
          "update AcademicPeriod p set p.status = ?1 where p.status = ?2 and p.id <> ?3",
          AcademicPeriodStatus.CLOSED,
          AcademicPeriodStatus.ACTIVE,
          id);
      createMissingCourses(period);
    }
    period.setStatus(status);
    save(period);
    audit.log(
        actorId,
        "ACADEMIC_PERIOD_STATUS_CHANGED",
        "AcademicPeriod",
        id,
        data("status", previous),
        data("status", status));
    return PeriodResponse.of(period);
  }

  public SyncCoursesResponse syncCourses(UUID id, UUID actorId) {
    AcademicPeriod period = find(id);
    int created = createMissingCourses(period);
    audit.log(
        actorId,
        "ACADEMIC_COURSES_SYNCHRONIZED",
        "AcademicPeriod",
        id,
        null,
        data("created", created));
    return new SyncCoursesResponse(created);
  }

  public CourseResponse updateCourse(UUID id, Integer studentCount, AuthenticatedUser actor) {
    AcademicCourse course = findCourse(id);
    if (!actor.canAccessCareer(course.getLevel().getCareer().getId())) {
      throw ApiException.forbidden("No puedes administrar este curso");
    }
    Integer previous = course.getStudentCount();
    if (studentCount != null) course.setStudentCount(studentCount);
    courses.saveAndFlush(course);
    audit.log(
        actor.id(),
        "ACADEMIC_COURSE_UPDATED",
        "AcademicCourse",
        id,
        data("studentCount", previous),
        data("studentCount", course.getStudentCount()));
    return CourseResponse.of(course);
  }

  public void removeCourse(UUID id, UUID actorId) {
    AcademicCourse course = findCourse(id);
    if (db.exists("select count(s) from Schedule s where s.course.id = ?1", id)) {
      throw ApiException.conflict(
          "No se puede eliminar porque el curso ya tiene horarios vinculados");
    }
    audit.log(
        actorId,
        "ACADEMIC_COURSE_DELETED",
        "AcademicCourse",
        id,
        data(
            "name", course.getName(),
            "studentCount", course.getStudentCount(),
            "period", course.getPeriod().getName(),
            "level", course.getLevel().getName(),
            "career", course.getLevel().getCareer().getName()),
        null);
    courses.delete(course);
  }

  /** Crea el "Curso único" de cada nivel activo que aún no tenga curso en el periodo. */
  private int createMissingCourses(AcademicPeriod period) {
    List<UUID> missingLevels =
        db.list(
            "select l.id from Level l where l.status = ?1 and not exists"
                + " (select c.id from AcademicCourse c where c.period.id = ?2 and c.level.id = l.id)",
            UUID.class,
            RecordStatus.ACTIVE,
            period.getId());
    for (UUID levelId : missingLevels) {
      AcademicCourse course = new AcademicCourse();
      course.setPeriod(period);
      course.setLevel(db.ref(Level.class, levelId));
      course.setName("Curso único");
      db.persist(course);
    }
    db.flush();
    return missingLevels.size();
  }

  private AcademicPeriod find(UUID id) {
    return periods
        .findById(id)
        .orElseThrow(() -> ApiException.notFound("Periodo académico no encontrado"));
  }

  private AcademicCourse findCourse(UUID id) {
    return courses.findById(id).orElseThrow(() -> ApiException.notFound("Curso no encontrado"));
  }

  private static void validateDates(LocalDate startDate, LocalDate endDate) {
    if (!endDate.isAfter(startDate)) {
      throw ApiException.conflict(
          "La fecha de finalización debe ser posterior a la fecha de inicio");
    }
  }

  private static Map<String, Object> periodData(AcademicPeriod period) {
    return data(
        "name", period.getName(),
        "startDate", ApiDates.date(period.getStartDate()),
        "endDate", ApiDates.date(period.getEndDate()));
  }

  private void save(AcademicPeriod period) {
    try {
      periods.saveAndFlush(period);
    } catch (DataIntegrityViolationException exception) {
      throw ApiException.conflict("Ya existe un periodo académico con ese nombre");
    }
  }
}
