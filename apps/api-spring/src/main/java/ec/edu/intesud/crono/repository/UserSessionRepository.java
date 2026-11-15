package ec.edu.intesud.crono.repository;

import ec.edu.intesud.crono.domain.UserSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

  @Query("select s from UserSession s join fetch s.user where s.id = :id")
  Optional<UserSession> findWithUserById(@Param("id") UUID id);

  /** Rotación: cada renovación cambia el secreto del token de refresco. */
  @Modifying
  @Transactional
  @Query("update UserSession s set s.refreshTokenHash = :hash where s.id = :id")
  void rotate(@Param("id") UUID id, @Param("hash") String refreshTokenHash);

  @Modifying
  @Transactional
  @Query("update UserSession s set s.revokedAt = :now where s.id = :id and s.revokedAt is null")
  void revoke(@Param("id") UUID id, @Param("now") Instant now);
}
