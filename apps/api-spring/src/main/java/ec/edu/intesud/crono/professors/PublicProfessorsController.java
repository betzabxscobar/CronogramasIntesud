package ec.edu.intesud.crono.professors;

import ec.edu.intesud.crono.professors.ProfessorDtos.PublicProfessorResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Directorio público de profesores: no exige iniciar sesión (ruta /public/**). */
@RestController
@RequestMapping("/public/professors")
public class PublicProfessorsController {

  private final ProfessorsService professors;

  public PublicProfessorsController(ProfessorsService professors) {
    this.professors = professors;
  }

  @GetMapping
  public List<PublicProfessorResponse> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) UUID careerId) {
    return professors.listPublic(search, careerId);
  }

  @GetMapping("/{id}")
  public PublicProfessorResponse detail(@PathVariable UUID id) {
    return professors.publicDetail(id);
  }
}
