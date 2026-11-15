package ec.edu.intesud.crono.academicstructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import ec.edu.intesud.crono.domain.Level;
import ec.edu.intesud.crono.domain.RecordStatus;
import ec.edu.intesud.crono.domain.Subject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Datos de entrada y salida de la estructura académica (niveles y materias de una carrera). */
public final class StructureDtos {

  private StructureDtos() {}

  public record CreateLevelRequest(
      @NotNull UUID careerId,
      @NotNull @Size(min = 2, max = 80) String name,
      @NotNull @Min(1) @Max(20) Integer order) {}

  /** Edición parcial: los campos que no llegan no se modifican. */
  public record UpdateLevelRequest(
      UUID careerId, @Size(min = 2, max = 80) String name, @Min(1) @Max(20) Integer order) {}

  public record CreateSubjectRequest(
      @NotNull UUID careerId,
      @NotNull UUID levelId,
      @NotNull @Size(min = 2, max = 140) String name,
      @Size(max = 30) String code,
      @Min(1) @Max(40) Integer weeklyHours,
      Boolean requiresIndividualComputer) {}

  /** Edición parcial: los campos que no llegan no se modifican. */
  public record UpdateSubjectRequest(
      UUID careerId,
      UUID levelId,
      @Size(min = 2, max = 140) String name,
      @Size(max = 30) String code,
      @Min(1) @Max(40) Integer weeklyHours,
      Boolean requiresIndividualComputer) {}

  /** Fila de la tabla levels tal cual. */
  public record LevelResponse(UUID id, UUID careerId, String name, int order, RecordStatus status) {
    public static LevelResponse of(Level level) {
      return new LevelResponse(
          level.getId(),
          level.getCareer().getId(),
          level.getName(),
          level.getSortOrder(),
          level.getStatus());
    }
  }

  /** Fila de la tabla subjects tal cual. */
  public record SubjectResponse(
      UUID id,
      UUID careerId,
      UUID levelId,
      String code,
      String name,
      Integer weeklyHours,
      boolean requiresIndividualComputer,
      RecordStatus status) {
    public static SubjectResponse of(Subject subject) {
      return new SubjectResponse(
          subject.getId(),
          subject.getCareer().getId(),
          subject.getLevel() == null ? null : subject.getLevel().getId(),
          subject.getCode(),
          subject.getName(),
          subject.getWeeklyHours(),
          subject.isRequiresIndividualComputer(),
          subject.getStatus());
    }
  }

  // --- Detalle de una carrera: niveles con sus materias ---

  public record SubjectCount(long scheduleBlocks) {}

  public record SubjectItem(
      UUID id,
      String name,
      String code,
      Integer weeklyHours,
      boolean requiresIndividualComputer,
      RecordStatus status,
      @JsonProperty("_count") SubjectCount count) {}

  public record LevelCount(long subjects, long courses) {}

  public record LevelItem(
      UUID id,
      String name,
      int order,
      RecordStatus status,
      List<SubjectItem> subjects,
      @JsonProperty("_count") LevelCount count) {}

  public record StructureResponse(UUID id, String name, List<LevelItem> levels) {}
}
