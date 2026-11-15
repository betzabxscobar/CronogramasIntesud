package ec.edu.intesud.crono.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
public class User extends TimestampedEntity {
  @Column(name = "national_id", nullable = false, unique = true)
  private String nationalId;

  @Column(name = "first_names", nullable = false)
  private String firstNames;

  @Column(name = "last_names", nullable = false)
  private String lastNames;

  @Column(name = "email", nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash")
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "role", nullable = false)
  private UserRole role;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "status", nullable = false)
  private RecordStatus status = RecordStatus.ACTIVE;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @OneToMany(mappedBy = "user")
  private Set<CoordinatorCareer> careers = new LinkedHashSet<>();

  public String getNationalId() {
    return nationalId;
  }

  public void setNationalId(String nationalId) {
    this.nationalId = nationalId;
  }

  public String getFirstNames() {
    return firstNames;
  }

  public void setFirstNames(String firstNames) {
    this.firstNames = firstNames;
  }

  public String getLastNames() {
    return lastNames;
  }

  public void setLastNames(String lastNames) {
    this.lastNames = lastNames;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public UserRole getRole() {
    return role;
  }

  public void setRole(UserRole role) {
    this.role = role;
  }

  public RecordStatus getStatus() {
    return status;
  }

  public void setStatus(RecordStatus status) {
    this.status = status;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }

  public void setLastLoginAt(Instant lastLoginAt) {
    this.lastLoginAt = lastLoginAt;
  }

  public Set<CoordinatorCareer> getCareers() {
    return careers;
  }

  public void setCareers(Set<CoordinatorCareer> careers) {
    this.careers = careers;
  }
}
