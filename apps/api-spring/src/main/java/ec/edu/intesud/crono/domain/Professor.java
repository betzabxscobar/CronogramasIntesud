package ec.edu.intesud.crono.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "professors")
public class Professor extends TimestampedEntity {
  @Column(name = "national_id", unique = true)
  private String nationalId;

  @Column(name = "first_names", nullable = false)
  private String firstNames;

  @Column(name = "last_names", nullable = false)
  private String lastNames;

  @Column(name = "professional_title")
  private String professionalTitle;

  @Column(name = "schedule_color", nullable = false, length = 7)
  private String scheduleColor = "#A9CBE4";

  @Column(name = "academic_biography", length = 800)
  private String academicBiography;

  @Column(name = "public_profile_enabled", nullable = false)
  private boolean publicProfileEnabled = true;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "photo_asset_id", unique = true)
  private FileAsset photo;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "status", nullable = false)
  private RecordStatus status = RecordStatus.ACTIVE;

  @OneToMany(mappedBy = "professor")
  private Set<ProfessorCareer> careers = new LinkedHashSet<>();

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

  public String getProfessionalTitle() {
    return professionalTitle;
  }

  public void setProfessionalTitle(String professionalTitle) {
    this.professionalTitle = professionalTitle;
  }

  public String getScheduleColor() {
    return scheduleColor;
  }

  public void setScheduleColor(String scheduleColor) {
    this.scheduleColor = scheduleColor;
  }

  public String getAcademicBiography() {
    return academicBiography;
  }

  public void setAcademicBiography(String academicBiography) {
    this.academicBiography = academicBiography;
  }

  public boolean isPublicProfileEnabled() {
    return publicProfileEnabled;
  }

  public void setPublicProfileEnabled(boolean publicProfileEnabled) {
    this.publicProfileEnabled = publicProfileEnabled;
  }

  public FileAsset getPhoto() {
    return photo;
  }

  public void setPhoto(FileAsset photo) {
    this.photo = photo;
  }

  public RecordStatus getStatus() {
    return status;
  }

  public void setStatus(RecordStatus status) {
    this.status = status;
  }

  public Set<ProfessorCareer> getCareers() {
    return careers;
  }

  public void setCareers(Set<ProfessorCareer> careers) {
    this.careers = careers;
  }
}
