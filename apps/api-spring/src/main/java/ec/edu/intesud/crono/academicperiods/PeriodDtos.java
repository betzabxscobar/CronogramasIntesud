package ec.edu.intesud.crono.academicperiods;

import com.fasterxml.jackson.annotation.JsonProperty;
import ec.edu.intesud.crono.careers.CareerLookup.CareerOption;
import ec.edu.intesud.crono.common.ApiDates;
import ec.edu.intesud.crono.domain.AcademicCourse;
import ec.edu.intesud.crono.domain.AcademicPeriod;
import ec.edu.intesud.crono.domain.AcademicPeriodStatus;
import ec.edu.intesud.crono.domain.RecordStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Datos de entrada y salida de los periodos académicos y sus cursos. */
public final class PeriodDtos {

  private PeriodDtos() {}

  /** Las fechas llegan como "2026-10-05". */
  public record CreatePeriodRequest(
      @NotNull @Size(min = 3, max = 80) String name,
      @NotNull LocalDate startDate,
      @NotNull LocalDate endDate) {}

  /** Edición parcial: los campos que no llegan no se modifican. */
  public record UpdatePeriodRequest(
      @Size(min = 3, max = 80) String name, LocalDate startDate, LocalDate endDate) {}

  public record UpdatePeriodStatusRequest(@NotNull AcademicPeriodStatus status) {}

  public record UpdateCourseRequest(@Min(1) @Max(1000) Integer studentCount) {}

  /** Fila de la tabla academic_periods tal cual. */
  public record PeriodResponse(
      UUID id,
      String name,
      String startDate,
      String endDate,
      AcademicPeriodStatus status,
      String createdAt,
      String updatedAt) {
    public static PeriodResponse of(AcademicPeriod period) {
      return new PeriodResponse(
          period.getId(),
          period.getName(),
          ApiDates.date(period.getStartDate()),
          ApiDates.date(period.getEndDate()),
          period.getStatus(),
          ApiDates.instant(period.getCreatedAt()),
          ApiDates.instant(period.getUpdatedAt()));
    }
  }

  /** Fila de la tabla academic_courses tal cual. */
  public record CourseResponse(
      UUID id, UUID periodId, UUID levelId, String name, Integer studentCount, RecordStatus status) {
    public static CourseResponse of(AcademicCourse course) {
      return new CourseResponse(
          course.getId(),
          course.getPeriod().getId(),
          course.getLevel().getId(),
          course.getName(),
          course.getStudentCount(),
          course.getStatus());
    }
  }

  public record PeriodCount(long courses) {}

  public record PeriodListItem(
      UUID id,
      String name,
      String startDate,
      String endDate,
      AcademicPeriodStatus status,
      String createdAt,
      @JsonProperty("_count") PeriodCount count) {}

  public record CourseLevel(UUID id, String name, int order, CareerOption career) {}

  public record CourseCount(long schedules) {}

  public record CourseItem(
      UUID id,
      String name,
      Integer studentCount,
      RecordStatus status,
      CourseLevel level,
      @JsonProperty("_count") CourseCount count) {}

  public record PeriodDetail(
      UUID id,
      String name,
      String startDate,
      String endDate,
      AcademicPeriodStatus status,
      List<CourseItem> courses,
      List<String> shifts) {}

  public record SyncCoursesResponse(int created) {}
}
