package ec.edu.intesud.crono.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.hibernate.annotations.UpdateTimestamp;

/** Tablas con fecha de creación y de última modificación. */
@MappedSuperclass
public abstract class TimestampedEntity extends CreatedEntity {
  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
