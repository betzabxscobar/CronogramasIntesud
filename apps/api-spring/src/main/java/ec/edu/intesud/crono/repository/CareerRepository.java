package ec.edu.intesud.crono.repository;

import ec.edu.intesud.crono.domain.Career;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerRepository extends JpaRepository<Career, UUID> {

  List<Career> findAllByOrderByStatusAscNameAsc();
}
