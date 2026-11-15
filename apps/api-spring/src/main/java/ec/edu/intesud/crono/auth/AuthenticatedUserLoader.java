package ec.edu.intesud.crono.auth;

import ec.edu.intesud.crono.domain.RecordStatus;
import ec.edu.intesud.crono.domain.User;
import ec.edu.intesud.crono.repository.CoordinatorCareerRepository;
import ec.edu.intesud.crono.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Carga el usuario y sus carreras desde la base de datos. */
@Component
public class AuthenticatedUserLoader {

  private final UserRepository users;
  private final CoordinatorCareerRepository coordinatorCareers;

  public AuthenticatedUserLoader(
      UserRepository users, CoordinatorCareerRepository coordinatorCareers) {
    this.users = users;
    this.coordinatorCareers = coordinatorCareers;
  }

  public Optional<AuthenticatedUser> loadActive(UUID userId) {
    return users
        .findById(userId)
        .filter(user -> user.getStatus() == RecordStatus.ACTIVE)
        .map(this::toAuthenticatedUser);
  }

  public AuthenticatedUser toAuthenticatedUser(User user) {
    return new AuthenticatedUser(
        user.getId(),
        user.getNationalId(),
        user.getFirstNames(),
        user.getLastNames(),
        user.getEmail(),
        user.getRole(),
        coordinatorCareers.findCareerIdsByUserId(user.getId()));
  }
}
