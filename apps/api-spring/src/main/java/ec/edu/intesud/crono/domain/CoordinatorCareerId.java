package ec.edu.intesud.crono.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record CoordinatorCareerId(
    @Column(name = "user_id") UUID userId, @Column(name = "career_id") UUID careerId)
    implements Serializable {}
