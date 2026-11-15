package ec.edu.intesud.crono.professors;

import ec.edu.intesud.crono.careers.CareerLookup.CareerOption;
import ec.edu.intesud.crono.domain.RecordStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Datos de entrada y salida del módulo de profesores. */
public final class ProfessorDtos {

  private static final String COLOR = "^#[0-9A-Fa-f]{6}$";
  private static final String COLOR_MESSAGE = "El color debe tener formato hexadecimal #RRGGBB";

  private ProfessorDtos() {}

  public record CreateProfessorRequest(
      @Size(min = 6, max = 20) String nationalId,
      @NotNull @Size(min = 2, max = 100) String firstNames,
      @NotNull @Size(min = 2, max = 100) String lastNames,
      @Size(max = 180) String professionalTitle,
      @Pattern(regexp = COLOR, message = COLOR_MESSAGE) String scheduleColor,
      Boolean publicProfileEnabled,
      @NotNull @Size(min = 1) List<@NotNull UUID> careerIds) {}

  /** Edición parcial: los campos que no llegan no se modifican. */
  public record UpdateProfessorRequest(
      @Size(min = 6, max = 20) String nationalId,
      @Size(min = 2, max = 100) String firstNames,
      @Size(min = 2, max = 100) String lastNames,
      @Size(max = 180) String professionalTitle,
      @Pattern(regexp = COLOR, message = COLOR_MESSAGE) String scheduleColor,
      Boolean publicProfileEnabled,
      @Size(min = 1) List<@NotNull UUID> careerIds) {}

  public record UpdateProfessorStatusRequest(@NotNull RecordStatus status) {}

  public record CareerSummary(UUID id, String name, RecordStatus status) {}

  public record ProfessorResponse(
      UUID id,
      String nationalId,
      String firstNames,
      String lastNames,
      String professionalTitle,
      String scheduleColor,
      boolean publicProfileEnabled,
      RecordStatus status,
      String createdAt,
      String updatedAt,
      List<CareerSummary> careers,
      String professionalTitleAbbreviation) {}

  /** Lo que ve cualquier visitante, sin iniciar sesión. */
  public record PublicProfessorResponse(
      UUID id,
      String firstNames,
      String lastNames,
      String professionalTitle,
      String professionalTitleAbbreviation,
      List<CareerOption> careers) {}
}
