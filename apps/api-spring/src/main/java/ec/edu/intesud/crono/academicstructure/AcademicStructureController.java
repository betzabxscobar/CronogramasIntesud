package ec.edu.intesud.crono.academicstructure;

import ec.edu.intesud.crono.academicstructure.StructureDtos.CreateLevelRequest;
import ec.edu.intesud.crono.academicstructure.StructureDtos.CreateSubjectRequest;
import ec.edu.intesud.crono.academicstructure.StructureDtos.LevelResponse;
import ec.edu.intesud.crono.academicstructure.StructureDtos.StructureResponse;
import ec.edu.intesud.crono.academicstructure.StructureDtos.SubjectResponse;
import ec.edu.intesud.crono.academicstructure.StructureDtos.UpdateLevelRequest;
import ec.edu.intesud.crono.academicstructure.StructureDtos.UpdateSubjectRequest;
import ec.edu.intesud.crono.auth.AuthenticatedUser;
import ec.edu.intesud.crono.careers.CareerLookup.CareerOption;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Niveles y materias de cada carrera. */
@RestController
@RequestMapping("/academic-structure")
@Roles({UserRole.ADMIN, UserRole.COORDINATOR})
public class AcademicStructureController {

  private final AcademicStructureService structure;

  public AcademicStructureController(AcademicStructureService structure) {
    this.structure = structure;
  }

  @GetMapping("/careers")
  public List<CareerOption> careers(@AuthenticationPrincipal AuthenticatedUser actor) {
    return structure.listCareers(actor);
  }

  @GetMapping
  public StructureResponse detail(
      @RequestParam UUID careerId, @AuthenticationPrincipal AuthenticatedUser actor) {
    return structure.detail(careerId, actor);
  }

  @PostMapping("/levels")
  @ResponseStatus(HttpStatus.CREATED)
  public LevelResponse createLevel(
      @Valid @RequestBody CreateLevelRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return structure.createLevel(input, actor);
  }

  @PatchMapping("/levels/{id}")
  public LevelResponse updateLevel(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateLevelRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return structure.updateLevel(id, input, actor);
  }

  @DeleteMapping("/levels/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeLevel(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
    structure.removeLevel(id, actor);
  }

  @PostMapping("/subjects")
  @ResponseStatus(HttpStatus.CREATED)
  public SubjectResponse createSubject(
      @Valid @RequestBody CreateSubjectRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return structure.createSubject(input, actor);
  }

  @PatchMapping("/subjects/{id}")
  public SubjectResponse updateSubject(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateSubjectRequest input,
      @AuthenticationPrincipal AuthenticatedUser actor) {
    return structure.updateSubject(id, input, actor);
  }

  @DeleteMapping("/subjects/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeSubject(
      @PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
    structure.removeSubject(id, actor);
  }
}
