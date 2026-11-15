package ec.edu.intesud.crono.coordinators;

import ec.edu.intesud.crono.auth.AuthenticatedUser;
import ec.edu.intesud.crono.careers.CareerLookup.CareerOption;
import ec.edu.intesud.crono.common.PageResponse;
import ec.edu.intesud.crono.coordinators.CoordinatorDtos.CoordinatorResponse;
import ec.edu.intesud.crono.coordinators.CoordinatorDtos.CreateCoordinatorRequest;
import ec.edu.intesud.crono.coordinators.CoordinatorDtos.UpdateCoordinatorRequest;
import ec.edu.intesud.crono.coordinators.CoordinatorDtos.UpdateCoordinatorStatusRequest;
import ec.edu.intesud.crono.domain.RecordStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import ec.edu.intesud.crono.auth.Roles;
import ec.edu.intesud.crono.domain.UserRole;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Coordinadores y las carreras que administran. Solo para administradores. */
@RestController
@RequestMapping("/coordinators")
@Roles(UserRole.ADMIN)
public class CoordinatorsController {

  private final CoordinatorsService coordinators;

  public CoordinatorsController(CoordinatorsService coordinators) {
    this.coordinators = coordinators;
  }

  @GetMapping
  public PageResponse<CoordinatorResponse> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) RecordStatus status,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(50) int pageSize) {
    return coordinators.list(search, status, page, pageSize);
  }

  @GetMapping("/careers")
  public List<CareerOption> careers() {
    return coordinators.listCareers();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CoordinatorResponse create(
      @Valid @RequestBody CreateCoordinatorRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return coordinators.create(input, actor.id());
  }

  @PatchMapping("/{id}")
  public CoordinatorResponse update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateCoordinatorRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return coordinators.update(id, input, actor.id());
  }

  @PatchMapping("/{id}/status")
  public CoordinatorResponse updateStatus(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateCoordinatorStatusRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return coordinators.updateStatus(id, input.status(), actor.id());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
    coordinators.remove(id, actor.id());
  }
}
