package ec.edu.intesud.crono.repository;

import ec.edu.intesud.crono.domain.AcademicSpace;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AcademicSpaceRepository extends JpaRepository<AcademicSpace, UUID>, JpaSpecificationExecutor<AcademicSpace> {}
