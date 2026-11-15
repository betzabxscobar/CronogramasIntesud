package ec.edu.intesud.crono.coordinators;

import static ec.edu.intesud.crono.common.AuditService.data;

import ec.edu.intesud.crono.careers.CareerLookup;
import ec.edu.intesud.crono.common.ApiDates;
import ec.edu.intesud.crono.common.ApiException;
import ec.edu.intesud.crono.common.AuditService;
import ec.edu.intesud.crono.common.Db;
import ec.edu.intesud.crono.common.PageResponse;
import ec.edu.intesud.crono.common.Texts;
import ec.edu.intesud.crono.coordinators.CoordinatorDtos.CoordinatorCareerItem;
import ec.edu.intesud.crono.coordinators.CoordinatorDtos.CoordinatorResponse;
import ec.edu.intesud.crono.coordinators.CoordinatorDtos.CreateCoordinatorRequest;
import ec.edu.intesud.crono.coordinators.CoordinatorDtos.UpdateCoordinatorRequest;
import ec.edu.intesud.crono.domain.Career;
import ec.edu.intesud.crono.domain.CoordinatorCareer;
import ec.edu.intesud.crono.domain.CoordinatorCareerId;
import ec.edu.intesud.crono.domain.RecordStatus;
import ec.edu.intesud.crono.domain.User;
import ec.edu.intesud.crono.domain.UserRole;
import ec.edu.intesud.crono.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
public class CoordinatorsService {

  private final UserRepository users;
  private final CareerLookup careerLookup;
  private final Db db;
  private final AuditService audit;

  public CoordinatorsService(
      UserRepository users, CareerLookup careerLookup, Db db, AuditService audit) {
    this.users = users;
    this.careerLookup = careerLookup;
    this.db = db;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public PageResponse<CoordinatorResponse> list(
      String search, RecordStatus status, int page, int pageSize) {
    String term = Texts.trimToNull(search);
    Specification<User> filter =
        (root, query, builder) -> {
          List<Predicate> conditions = new ArrayList<>();
          conditions.add(builder.equal(root.get("role"), UserRole.COORDINATOR));
          if (status != null) conditions.add(builder.equal(root.get("status"), status));
          if (term != null) {
            String pattern = Texts.containsPattern(term);
            conditions.add(
                builder.or(
                    builder.like(builder.lower(root.get("nationalId")), pattern, '\\'),
                    builder.like(builder.lower(root.get("firstNames")), pattern, '\\'),
                    builder.like(builder.lower(root.get("lastNames")), pattern, '\\'),
                    builder.like(builder.lower(root.get("email")), pattern, '\\')));
          }
          return builder.and(conditions.toArray(Predicate[]::new));
        };
    Page<User> result =
        users.findAll(
            filter,
            PageRequest.of(page - 1, pageSize, Sort.by("status", "lastNames", "firstNames")));

    Map<UUID, List<CoordinatorCareerItem>> careers =
        careersOf(result.getContent().stream().map(User::getId).toList());
    List<CoordinatorResponse> items =
        result.getContent().stream()
            .map(user -> response(user, careers.getOrDefault(user.getId(), List.of())))
            .toList();
    return PageResponse.of(items, page, pageSize, result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public List<CareerLookup.CareerOption> listCareers() {
    return careerLookup.allActive();
  }

  public CoordinatorResponse create(CreateCoordinatorRequest input, UUID actorId) {
    assertUnique(input.careerIds());
    careerLookup.assertAllActive(input.careerIds());

    User user = new User();
    user.setNationalId(input.nationalId().strip());
    user.setFirstNames(input.firstNames().strip());
    user.setLastNames(input.lastNames().strip());
    user.setEmail(input.email().strip().toLowerCase(Locale.ROOT));
    user.setRole(UserRole.COORDINATOR);
    save(user);
    assignCareers(user, input.careerIds());

    audit.log(
        actorId,
        "COORDINATOR_CREATED",
        "User",
        user.getId(),
        null,
        data(
            "nationalId", user.getNationalId(),
            "email", user.getEmail(),
            "careerIds", input.careerIds()));
    return response(user);
  }

  public CoordinatorResponse update(UUID id, UpdateCoordinatorRequest input, UUID actorId) {
    User user = find(id);
    Map<String, Object> previous =
        data(
            "nationalId", user.getNationalId(),
            "email", user.getEmail(),
            "careerIds", careerIdsOf(id));
    if (input.careerIds() != null) {
      assertUnique(input.careerIds());
      careerLookup.assertAllActive(input.careerIds());
    }

    if (input.nationalId() != null) user.setNationalId(input.nationalId().strip());
    if (input.firstNames() != null) user.setFirstNames(input.firstNames().strip());
    if (input.lastNames() != null) user.setLastNames(input.lastNames().strip());
    if (input.email() != null) user.setEmail(input.email().strip().toLowerCase(Locale.ROOT));
    save(user);
    if (input.careerIds() != null) {
      db.execute("delete from CoordinatorCareer cc where cc.id.userId = ?1", id);
      assignCareers(user, input.careerIds());
    }

    audit.log(
        actorId,
        "COORDINATOR_UPDATED",
        "User",
        id,
        previous,
        data(
            "nationalId", user.getNationalId(),
            "email", user.getEmail(),
            "careerIds", careerIdsOf(id)));
    return response(user);
  }

  public CoordinatorResponse updateStatus(UUID id, RecordStatus status, UUID actorId) {
    User user = find(id);
    if (user.getStatus() == status) return response(user);

    RecordStatus previous = user.getStatus();
    user.setStatus(status);
    save(user);
    if (status == RecordStatus.INACTIVE) {
      // Un coordinador desactivado pierde de inmediato sus sesiones y códigos pendientes.
      Instant now = Instant.now();
      db.execute(
          "update UserSession s set s.revokedAt = ?1 where s.user.id = ?2 and s.revokedAt is null",
          now,
          id);
      db.execute(
          "update LoginCode c set c.consumedAt = ?1 where c.user.id = ?2 and c.consumedAt is null",
          now,
          id);
    }
    audit.log(
        actorId,
        status == RecordStatus.ACTIVE ? "COORDINATOR_ACTIVATED" : "COORDINATOR_DEACTIVATED",
        "User",
        id,
        data("status", previous),
        data("status", status));
    return response(user);
  }

  public void remove(UUID id, UUID actorId) {
    User user = find(id);
    long academicActivity =
        db.count("select count(v) from ScheduleVersion v where v.createdBy.id = ?1", id)
            + db.count("select count(v) from ScheduleVersion v where v.modifiedBy.id = ?1", id)
            + db.count("select count(v) from ScheduleVersion v where v.publishedBy.id = ?1", id)
            + db.count("select count(r) from SpaceReservation r where r.createdBy.id = ?1", id)
            + db.count("select count(j) from ImportJob j where j.user.id = ?1", id);
    if (academicActivity > 0) {
      throw ApiException.conflict(
          "No se puede eliminar porque el coordinador ya tiene actividad académica. Puedes desactivarlo.");
    }

    db.execute("delete from CoordinatorCareer cc where cc.id.userId = ?1", id);
    audit.log(
        actorId,
        "COORDINATOR_DELETED",
        "User",
        id,
        data(
            "nationalId", user.getNationalId(),
            "firstNames", user.getFirstNames(),
            "lastNames", user.getLastNames(),
            "email", user.getEmail()),
        null);
    db.flush();
    // Las sesiones y códigos del usuario se borran en cascada en la base de datos.
    db.execute("delete from User u where u.id = ?1", id);
  }

  private User find(UUID id) {
    return users
        .findById(id)
        .filter(user -> user.getRole() == UserRole.COORDINATOR)
        .orElseThrow(() -> ApiException.notFound("Coordinador no encontrado"));
  }

  private void assertUnique(List<UUID> careerIds) {
    if (new HashSet<>(careerIds).size() != careerIds.size()) {
      throw ApiException.badRequest("careerIds no debe contener valores repetidos");
    }
  }

  private void assignCareers(User user, List<UUID> careerIds) {
    for (UUID careerId : careerIds) {
      CoordinatorCareer link = new CoordinatorCareer();
      link.setId(new CoordinatorCareerId(user.getId(), careerId));
      link.setUser(user);
      link.setCareer(db.ref(Career.class, careerId));
      db.persist(link);
    }
    db.flush();
  }

  /** Guarda y traduce el choque de cédula o correo repetido (índices únicos) a un 409. */
  private void save(User user) {
    try {
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException exception) {
      String detail = String.valueOf(exception.getMostSpecificCause().getMessage());
      throw ApiException.conflict(
          detail.contains("email")
              ? "El correo ya está registrado"
              : "La cédula ya está registrada");
    }
  }

  private List<UUID> careerIdsOf(UUID userId) {
    return db.list(
        "select cc.id.careerId from CoordinatorCareer cc where cc.id.userId = ?1",
        UUID.class,
        userId);
  }

  /** Carreras de varios coordinadores a la vez, ordenadas por nombre. */
  private Map<UUID, List<CoordinatorCareerItem>> careersOf(List<UUID> userIds) {
    Map<UUID, List<CoordinatorCareerItem>> byUser = new LinkedHashMap<>();
    if (userIds.isEmpty()) return byUser;
    List<Object[]> rows =
        db.list(
            "select cc.id.userId, c.id, c.name, c.status from CoordinatorCareer cc"
                + " join cc.career c where cc.id.userId in ?1 order by c.name",
            Object[].class,
            userIds);
    for (Object[] row : rows) {
      byUser
          .computeIfAbsent((UUID) row[0], key -> new ArrayList<>())
          .add(new CoordinatorCareerItem((UUID) row[1], (String) row[2], (RecordStatus) row[3]));
    }
    return byUser;
  }

  private CoordinatorResponse response(User user) {
    return response(
        user, careersOf(List.of(user.getId())).getOrDefault(user.getId(), List.of()));
  }

  private static CoordinatorResponse response(User user, List<CoordinatorCareerItem> careers) {
    return new CoordinatorResponse(
        user.getId(),
        user.getNationalId(),
        user.getFirstNames(),
        user.getLastNames(),
        user.getEmail(),
        user.getStatus(),
        ApiDates.instant(user.getLastLoginAt()),
        ApiDates.instant(user.getCreatedAt()),
        ApiDates.instant(user.getUpdatedAt()),
        careers);
  }
}
