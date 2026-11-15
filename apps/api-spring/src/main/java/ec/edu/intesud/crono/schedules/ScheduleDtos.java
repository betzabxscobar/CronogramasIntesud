package ec.edu.intesud.crono.schedules;

import com.fasterxml.jackson.annotation.JsonProperty;
import ec.edu.intesud.crono.careers.CareerLookup.CareerOption;
import ec.edu.intesud.crono.domain.AcademicPeriodStatus;
import ec.edu.intesud.crono.domain.DayOfWeek;
import ec.edu.intesud.crono.domain.ScheduleStatus;
import ec.edu.intesud.crono.domain.Shift;
import ec.edu.intesud.crono.domain.SpaceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Datos de entrada y salida del módulo de horarios. */
public final class ScheduleDtos {

  private static final String HOUR = "^([01]\\d|2[0-3]):[0-5]\\d$";

  private ScheduleDtos() {}

  // ----- Entrada -----

  public record CreateScheduleRequest(@NotNull UUID courseId, @NotNull Shift shift) {}

  /** Una clase del horario. Las horas van como "08:00". */
  public record SaveBlockRequest(
      @NotNull UUID subjectId,
      @NotNull UUID professorId,
      UUID spaceId,
      @NotNull DayOfWeek dayOfWeek,
      @NotNull @Pattern(regexp = HOUR) String startTime,
      @NotNull @Pattern(regexp = HOUR) String endTime,
      @Min(1) @Max(20) Integer moduleNumber,
      @Size(max = 250) String notes) {}

  /** Fechas de un módulo nocturno, como "2026-10-05". */
  public record UpdateModuleRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate) {}

  // ----- Contexto: periodos con sus cursos -----

  public record LevelInfo(UUID id, String name, int order, CareerOption career) {}

  public record ContextCourse(UUID id, String name, Integer studentCount, LevelInfo level) {}

  public record ContextPeriod(
      UUID id, String name, AcademicPeriodStatus status, List<ContextCourse> courses) {}

  // ----- Lista de horarios de un periodo -----

  public record ListLevel(String name, int order, CareerOption career) {}

  public record ListCourse(UUID id, String name, ListLevel level) {}

  public record BlockCount(long blocks) {}

  public record ListVersion(
      UUID id,
      int version,
      ScheduleStatus status,
      String updatedAt,
      @JsonProperty("_count") BlockCount count) {}

  public record ScheduleListItem(
      UUID id, Shift shift, String updatedAt, ListCourse course, List<ListVersion> versions) {}

  // ----- Detalle de un horario -----

  public record PeriodInfo(UUID id, String name, String startDate, String endDate) {}

  public record CourseInfo(
      UUID id, String name, Integer studentCount, PeriodInfo period, LevelInfo level) {}

  public record ModuleResponse(
      UUID id,
      UUID scheduleId,
      int moduleNumber,
      String startDate,
      String endDate,
      String createdAt,
      String updatedAt) {}

  public record PublishedVersion(int version, String publishedAt) {}

  public record BlockSubject(UUID id, String name) {}

  public record ProfessorInfo(
      UUID id,
      String firstNames,
      String lastNames,
      String professionalTitle,
      String scheduleColor,
      String professionalTitleAbbreviation) {}

  public record BlockSpace(UUID id, String name, String code, String location, SpaceType type) {}

  public record BlockResponse(
      UUID id,
      DayOfWeek dayOfWeek,
      String startTime,
      String endTime,
      String validFrom,
      String validUntil,
      Integer moduleNumber,
      String notes,
      BlockSubject subject,
      ProfessorInfo professor,
      BlockSpace space) {}

  public record VersionDetail(
      UUID id, int version, ScheduleStatus status, List<BlockResponse> blocks) {}

  public record CatalogSubject(UUID id, String name, Integer weeklyHours) {}

  public record CatalogSpace(
      UUID id, String name, String code, String location, SpaceType type, Integer peopleCapacity) {}

  public record Catalogs(
      List<CatalogSubject> subjects, List<ProfessorInfo> professors, List<CatalogSpace> spaces) {}

  public record ScheduleDetail(
      UUID id,
      Shift shift,
      CourseInfo course,
      List<ModuleResponse> modules,
      PublishedVersion publishedVersion,
      VersionDetail version,
      Catalogs catalogs,
      List<DayOfWeek> days) {}

  // ----- Respuestas de acciones -----

  public record CreatedSchedule(UUID id, UUID versionId) {}

  public record PublishResult(int publishedVersion, int draftVersion, int blocks) {}
}
