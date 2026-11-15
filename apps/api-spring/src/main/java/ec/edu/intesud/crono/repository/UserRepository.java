package ec.edu.intesud.crono.repository;

import ec.edu.intesud.crono.domain.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository
    extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

  Optional<User> findByNationalId(String nationalId);

  @Modifying
  @Transactional
  @Query("update User u set u.lastLoginAt = :now, u.updatedAt = :now where u.id = :id")
  void registerLogin(@Param("id") UUID id, @Param("now") Instant now);
}
