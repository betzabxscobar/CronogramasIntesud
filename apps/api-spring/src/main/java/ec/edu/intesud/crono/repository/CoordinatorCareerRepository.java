package ec.edu.intesud.crono.repository;

import ec.edu.intesud.crono.domain.CoordinatorCareer;
import ec.edu.intesud.crono.domain.CoordinatorCareerId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoordinatorCareerRepository
    extends JpaRepository<CoordinatorCareer, CoordinatorCareerId> {

  /** Carreras a las que tiene acceso un coordinador. */
  @Query("select cc.id.careerId from CoordinatorCareer cc where cc.id.userId = :userId")
  List<UUID> findCareerIdsByUserId(@Param("userId") UUID userId);
}
