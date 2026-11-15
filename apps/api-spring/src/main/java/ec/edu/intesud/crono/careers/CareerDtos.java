package ec.edu.intesud.crono.careers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import ec.edu.intesud.crono.domain.RecordStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Datos de entrada y salida del módulo de carreras. */
public final class CareerDtos {

  private CareerDtos() {}

  public record CreateCareerRequest(@NotNull @Size(min = 2, max = 150) String name) {}

  /** Edición parcial: los campos que no llegan no se modifican. */
  public record UpdateCareerRequest(@Size(min = 2, max = 150) String name) {}

  public record UpdateCareerStatusRequest(@NotNull RecordStatus status) {}

  public record CareerCount(long coordinators, long professors) {}

  public record CareerResponse(
      UUID id,
      String name,
      String slug,
      RecordStatus status,
      @JsonProperty("_count") @JsonInclude(JsonInclude.Include.NON_NULL) CareerCount count) {}
}
