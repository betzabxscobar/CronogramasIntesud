package ec.edu.intesud.crono.common;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Atajos para consultas JPQL puntuales (conteos, borrados masivos, proyecciones) que no merecen
 * un método propio en un repositorio. Los parámetros son posicionales: ?1, ?2, ...
 */
@Component
public class Db {

  @PersistenceContext private EntityManager entityManager;

  public long count(String jpql, Object... parameters) {
    return bind(entityManager.createQuery(jpql, Long.class), parameters).getSingleResult();
  }

  public boolean exists(String jpql, Object... parameters) {
    return count(jpql, parameters) > 0;
  }

  public <T> List<T> list(String jpql, Class<T> type, Object... parameters) {
    return bind(entityManager.createQuery(jpql, type), parameters).getResultList();
  }

  /** Para consultas "select clave, count(x) ... group by clave". */
  public Map<UUID, Long> countByKey(String jpql, Object... parameters) {
    Map<UUID, Long> counts = new LinkedHashMap<>();
    for (Object[] row : list(jpql, Object[].class, parameters)) {
      counts.put((UUID) row[0], (Long) row[1]);
    }
    return counts;
  }

  /** Ejecuta un update o delete masivo y devuelve las filas afectadas. */
  public int execute(String jpql, Object... parameters) {
    Query query = entityManager.createQuery(jpql);
    for (int index = 0; index < parameters.length; index++) {
      query.setParameter(index + 1, parameters[index]);
    }
    return query.executeUpdate();
  }

  /** Referencia a una fila por su id, sin consultarla (para asignar claves foráneas). */
  public <T> T ref(Class<T> type, Object id) {
    return id == null ? null : entityManager.getReference(type, id);
  }

  public <T> T find(Class<T> type, Object id) {
    return entityManager.find(type, id);
  }

  public void persist(Object entity) {
    entityManager.persist(entity);
  }

  public void remove(Object entity) {
    entityManager.remove(entity);
  }

  public void flush() {
    entityManager.flush();
  }

  public void clear() {
    entityManager.clear();
  }

  private static <T> TypedQuery<T> bind(TypedQuery<T> query, Object... parameters) {
    for (int index = 0; index < parameters.length; index++) {
      query.setParameter(index + 1, parameters[index]);
    }
    return query;
  }
}
