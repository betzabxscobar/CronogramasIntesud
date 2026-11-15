package ec.edu.intesud.crono.professors;

import ec.edu.intesud.crono.auth.AuthenticatedUser;
import ec.edu.intesud.crono.auth.Roles;
import ec.edu.intesud.crono.careers.CareerLookup.CareerOption;
import ec.edu.intesud.crono.common.PageResponse;
import ec.edu.intesud.crono.domain.RecordStatus;
import ec.edu.intesud.crono.domain.UserRole;
import ec.edu.intesud.crono.professors.ProfessorDtos.CreateProfessorRequest;
import ec.edu.intesud.crono.professors.ProfessorDtos.ProfessorResponse;
import ec.edu.intesud.crono.professors.ProfessorDtos.UpdateProfessorRequest;
import ec.edu.intesud.crono.professors.ProfessorDtos.UpdateProfessorStatusRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

/** Profesores. El coordinador gestiona los de sus carreras; solo el administrador elimina. */
@RestController
@RequestMapping("/professors")
@Roles({UserRole.ADMIN, UserRole.COORDINATOR})
public class ProfessorsController {

  private final ProfessorsService professors;

  public ProfessorsController(ProfessorsService professors) {
    this.professors = professors;
  }

  @GetMapping
  public PageResponse<ProfessorResponse> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) RecordStatus status,
      @RequestParam(required = false) UUID careerId,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "6") @Min(1) @Max(50) int pageSize,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return professors.list(search, status, careerId, page, pageSize, actor);
  }

  @GetMapping("/careers")
  public List<CareerOption> careers(@AuthenticationPrincipal AuthenticatedUser actor) {
    return professors.listCareers(actor);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ProfessorResponse create(
      @Valid @RequestBody CreateProfessorRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return professors.create(input, actor);
  }

  @PatchMapping("/{id}")
  public ProfessorResponse update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateProfessorRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return professors.update(id, input, actor);
  }

  @PatchMapping("/{id}/status")
  public ProfessorResponse updateStatus(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateProfessorStatusRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return professors.updateStatus(id, input.status(), actor);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Roles(UserRole.ADMIN)
  public void remove(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
    professors.remove(id, actor.id());
  }
}
