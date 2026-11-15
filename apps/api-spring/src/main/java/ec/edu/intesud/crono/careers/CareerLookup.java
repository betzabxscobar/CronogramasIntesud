package ec.edu.intesud.crono.careers;

import ec.edu.intesud.crono.auth.AuthenticatedUser;
import ec.edu.intesud.crono.common.ApiException;
import ec.edu.intesud.crono.common.Db;
import ec.edu.intesud.crono.domain.RecordStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Consultas de carreras que comparten varios módulos (listas desplegables y permisos). */
@Component
public class CareerLookup {

  /** Carrera para listas desplegables: {"id": ..., "name": ...}. */
  public record CareerOption(UUID id, String name) {}

  private final Db db;

  public CareerLookup(Db db) {
    this.db = db;
  }

  /** Carreras activas que el usuario puede ver: todas si es administrador. */
  public List<CareerOption> activeFor(AuthenticatedUser actor) {
    if (actor.isAdmin()) return allActive();
    if (actor.careerIds().isEmpty()) return List.of();
    return db.list(
        "select new ec.edu.intesud.crono.careers.CareerLookup$CareerOption(c.id, c.name)"
            + " from Career c where c.status = ?1 and c.id in ?2 order by c.name",
        CareerOption.class,
        RecordStatus.ACTIVE,
        actor.careerIds());
  }

  public List<CareerOption> allActive() {
    return db.list(
        "select new ec.edu.intesud.crono.careers.CareerLookup$CareerOption(c.id, c.name)"
            + " from Career c where c.status = ?1 order by c.name",
        CareerOption.class,
        RecordStatus.ACTIVE);
  }

  /** Todas las carreras indicadas deben existir y estar activas. */
  public void assertAllActive(Collection<UUID> careerIds) {
    if (careerIds.isEmpty()) return;
    long active =
        db.count(
            "select count(c) from Career c where c.id in ?1 and c.status = ?2",
            careerIds,
            RecordStatus.ACTIVE);
    if (active != careerIds.size()) {
      throw ApiException.conflict("Una o más carreras no están disponibles");
    }
  }

  public boolean isActive(UUID careerId) {
    return db.exists(
        "select count(c) from Career c where c.id = ?1 and c.status = ?2",
        careerId,
        RecordStatus.ACTIVE);
  }
}
