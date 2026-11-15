package ec.edu.intesud.crono.careers;

import ec.edu.intesud.crono.auth.AuthenticatedUser;
import ec.edu.intesud.crono.careers.CareerDtos.CareerResponse;
import ec.edu.intesud.crono.careers.CareerDtos.CreateCareerRequest;
import ec.edu.intesud.crono.careers.CareerDtos.UpdateCareerRequest;
import ec.edu.intesud.crono.careers.CareerDtos.UpdateCareerStatusRequest;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Carreras institucionales. Solo para administradores. */
@RestController
@RequestMapping("/careers")
@Roles(UserRole.ADMIN)
public class CareersController {

  private final CareersService careers;

  public CareersController(CareersService careers) {
    this.careers = careers;
  }

  @GetMapping
  public List<CareerResponse> list() {
    return careers.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CareerResponse create(
      @Valid @RequestBody CreateCareerRequest input, @AuthenticationPrincipal AuthenticatedUser actor) {
    return careers.create(input.name(), actor.id());
  }

  @PatchMapping("/{id}")
  public CareerResponse update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateCareerRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return careers.update(id, input.name(), actor.id());
  }

  @PatchMapping("/{id}/status")
  public CareerResponse updateStatus(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateCareerStatusRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return careers.updateStatus(id, input.status(), actor.id());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
    careers.remove(id, actor.id());
  }
}
