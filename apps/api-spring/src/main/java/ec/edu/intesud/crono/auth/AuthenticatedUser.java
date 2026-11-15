package ec.edu.intesud.crono.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ec.edu.intesud.crono.domain.UserRole;
import java.util.List;
import java.util.UUID;

/**
 * Usuario de la petición actual. En los controladores se recibe con {@code
 * @AuthenticationPrincipal AuthenticatedUser user} (antes {@code @CurrentUser()}).
 */
public record AuthenticatedUser(
    UUID id,
    String nationalId,
    String firstNames,
    String lastNames,
    String email,
    UserRole role,
    List<UUID> careerIds) {

  @JsonIgnore // ayuda interna; no forma parte del JSON que recibe el frontend
  public boolean isAdmin() {
    return role == UserRole.ADMIN;
  }

  /** Un administrador accede a todas las carreras; un coordinador solo a las asignadas. */
  public boolean canAccessCareer(UUID careerId) {
    return isAdmin() || (careerId != null && careerIds.contains(careerId));
  }
}
