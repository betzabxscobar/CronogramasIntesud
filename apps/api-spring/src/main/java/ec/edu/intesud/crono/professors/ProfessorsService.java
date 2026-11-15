package ec.edu.intesud.crono.professors;

import static ec.edu.intesud.crono.common.AuditService.data;

import ec.edu.intesud.crono.auth.AuthenticatedUser;
import ec.edu.intesud.crono.careers.CareerLookup;
import ec.edu.intesud.crono.careers.CareerLookup.CareerOption;
import ec.edu.intesud.crono.common.ApiDates;
import ec.edu.intesud.crono.common.ApiException;
import ec.edu.intesud.crono.common.AuditService;
import ec.edu.intesud.crono.common.Db;
import ec.edu.intesud.crono.common.PageResponse;
import ec.edu.intesud.crono.common.Texts;
import ec.edu.intesud.crono.domain.Career;
import ec.edu.intesud.crono.domain.Professor;
import ec.edu.intesud.crono.domain.ProfessorCareer;
import ec.edu.intesud.crono.domain.ProfessorCareerId;
import ec.edu.intesud.crono.domain.RecordStatus;
import ec.edu.intesud.crono.professors.ProfessorDtos.CareerSummary;
import ec.edu.intesud.crono.professors.ProfessorDtos.CreateProfessorRequest;
import ec.edu.intesud.crono.professors.ProfessorDtos.ProfessorResponse;
import ec.edu.intesud.crono.professors.ProfessorDtos.PublicProfessorResponse;
import ec.edu.intesud.crono.professors.ProfessorDtos.UpdateProfessorRequest;
import ec.edu.intesud.crono.repository.ProfessorRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProfessorsService {

  private static final String NOT_FOUND = "Profesor no encontrado";
  private static final String DEFAULT_COLOR = "#A9CBE4";

  private final ProfessorRepository professors;
  private final CareerLookup careerLookup;
  private final Db db;
  private final AuditService audit;

  public ProfessorsService(
      ProfessorRepository professors, CareerLookup careerLookup, Db db, AuditService audit) {
    this.professors = professors;
    this.careerLookup = careerLookup;
    this.db = db;
    this.audit = audit;
  }

  /** Un coordinador solo ve los profesores vinculados con alguna de sus carreras. */
  @Transactional(readOnly = true)
  public PageResponse<ProfessorResponse> list(
      String search,
      RecordStatus status,
      UUID careerId,
      int page,
      int pageSize,
      AuthenticatedUser actor) {
    if (careerId != null && !actor.canAccessCareer(careerId)) {
      throw ApiException.forbidden("No tiene acceso a esta carrera");
    }
    String term = Texts.trimToNull(search);
    Specification<Professor> filter =
        (root, query, builder) -> {
          List<Predicate> conditions = new ArrayList<>();
          if (status != null) conditions.add(builder.equal(root.get("status"), status));

          // Debe tener al menos una carrera activa dentro de las que el usuario puede ver.
          Subquery<UUID> linked = query.subquery(UUID.class);
          Root<ProfessorCareer> link = linked.from(ProfessorCareer.class);
          List<Predicate> linkConditions = new ArrayList<>();
          linkConditions.add(builder.equal(link.get("status"), RecordStatus.ACTIVE));
          if (careerId != null) {
            linkConditions.add(builder.equal(link.get("id").get("careerId"), careerId));
          } else if (!actor.isAdmin()) {
            linkConditions.add(
                actor.careerIds().isEmpty()
                    ? builder.disjunction()
                    : link.get("id").get("careerId").in(actor.careerIds()));
          }
          linked
              .select(link.get("id").get("professorId"))
              .where(linkConditions.toArray(Predicate[]::new));
          conditions.add(root.get("id").in(linked));

          if (term != null) {
            conditions.add(
                matchesAny(
                    builder, root, term, "nationalId", "firstNames", "lastNames", "professionalTitle"));
          }
          return builder.and(conditions.toArray(Predicate[]::new));
        };
    Page<Professor> result =
        professors.findAll(
            filter, PageRequest.of(page - 1, pageSize, Sort.by("status", "lastNames", "firstNames")));
    List<ProfessorResponse> items = result.getContent().stream().map(this::response).toList();
    return PageResponse.of(items, page, pageSize, result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public List<CareerOption> listCareers(AuthenticatedUser actor) {
    return careerLookup.activeFor(actor);
  }

  public ProfessorResponse create(CreateProfessorRequest input, AuthenticatedUser actor) {
    assertCareerScope(input.careerIds(), actor);

    Professor professor = new Professor();
    professor.setNationalId(Texts.trimToNull(input.nationalId()));
    professor.setFirstNames(input.firstNames().strip());
    professor.setLastNames(input.lastNames().strip());
    professor.setProfessionalTitle(ProfessionalTitles.expand(input.professionalTitle()));
    professor.setScheduleColor(
        input.scheduleColor() == null
            ? DEFAULT_COLOR
            : input.scheduleColor().toUpperCase(Locale.ROOT));
    professor.setPublicProfileEnabled(!Boolean.FALSE.equals(input.publicProfileEnabled()));
    save(professor);
    assignCareers(professor, input.careerIds());

    audit.log(
        actor.id(),
        "PROFESSOR_CREATED",
        "Professor",
        professor.getId(),
        null,
        data(
            "nationalId", professor.getNationalId(),
            "firstNames", professor.getFirstNames(),
            "lastNames", professor.getLastNames(),
            "careerIds", input.careerIds()));
    return response(professor);
  }

  /**
   * Al cambiar las carreras, un coordinador solo reemplaza las suyas: los vínculos del profesor
   * con carreras de otros coordinadores no se tocan.
   */
  public ProfessorResponse update(UUID id, UpdateProfessorRequest input, AuthenticatedUser actor) {
    Professor professor = findScoped(id, actor);
    Map<String, Object> previous =
        data(
            "nationalId", professor.getNationalId(),
            "firstNames", professor.getFirstNames(),
            "lastNames", professor.getLastNames());
    if (input.careerIds() != null) assertCareerScope(input.careerIds(), actor);

    if (input.careerIds() != null) {
      if (actor.isAdmin()) {
        db.execute("delete from ProfessorCareer pc where pc.id.professorId = ?1", id);
      } else {
        db.execute(
            "delete from ProfessorCareer pc where pc.id.professorId = ?1 and pc.id.careerId in ?2",
            id,
            actor.careerIds());
      }
    }
    if (input.nationalId() != null) professor.setNationalId(Texts.trimToNull(input.nationalId()));
    if (input.firstNames() != null) professor.setFirstNames(input.firstNames().strip());
    if (input.lastNames() != null) professor.setLastNames(input.lastNames().strip());
    if (input.professionalTitle() != null) {
      professor.setProfessionalTitle(ProfessionalTitles.expand(input.professionalTitle()));
    }
    if (input.scheduleColor() != null) {
      professor.setScheduleColor(input.scheduleColor().toUpperCase(Locale.ROOT));
    }
    if (input.publicProfileEnabled() != null) {
      professor.setPublicProfileEnabled(input.publicProfileEnabled());
    }
    save(professor);
    if (input.careerIds() != null) assignCareers(professor, input.careerIds());

    ProfessorResponse updated = response(professor);
    audit.log(
        actor.id(),
        "PROFESSOR_UPDATED",
        "Professor",
        id,
        previous,
        data(
            "nationalId", professor.getNationalId(),
            "firstNames", professor.getFirstNames(),
            "lastNames", professor.getLastNames(),
            "careerIds", updated.careers().stream().map(CareerSummary::id).toList()));
    return updated;
  }

  public ProfessorResponse updateStatus(UUID id, RecordStatus status, AuthenticatedUser actor) {
    Professor professor = findScoped(id, actor);
    RecordStatus previous = professor.getStatus();
    if (previous == status) return response(professor);
    professor.setStatus(status);
    save(professor);
    audit.log(
        actor.id(),
        status == RecordStatus.ACTIVE ? "PROFESSOR_ACTIVATED" : "PROFESSOR_DEACTIVATED",
        "Professor",
        id,
        data("status", previous),
        data("status", status));
    return response(professor);
  }

  public void remove(UUID id, UUID actorId) {
    Professor professor =
        professors.findById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    if (db.exists("select count(b) from ScheduleBlock b where b.professor.id = ?1", id)) {
      throw ApiException.conflict(
          "No se puede eliminar porque el profesor ya tiene horarios asignados. Puedes desactivarlo.");
    }
    UUID photoId = professor.getPhoto() == null ? null : professor.getPhoto().getId();

    audit.log(
        actorId,
        "PROFESSOR_DELETED",
        "Professor",
        id,
        data(
            "nationalId", professor.getNationalId(),
            "firstNames", professor.getFirstNames(),
            "lastNames", professor.getLastNames(),
            "professionalTitle", professor.getProfessionalTitle()),
        null);
    db.flush();
    db.execute("delete from ProfessorCareer pc where pc.id.professorId = ?1", id);
    db.execute("delete from Professor p where p.id = ?1", id);
    if (photoId != null) db.execute("delete from FileAsset f where f.id = ?1", photoId);
  }

  /** Directorio público: solo profesores activos, con perfil público y carrera activa. */
  @Transactional(readOnly = true)
  public List<PublicProfessorResponse> listPublic(String search, UUID careerId) {
    String term = Texts.trimToNull(search);
    Specification<Professor> filter =
        (root, query, builder) -> {
          List<Predicate> conditions = new ArrayList<>();
          conditions.add(builder.equal(root.get("status"), RecordStatus.ACTIVE));
          conditions.add(builder.isTrue(root.get("publicProfileEnabled")));

          Subquery<UUID> linked = query.subquery(UUID.class);
          Root<ProfessorCareer> link = linked.from(ProfessorCareer.class);
          List<Predicate> linkConditions = new ArrayList<>();
          linkConditions.add(builder.equal(link.get("status"), RecordStatus.ACTIVE));
          linkConditions.add(builder.equal(link.get("career").get("status"), RecordStatus.ACTIVE));
          if (careerId != null) {
            linkConditions.add(builder.equal(link.get("id").get("careerId"), careerId));
          }
          linked
              .select(link.get("id").get("professorId"))
              .where(linkConditions.toArray(Predicate[]::new));
          conditions.add(root.get("id").in(linked));

          if (term != null) {
            conditions.add(
                matchesAny(builder, root, term, "firstNames", "lastNames", "professionalTitle"));
          }
          return builder.and(conditions.toArray(Predicate[]::new));
        };
    return professors.findAll(filter, Sort.by("lastNames", "firstNames")).stream()
        .map(professor -> publicResponse(professor, false))
        .toList();
  }

  @Transactional(readOnly = true)
  public PublicProfessorResponse publicDetail(UUID id) {
    Professor professor =
        professors
            .findById(id)
            .filter(found -> found.getStatus() == RecordStatus.ACTIVE)
            .filter(Professor::isPublicProfileEnabled)
            .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    return publicResponse(professor, true);
  }

  /** Busca el profesor; para un coordinador solo existe si está en alguna de sus carreras. */
  private Professor findScoped(UUID id, AuthenticatedUser actor) {
    Professor professor =
        professors.findById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    if (!actor.isAdmin()) {
      boolean visible =
          !actor.careerIds().isEmpty()
              && db.exists(
                  "select count(pc) from ProfessorCareer pc where pc.id.professorId = ?1"
                      + " and pc.status = ?2 and pc.id.careerId in ?3",
                  id,
                  RecordStatus.ACTIVE,
                  actor.careerIds());
      if (!visible) throw ApiException.notFound(NOT_FOUND);
    }
    return professor;
  }

  private void assertCareerScope(List<UUID> careerIds, AuthenticatedUser actor) {
    if (new HashSet<>(careerIds).size() != careerIds.size()) {
      throw ApiException.badRequest("careerIds no debe contener valores repetidos");
    }
    if (!actor.isAdmin() && !actor.careerIds().containsAll(careerIds)) {
      throw ApiException.forbidden("No puede asignar profesores a carreras no autorizadas");
    }
    careerLookup.assertAllActive(careerIds);
  }

  private void assignCareers(Professor professor, List<UUID> careerIds) {
    for (UUID careerId : careerIds) {
      ProfessorCareer link = new ProfessorCareer();
      link.setId(new ProfessorCareerId(professor.getId(), careerId));
      link.setProfessor(professor);
      link.setCareer(db.ref(Career.class, careerId));
      db.persist(link);
    }
    db.flush();
  }

  /** Guarda y traduce el choque de cédula repetida (índice único) a un 409. */
  private void save(Professor professor) {
    try {
      professors.saveAndFlush(professor);
    } catch (DataIntegrityViolationException exception) {
      throw ApiException.conflict("La cédula del profesor ya está registrada");
    }
  }

  private static Predicate matchesAny(
      CriteriaBuilder builder, Root<Professor> root, String term, String... fields) {
    String pattern = Texts.containsPattern(term);
    List<Predicate> options = new ArrayList<>();
    for (String field : fields) {
      options.add(builder.like(builder.lower(root.get(field)), pattern, '\\'));
    }
    return builder.or(options.toArray(Predicate[]::new));
  }

  private ProfessorResponse response(Professor professor) {
    List<CareerSummary> careers =
        db.list(
            "select new ec.edu.intesud.crono.professors.ProfessorDtos$CareerSummary("
                + "c.id, c.name, c.status)"
                + " from ProfessorCareer pc join pc.career c"
                + " where pc.id.professorId = ?1 and pc.status = ?2 order by c.name",
            CareerSummary.class,
            professor.getId(),
            RecordStatus.ACTIVE);
    return new ProfessorResponse(
        professor.getId(),
        professor.getNationalId(),
        professor.getFirstNames(),
        professor.getLastNames(),
        ProfessionalTitles.expand(professor.getProfessionalTitle()),
        professor.getScheduleColor(),
        professor.isPublicProfileEnabled(),
        professor.getStatus(),
        ApiDates.instant(professor.getCreatedAt()),
        ApiDates.instant(professor.getUpdatedAt()),
        careers,
        ProfessionalTitles.prefix(professor.getProfessionalTitle()));
  }

  private PublicProfessorResponse publicResponse(Professor professor, boolean onlyActiveCareers) {
    List<CareerOption> careers =
        db.list(
            "select new ec.edu.intesud.crono.careers.CareerLookup$CareerOption(c.id, c.name)"
                + " from ProfessorCareer pc join pc.career c"
                + " where pc.id.professorId = ?1 and pc.status = ?2"
                + (onlyActiveCareers ? " and c.status = ?2" : "")
                + " order by c.name",
            CareerOption.class,
            professor.getId(),
            RecordStatus.ACTIVE);
    return new PublicProfessorResponse(
        professor.getId(),
        professor.getFirstNames(),
        professor.getLastNames(),
        ProfessionalTitles.expand(professor.getProfessionalTitle()),
        ProfessionalTitles.prefix(professor.getProfessionalTitle()),
        careers);
  }
}
