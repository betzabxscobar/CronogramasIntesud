package ec.edu.intesud.crono.repository;

import ec.edu.intesud.crono.domain.AcademicPeriod;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicPeriodRepository extends JpaRepository<AcademicPeriod, UUID> {}
