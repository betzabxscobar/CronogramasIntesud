package ec.edu.intesud.crono.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "careers")
public class Career extends TimestampedEntity {
  @Column(name = "name", nullable = false, unique = true)
  private String name;

  @Column(name = "slug", nullable = false, unique = true)
  private String slug;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "status", nullable = false)
  private RecordStatus status = RecordStatus.ACTIVE;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSlug() {
    return slug;
  }

  public void setSlug(String slug) {
    this.slug = slug;
  }

  public RecordStatus getStatus() {
    return status;
  }

  public void setStatus(RecordStatus status) {
    this.status = status;
  }
}
