package ec.edu.intesud.crono.repository;

import ec.edu.intesud.crono.domain.LoginCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface LoginCodeRepository extends JpaRepository<LoginCode, UUID> {

  /** ¿Se pidió un código hace poco? Sirve para el tiempo de espera entre reenvíos. */
  boolean existsByUser_IdAndCreatedAtAfter(UUID userId, Instant since);

  /** El código vigente de un usuario: el más reciente que aún no se ha usado. */
  Optional<LoginCode> findFirstByUser_IdAndConsumedAtIsNullOrderByCreatedAtDesc(UUID userId);

  @Modifying
  @Transactional
  @Query(
      "update LoginCode c set c.consumedAt = :now where c.user.id = :userId and c.consumedAt is null")
  void consumePending(@Param("userId") UUID userId, @Param("now") Instant now);

  @Modifying
  @Transactional
  @Query("update LoginCode c set c.consumedAt = :now where c.id = :id")
  void consume(@Param("id") UUID id, @Param("now") Instant now);

  @Modifying
  @Transactional
  @Query("update LoginCode c set c.attempts = c.attempts + 1 where c.id = :id")
  void incrementAttempts(@Param("id") UUID id);

  /** Invalida el código cuando ya se agotaron los intentos permitidos. */
  @Modifying
  @Transactional
  @Query(
      "update LoginCode c set c.consumedAt = :now "
          + "where c.id = :id and c.attempts >= :maxAttempts and c.consumedAt is null")
  void consumeIfExhausted(
      @Param("id") UUID id, @Param("maxAttempts") int maxAttempts, @Param("now") Instant now);
}
