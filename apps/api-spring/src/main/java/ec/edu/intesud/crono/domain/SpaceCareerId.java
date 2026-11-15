package ec.edu.intesud.crono.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record SpaceCareerId(
    @Column(name = "space_id") UUID spaceId, @Column(name = "career_id") UUID careerId)
    implements Serializable {}
