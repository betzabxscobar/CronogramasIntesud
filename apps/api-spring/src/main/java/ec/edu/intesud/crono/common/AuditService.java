package ec.edu.intesud.crono.common;

import ec.edu.intesud.crono.domain.AuditLog;
import ec.edu.intesud.crono.domain.User;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Registra en audit_logs quién hizo qué. Se llama dentro de la transacción del cambio. */
@Service
public class AuditService {

  private final Db db;
  private final ObjectMapper objectMapper;

  public AuditService(Db db, ObjectMapper objectMapper) {
    this.db = db;
    this.objectMapper = objectMapper;
  }

  public void log(
      UUID actorId,
      String action,
      String entityType,
      Object entityId,
      Map<String, Object> previousData,
      Map<String, Object> newData) {
    AuditLog entry = new AuditLog();
    entry.setUser(db.ref(User.class, actorId));
    entry.setAction(action);
    entry.setEntityType(entityType);
    entry.setEntityId(entityId == null ? null : entityId.toString());
    entry.setPreviousData(previousData == null ? null : objectMapper.writeValueAsString(previousData));
    entry.setNewData(newData == null ? null : objectMapper.writeValueAsString(newData));
    db.persist(entry);
  }

  /** Construye el JSON de datos: data("name", nombre, "slug", slug). Admite valores null. */
  public static Map<String, Object> data(Object... keysAndValues) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int index = 0; index < keysAndValues.length; index += 2) {
      map.put((String) keysAndValues[index], keysAndValues[index + 1]);
    }
    return map;
  }
}
