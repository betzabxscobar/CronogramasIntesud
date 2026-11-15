package ec.edu.intesud.crono.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "coordinator_careers")
public class CoordinatorCareer {
  @EmbeddedId
  private CoordinatorCareerId id;

  @MapsId("userId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @MapsId("careerId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "career_id", nullable = false)
  private Career career;

  @CreationTimestamp
  @Column(name = "assigned_at", nullable = false, updatable = false)
  private Instant assignedAt;

  public CoordinatorCareerId getId() {
    return id;
  }

  public void setId(CoordinatorCareerId id) {
    this.id = id;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Career getCareer() {
    return career;
  }

  public void setCareer(Career career) {
    this.career = career;
  }

  public Instant getAssignedAt() {
    return assignedAt;
  }

  public void setAssignedAt(Instant assignedAt) {
    this.assignedAt = assignedAt;
  }
}
