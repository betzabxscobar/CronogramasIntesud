package ec.edu.intesud.crono.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record ProfessorCareerId(
    @Column(name = "professor_id") UUID professorId, @Column(name = "career_id") UUID careerId)
    implements Serializable {}
