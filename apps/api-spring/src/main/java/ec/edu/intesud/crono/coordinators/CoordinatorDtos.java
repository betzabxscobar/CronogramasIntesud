package ec.edu.intesud.crono.coordinators;

import ec.edu.intesud.crono.domain.RecordStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Datos de entrada y salida del módulo de coordinadores. */
public final class CoordinatorDtos {

  private CoordinatorDtos() {}

  public record CreateCoordinatorRequest(
      @NotNull @Size(min = 6, max = 20) String nationalId,
      @NotNull @Size(min = 2, max = 100) String firstNames,
      @NotNull @Size(min = 2, max = 100) String lastNames,
      @NotNull @Email @Size(min = 5, max = 180) String email,
      @NotNull @Size(min = 1) List<@NotNull UUID> careerIds) {}

  /** Edición parcial: los campos que no llegan no se modifican. */
  public record UpdateCoordinatorRequest(
      @Size(min = 6, max = 20) String nationalId,
      @Size(min = 2, max = 100) String firstNames,
      @Size(min = 2, max = 100) String lastNames,
      @Email @Size(min = 5, max = 180) String email,
      @Size(min = 1) List<@NotNull UUID> careerIds) {}

  public record UpdateCoordinatorStatusRequest(@NotNull RecordStatus status) {}

  public record CoordinatorCareerItem(UUID id, String name, RecordStatus status) {}

  public record CoordinatorResponse(
      UUID id,
      String nationalId,
      String firstNames,
      String lastNames,
      String email,
      RecordStatus status,
      String lastLoginAt,
      String createdAt,
      String updatedAt,
      List<CoordinatorCareerItem> careers) {}
}
