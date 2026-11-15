package ec.edu.intesud.crono.schedules;

import ec.edu.intesud.crono.auth.AuthenticatedUser;
import ec.edu.intesud.crono.auth.Roles;
import ec.edu.intesud.crono.domain.UserRole;
import ec.edu.intesud.crono.schedules.ScheduleDtos.BlockResponse;
import ec.edu.intesud.crono.schedules.ScheduleDtos.ContextPeriod;
import ec.edu.intesud.crono.schedules.ScheduleDtos.CreateScheduleRequest;
import ec.edu.intesud.crono.schedules.ScheduleDtos.CreatedSchedule;
import ec.edu.intesud.crono.schedules.ScheduleDtos.ModuleResponse;
import ec.edu.intesud.crono.schedules.ScheduleDtos.PublishResult;
import ec.edu.intesud.crono.schedules.ScheduleDtos.SaveBlockRequest;
import ec.edu.intesud.crono.schedules.ScheduleDtos.ScheduleDetail;
import ec.edu.intesud.crono.schedules.ScheduleDtos.ScheduleListItem;
import ec.edu.intesud.crono.schedules.ScheduleDtos.UpdateModuleRequest;
import jakarta.validation.Valid;
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

/** Horarios. El coordinador solo administra los de sus carreras. */
@RestController
@RequestMapping("/schedules")
@Roles({UserRole.ADMIN, UserRole.COORDINATOR})
public class SchedulesController {

  private final SchedulesService schedules;

  public SchedulesController(SchedulesService schedules) {
    this.schedules = schedules;
  }

  @GetMapping("/context")
  public List<ContextPeriod> context(@AuthenticationPrincipal AuthenticatedUser actor) {
    return schedules.context(actor);
  }

  @GetMapping
  public List<ScheduleListItem> list(
      @RequestParam UUID periodId, @AuthenticationPrincipal AuthenticatedUser actor) {
    return schedules.list(periodId, actor);
  }

  @GetMapping("/{id}")
  public ScheduleDetail detail(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
    return schedules.detail(id, actor);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CreatedSchedule create(
      @Valid @RequestBody CreateScheduleRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return schedules.create(input, actor);
  }

  @PostMapping("/{id}/blocks")
  @ResponseStatus(HttpStatus.CREATED)
  public BlockResponse addBlock(
      @PathVariable UUID id,
      @Valid @RequestBody SaveBlockRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return schedules.addBlock(id, input, actor);
  }

  @PatchMapping("/{id}/blocks/{blockId}")
  public BlockResponse updateBlock(
      @PathVariable UUID id,
      @PathVariable UUID blockId,
      @Valid @RequestBody SaveBlockRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return schedules.updateBlock(id, blockId, input, actor);
  }

  /** Publica el borrador y abre el siguiente con las mismas clases. */
  @PostMapping("/{id}/publish")
  @ResponseStatus(HttpStatus.CREATED)
  public PublishResult publish(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
    return schedules.publish(id, actor);
  }

  @PatchMapping("/{id}/modules/{moduleNumber}")
  public ModuleResponse updateModule(
      @PathVariable UUID id,
      @PathVariable String moduleNumber,
      @Valid @RequestBody UpdateModuleRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return schedules.updateModule(id, moduleNumber, input, actor);
  }

  @DeleteMapping("/{id}/blocks/{blockId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeBlock(
      @PathVariable UUID id,
      @PathVariable UUID blockId,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    schedules.removeBlock(id, blockId, actor);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void remove(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
    schedules.remove(id, actor);
  }
}
