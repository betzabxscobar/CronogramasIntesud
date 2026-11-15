package ec.edu.intesud.crono.careers;

import static ec.edu.intesud.crono.common.AuditService.data;

import ec.edu.intesud.crono.careers.CareerDtos.CareerCount;
import ec.edu.intesud.crono.careers.CareerDtos.CareerResponse;
import ec.edu.intesud.crono.common.ApiException;
import ec.edu.intesud.crono.common.AuditService;
import ec.edu.intesud.crono.common.Db;
import ec.edu.intesud.crono.common.Texts;
import ec.edu.intesud.crono.domain.Career;
import ec.edu.intesud.crono.domain.RecordStatus;
import ec.edu.intesud.crono.repository.CareerRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CareersService {

  private final CareerRepository careers;
  private final Db db;
  private final AuditService audit;

  public CareersService(CareerRepository careers, Db db, AuditService audit) {
    this.careers = careers;
    this.db = db;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<CareerResponse> list() {
    Map<UUID, Long> coordinators =
        db.countByKey(
            "select cc.id.careerId, count(cc) from CoordinatorCareer cc group by cc.id.careerId");
    Map<UUID, Long> professors =
        db.countByKey(
            "select pc.id.careerId, count(pc) from ProfessorCareer pc group by pc.id.careerId");
    return careers.findAllByOrderByStatusAscNameAsc().stream()
        .map(
            career ->
                response(
                    career,
                    new CareerCount(
                        coordinators.getOrDefault(career.getId(), 0L),
                        professors.getOrDefault(career.getId(), 0L))))
        .toList();
  }

  public CareerResponse create(String rawName, UUID actorId) {
    String name = Texts.clean(rawName);
    String slug = validSlug(name);

    Career career = new Career();
    career.setName(name);
    career.setSlug(slug);
    save(career);
    audit.log(
        actorId, "CAREER_CREATED", "Career", career.getId(), null, data("name", name, "slug", slug));
    return response(career, null);
  }

  public CareerResponse update(UUID id, String rawName, UUID actorId) {
    Career career = find(id);
    if (rawName == null || rawName.isEmpty()) return response(career, counts(id));

    String name = Texts.clean(rawName);
    String slug = validSlug(name);
    Map<String, Object> previous = data("name", career.getName(), "slug", career.getSlug());
    career.setName(name);
    career.setSlug(slug);
    save(career);
    audit.log(actorId, "CAREER_UPDATED", "Career", id, previous, data("name", name, "slug", slug));
    return response(career, counts(id));
  }

  public CareerResponse updateStatus(UUID id, RecordStatus status, UUID actorId) {
    Career career = find(id);
    if (career.getStatus() == status) return response(career, counts(id));

    RecordStatus previous = career.getStatus();
    career.setStatus(status);
    save(career);
    audit.log(
        actorId,
        status == RecordStatus.ACTIVE ? "CAREER_ACTIVATED" : "CAREER_DEACTIVATED",
        "Career",
        id,
        data("status", previous),
        data("status", status));
    return response(career, counts(id));
  }

  public void remove(UUID id, UUID actorId) {
    Career career = find(id);
    long related =
        db.count("select count(x) from CoordinatorCareer x where x.id.careerId = ?1", id)
            + db.count("select count(x) from ProfessorCareer x where x.id.careerId = ?1", id)
            + db.count("select count(x) from Level x where x.career.id = ?1", id)
            + db.count("select count(x) from Subject x where x.career.id = ?1", id)
            + db.count("select count(x) from SpaceCareer x where x.id.careerId = ?1", id)
            + db.count("select count(x) from SpaceReservation x where x.career.id = ?1", id)
            + db.count("select count(x) from ImportJob x where x.career.id = ?1", id);
    if (related > 0) {
      throw ApiException.conflict(
          "No se puede eliminar porque la carrera ya tiene información vinculada");
    }
    audit.log(
        actorId,
        "CAREER_DELETED",
        "Career",
        id,
        data("name", career.getName(), "slug", career.getSlug()),
        null);
    careers.delete(career);
  }

  private Career find(UUID id) {
    return careers.findById(id).orElseThrow(() -> ApiException.notFound("Carrera no encontrada"));
  }

  private String validSlug(String name) {
    String slug = Texts.slugify(name);
    if (slug.isEmpty()) throw ApiException.conflict("El nombre de la carrera no es válido");
    return slug;
  }

  /** Guarda y traduce el choque de nombre o slug repetido (índices únicos) a un 409. */
  private void save(Career career) {
    try {
      careers.saveAndFlush(career);
    } catch (DataIntegrityViolationException exception) {
      throw ApiException.conflict("La carrera ya está registrada");
    }
  }

  private CareerCount counts(UUID careerId) {
    return new CareerCount(
        db.count("select count(x) from CoordinatorCareer x where x.id.careerId = ?1", careerId),
        db.count("select count(x) from ProfessorCareer x where x.id.careerId = ?1", careerId));
  }

  private static CareerResponse response(Career career, CareerCount count) {
    return new CareerResponse(
        career.getId(), career.getName(), career.getSlug(), career.getStatus(), count);
  }
}
