package ec.edu.intesud.crono.imports;

import ec.edu.intesud.crono.auth.AuthenticatedUser;
import ec.edu.intesud.crono.auth.Roles;
import ec.edu.intesud.crono.common.ApiException;
import ec.edu.intesud.crono.domain.UserRole;
import ec.edu.intesud.crono.imports.ImportDtos.CancelResult;
import ec.edu.intesud.crono.imports.ImportDtos.CommitResult;
import ec.edu.intesud.crono.imports.ImportDtos.ImportPreview;
import ec.edu.intesud.crono.imports.ImportDtos.JobDetail;
import ec.edu.intesud.crono.imports.ImportDtos.JobListItem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Importación del libro de horarios: vista previa, confirmación, consulta y cancelación. */
@RestController
@RequestMapping("/imports")
@Roles({UserRole.ADMIN, UserRole.COORDINATOR})
public class ImportsController {

  private static final Set<String> SHIFTS = Set.of("ALL", "DAY", "NIGHT");

  private final ImportsService imports;

  public ImportsController(ImportsService imports) {
    this.imports = imports;
  }

  @GetMapping
  public List<JobListItem> list(@AuthenticationPrincipal AuthenticatedUser actor) {
    return imports.list(actor);
  }

  @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public ImportPreview preview(
      @RequestParam(value = "file", required = false) MultipartFile file,
      @RequestParam(value = "careerId", required = false) String careerId,
      @RequestParam(value = "catalogColumn", required = false) String catalogColumn,
      @RequestParam(value = "includeSchedule", required = false) String includeSchedule,
      @RequestParam(value = "periodId", required = false) String periodId,
      @RequestParam(value = "shift", required = false) String shift,
      @AuthenticationPrincipal AuthenticatedUser actor)
      throws IOException {
    List<String> errors = new ArrayList<>();
    UUID career = uuid(careerId);
    if (career == null) errors.add("careerId must be a UUID");

    Integer column = null;
    if (catalogColumn != null && !catalogColumn.isBlank()) {
      try {
        column = Integer.valueOf(catalogColumn.trim());
      } catch (NumberFormatException exception) {
        errors.add("catalogColumn must be an integer number");
      }
      if (column != null && (column < 1 || column > 400)) {
        errors.add(
            column < 1
                ? "catalogColumn must not be less than 1"
                : "catalogColumn must not be greater than 400");
      }
    }

    UUID period = null;
    if (periodId != null && !periodId.isBlank()) {
      period = uuid(periodId);
      if (period == null) errors.add("periodId must be a UUID");
    }
    String shiftValue = shift == null || shift.isBlank() ? "ALL" : shift;
    if (!SHIFTS.contains(shiftValue)) {
      errors.add("shift must be one of the following values: ALL, DAY, NIGHT");
    }
    if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, errors);

    return imports.preview(
        file == null ? null : file.getBytes(),
        file == null ? null : file.getOriginalFilename(),
        actor,
        career,
        column,
        "true".equals(includeSchedule) || "1".equals(includeSchedule),
        period,
        shiftValue);
  }

  @PostMapping("/{id}/commit")
  @ResponseStatus(HttpStatus.CREATED)
  public CommitResult commit(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
    return imports.commit(id, actor);
  }

  @GetMapping("/{id}")
  public JobDetail find(@PathVariable UUID id) {
    return imports.findJob(id);
  }

  @DeleteMapping("/{id}")
  public CancelResult cancel(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
    return imports.cancel(id, actor);
  }

  private static UUID uuid(String value) {
    if (value == null) return null;
    try {
      return UUID.fromString(value.trim());
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }
}
