package ec.edu.intesud.crono.academicperiods;

import ec.edu.intesud.crono.academicperiods.PeriodDtos.CourseResponse;
import ec.edu.intesud.crono.academicperiods.PeriodDtos.CreatePeriodRequest;
import ec.edu.intesud.crono.academicperiods.PeriodDtos.PeriodDetail;
import ec.edu.intesud.crono.academicperiods.PeriodDtos.PeriodListItem;
import ec.edu.intesud.crono.academicperiods.PeriodDtos.PeriodResponse;
import ec.edu.intesud.crono.academicperiods.PeriodDtos.SyncCoursesResponse;
import ec.edu.intesud.crono.academicperiods.PeriodDtos.UpdateCourseRequest;
import ec.edu.intesud.crono.academicperiods.PeriodDtos.UpdatePeriodRequest;
import ec.edu.intesud.crono.academicperiods.PeriodDtos.UpdatePeriodStatusRequest;
import ec.edu.intesud.crono.auth.AuthenticatedUser;
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

/** Periodos académicos y los cursos de cada periodo. */
@RestController
@RequestMapping("/academic-periods")
@Roles({UserRole.ADMIN, UserRole.COORDINATOR})
public class AcademicPeriodsController {

  private final AcademicPeriodsService periods;

  public AcademicPeriodsController(AcademicPeriodsService periods) {
    this.periods = periods;
  }

  @GetMapping
  public List<PeriodListItem> list(@AuthenticationPrincipal AuthenticatedUser actor) {
    return periods.list(actor);
  }

  @GetMapping("/{id}")
  public PeriodDetail detail(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
    return periods.detail(id, actor);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Roles(UserRole.ADMIN)
  public PeriodResponse create(
      @Valid @RequestBody CreatePeriodRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return periods.create(input, actor.id());
  }

  @PatchMapping("/{id}")
  @Roles(UserRole.ADMIN)
  public PeriodResponse update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdatePeriodRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return periods.update(id, input, actor.id());
  }

  @PatchMapping("/{id}/status")
  @Roles(UserRole.ADMIN)
  public PeriodResponse updateStatus(
      @PathVariable UUID id,
      @Valid @RequestBody UpdatePeriodStatusRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return periods.updateStatus(id, input.status(), actor.id());
  }

  @PostMapping("/{id}/sync-courses")
  @ResponseStatus(HttpStatus.CREATED)
  @Roles(UserRole.ADMIN)
  public SyncCoursesResponse syncCourses(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
    return periods.syncCourses(id, actor.id());
  }

  @PatchMapping("/courses/{id}")
  public CourseResponse updateCourse(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateCourseRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return periods.updateCourse(id, input.studentCount(), actor);
  }

  @DeleteMapping("/courses/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Roles(UserRole.ADMIN)
  public void removeCourse(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
    periods.removeCourse(id, actor.id());
  }
}
