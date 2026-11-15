package ec.edu.intesud.crono.repository;

import ec.edu.intesud.crono.domain.Professor;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProfessorRepository extends JpaRepository<Professor, UUID>, JpaSpecificationExecutor<Professor> {}
