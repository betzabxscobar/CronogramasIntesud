package ec.edu.intesud.crono.laboratories;

import com.fasterxml.jackson.annotation.JsonProperty;
import ec.edu.intesud.crono.careers.CareerLookup.CareerOption;
import ec.edu.intesud.crono.domain.SpaceStatus;
import ec.edu.intesud.crono.domain.SpaceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Datos de entrada y salida del módulo de laboratorios y aulas (espacios académicos). */
public final class LaboratoryDtos {

  private LaboratoryDtos() {}

  public record CreateLaboratoryRequest(
      @NotNull SpaceType type,
      @NotNull @Size(min = 2, max = 120) String name,
      @Size(min = 2, max = 30) String code,
      @Size(max = 180) String location,
      @Min(1) @Max(1000) Integer peopleCapacity,
      @Min(0) @Max(1000) Integer studentComputers,
      @Min(0) @Max(100) Integer teacherComputers,
      Boolean hasProjector,
      Boolean hasDigitalBoard,
      @NotNull @Size(min = 1) List<@NotNull UUID> careerIds) {}

  /** Edición parcial: los campos que no llegan no se modifican. */
  public record UpdateLaboratoryRequest(
      SpaceType type,
      @Size(min = 2, max = 120) String name,
      @Size(min = 2, max = 30) String code,
      @Size(max = 180) String location,
      @Min(1) @Max(1000) Integer peopleCapacity,
      @Min(0) @Max(1000) Integer studentComputers,
      @Min(0) @Max(100) Integer teacherComputers,
      Boolean hasProjector,
      Boolean hasDigitalBoard,
      @Size(min = 1) List<@NotNull UUID> careerIds) {}

  public record UpdateLaboratoryStatusRequest(@NotNull SpaceStatus status) {}

  public record LaboratoryCount(long scheduleBlocks, long reservations, long unavailabilities) {}

  public record LaboratoryResponse(
      UUID id,
      String name,
      String code,
      String location,
      SpaceType type,
      Integer peopleCapacity,
      Integer studentComputers,
      Integer teacherComputers,
      boolean hasProjector,
      boolean hasDigitalBoard,
      SpaceStatus status,
      String createdAt,
      String updatedAt,
      List<CareerOption> careers,
      @JsonProperty("_count") LaboratoryCount count) {}
}
