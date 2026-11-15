package ec.edu.intesud.crono.laboratories;

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
import ec.edu.intesud.crono.domain.AcademicSpace;
import ec.edu.intesud.crono.domain.Career;
import ec.edu.intesud.crono.domain.SpaceCareer;
import ec.edu.intesud.crono.domain.SpaceCareerId;
import ec.edu.intesud.crono.domain.SpaceStatus;
import ec.edu.intesud.crono.domain.SpaceType;
import ec.edu.intesud.crono.laboratories.LaboratoryDtos.CreateLaboratoryRequest;
import ec.edu.intesud.crono.laboratories.LaboratoryDtos.LaboratoryCount;
import ec.edu.intesud.crono.laboratories.LaboratoryDtos.LaboratoryResponse;
import ec.edu.intesud.crono.laboratories.LaboratoryDtos.UpdateLaboratoryRequest;
import ec.edu.intesud.crono.repository.AcademicSpaceRepository;
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
public class LaboratoriesService {

  private static final String NOT_FOUND = "Espacio académico no encontrado";

  private final AcademicSpaceRepository spaces;
  private final CareerLookup careerLookup;
  private final Db db;
  private final AuditService audit;

  public LaboratoriesService(
      AcademicSpaceRepository spaces, CareerLookup careerLookup, Db db, AuditService audit) {
    this.spaces = spaces;
    this.careerLookup = careerLookup;
    this.db = db;
    this.audit = audit;
  }

  /** Un coordinador solo ve los espacios autorizados para alguna de sus carreras. */
  @Transactional(readOnly = true)
  public PageResponse<LaboratoryResponse> list(
      String search,
      SpaceStatus status,
      SpaceType type,
      int page,
      int pageSize,
      AuthenticatedUser actor) {
    String term = Texts.trimToNull(search);
    Specification<AcademicSpace> filter =
        (root, query, builder) -> {
          List<Predicate> conditions = new ArrayList<>();
          if (type != null) conditions.add(builder.equal(root.get("type"), type));
          if (status != null) conditions.add(builder.equal(root.get("status"), status));
          if (!actor.isAdmin()) {
            if (actor.careerIds().isEmpty()) {
              conditions.add(builder.disjunction());
            } else {
              Subquery<UUID> allowed = query.subquery(UUID.class);
              Root<SpaceCareer> link = allowed.from(SpaceCareer.class);
              allowed
                  .select(link.get("id").get("spaceId"))
                  .where(link.get("id").get("careerId").in(actor.careerIds()));
              conditions.add(root.get("id").in(allowed));
            }
          }
          if (term != null) {
            String pattern = Texts.containsPattern(term);
            conditions.add(
                builder.or(
                    builder.like(builder.lower(root.get("name")), pattern, '\\'),
                    builder.like(builder.lower(root.get("code")), pattern, '\\'),
                    builder.like(builder.lower(root.get("location")), pattern, '\\')));
          }
          return builder.and(conditions.toArray(Predicate[]::new));
        };
    Page<AcademicSpace> result =
        spaces.findAll(filter, PageRequest.of(page - 1, pageSize, Sort.by("name")));
    List<LaboratoryResponse> items = result.getContent().stream().map(this::response).toList();
    return PageResponse.of(items, page, pageSize, result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public List<CareerOption> listCareers(AuthenticatedUser actor) {
    return careerLookup.activeFor(actor);
  }

  public LaboratoryResponse create(CreateLaboratoryRequest input, UUID actorId) {
    assertUnique(input.careerIds());
    careerLookup.assertAllActive(input.careerIds());

    AcademicSpace space = new AcademicSpace();
    space.setType(input.type());
    space.setName(input.name().strip());
    String code = input.code() == null ? "" : input.code().strip().toUpperCase(Locale.ROOT);
    space.setCode(code.isEmpty() ? internalCode(input.name(), input.type()) : code);
    space.setLocation(Texts.trimToNull(input.location()));
    space.setPeopleCapacity(input.peopleCapacity());
    space.setStudentComputers(input.studentComputers() == null ? 0 : input.studentComputers());
    space.setTeacherComputers(input.teacherComputers() == null ? 0 : input.teacherComputers());
    space.setHasProjector(Boolean.TRUE.equals(input.hasProjector()));
    space.setHasDigitalBoard(Boolean.TRUE.equals(input.hasDigitalBoard()));
    save(space);
    assignCareers(space, input.careerIds());

    audit.log(
        actorId,
        "ACADEMIC_SPACE_CREATED",
        "AcademicSpace",
        space.getId(),
        null,
        data(
            "name", space.getName(),
            "code", space.getCode(),
            "type", space.getType(),
            "careerIds", input.careerIds()));
    return response(space);
  }

  public LaboratoryResponse update(UUID id, UpdateLaboratoryRequest input, UUID actorId) {
    AcademicSpace space = find(id);
    Map<String, Object> previous =
        data("name", space.getName(), "code", space.getCode(), "type", space.getType());
    if (input.careerIds() != null) {
      assertUnique(input.careerIds());
      careerLookup.assertAllActive(input.careerIds());
    }

    if (input.type() != null) space.setType(input.type());
    if (input.name() != null) space.setName(input.name().strip());
    if (input.code() != null) space.setCode(input.code().strip().toUpperCase(Locale.ROOT));
    if (input.location() != null) space.setLocation(Texts.trimToNull(input.location()));
    if (input.peopleCapacity() != null) space.setPeopleCapacity(input.peopleCapacity());
    if (input.studentComputers() != null) space.setStudentComputers(input.studentComputers());
    if (input.teacherComputers() != null) space.setTeacherComputers(input.teacherComputers());
    if (input.hasProjector() != null) space.setHasProjector(input.hasProjector());
    if (input.hasDigitalBoard() != null) space.setHasDigitalBoard(input.hasDigitalBoard());
    save(space);
    if (input.careerIds() != null) {
      db.execute("delete from SpaceCareer sc where sc.id.spaceId = ?1", id);
      assignCareers(space, input.careerIds());
    }

    audit.log(
        actorId,
        "ACADEMIC_SPACE_UPDATED",
        "AcademicSpace",
        id,
        previous,
        data("name", space.getName(), "code", space.getCode(), "type", space.getType()));
    return response(space);
  }

  public LaboratoryResponse updateStatus(UUID id, SpaceStatus status, UUID actorId) {
    AcademicSpace space = find(id);
    SpaceStatus previous = space.getStatus();
    space.setStatus(status);
    save(space);
    audit.log(
        actorId,
        "ACADEMIC_SPACE_STATUS_CHANGED",
        "AcademicSpace",
        id,
        data("status", previous),
        data("status", status));
    return response(space);
  }

  public void remove(UUID id, UUID actorId) {
    AcademicSpace space = find(id);
    LaboratoryCount count = counts(id);
    if (count.scheduleBlocks() + count.reservations() > 0) {
      throw ApiException.conflict(
          "No se puede eliminar porque el espacio tiene horarios o reservas. Puedes marcarlo como inactivo.");
    }
    audit.log(
        actorId,
        "ACADEMIC_SPACE_DELETED",
        "AcademicSpace",
        id,
        data("name", space.getName(), "code", space.getCode()),
        null);
    db.flush();
    // Sus carreras autorizadas y bloqueos se borran en cascada en la base de datos.
    db.execute("delete from AcademicSpace s where s.id = ?1", id);
  }

  private AcademicSpace find(UUID id) {
    return spaces.findById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
  }

  private void assertUnique(List<UUID> careerIds) {
    if (new HashSet<>(careerIds).size() != careerIds.size()) {
      throw ApiException.badRequest("careerIds no debe contener valores repetidos");
    }
  }

  private void assignCareers(AcademicSpace space, List<UUID> careerIds) {
    for (UUID careerId : careerIds) {
      SpaceCareer link = new SpaceCareer();
      link.setId(new SpaceCareerId(space.getId(), careerId));
      link.setSpace(space);
      link.setCareer(db.ref(Career.class, careerId));
      db.persist(link);
    }
    db.flush();
  }

  /** Código generado cuando no se indica uno: "LAB-NOMBRE" o "AULA-NOMBRE" (máx. 30). */
  private static String internalCode(String name, SpaceType type) {
    String normalized =
        Texts.withoutAccents(name)
            .replaceAll("[^a-zA-Z0-9]+", "-")
            .replaceAll("^-|-$", "")
            .toUpperCase(Locale.ROOT);
    String code = (type == SpaceType.CLASSROOM ? "AULA" : "LAB") + "-" + normalized;
    return code.length() > 30 ? code.substring(0, 30) : code;
  }

  /** Guarda y traduce el choque de código repetido (índice único) a un 409. */
  private void save(AcademicSpace space) {
    try {
      spaces.saveAndFlush(space);
    } catch (DataIntegrityViolationException exception) {
      throw ApiException.conflict("Ya existe un espacio académico con ese nombre");
    }
  }

  private LaboratoryCount counts(UUID spaceId) {
    return new LaboratoryCount(
        db.count("select count(b) from ScheduleBlock b where b.space.id = ?1", spaceId),
        db.count("select count(r) from SpaceReservation r where r.space.id = ?1", spaceId),
        db.count("select count(u) from SpaceUnavailability u where u.space.id = ?1", spaceId));
  }

  private LaboratoryResponse response(AcademicSpace space) {
    List<CareerOption> careers =
        db.list(
            "select new ec.edu.intesud.crono.careers.CareerLookup$CareerOption(c.id, c.name)"
                + " from SpaceCareer sc join sc.career c where sc.id.spaceId = ?1 order by c.name",
            CareerOption.class,
            space.getId());
    return new LaboratoryResponse(
        space.getId(),
        space.getName(),
        space.getCode(),
        space.getLocation(),
        space.getType(),
        space.getPeopleCapacity(),
        space.getStudentComputers(),
        space.getTeacherComputers(),
        space.isHasProjector(),
        space.isHasDigitalBoard(),
        space.getStatus(),
        ApiDates.instant(space.getCreatedAt()),
        ApiDates.instant(space.getUpdatedAt()),
        careers,
        counts(space.getId()));
  }
}
