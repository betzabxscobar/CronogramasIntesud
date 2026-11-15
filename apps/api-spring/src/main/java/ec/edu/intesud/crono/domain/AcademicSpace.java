package ec.edu.intesud.crono.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "academic_spaces")
public class AcademicSpace extends TimestampedEntity {
  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "code", nullable = false, unique = true)
  private String code;

  @Column(name = "location")
  private String location;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "type", nullable = false)
  private SpaceType type;

  @Column(name = "people_capacity")
  private Integer peopleCapacity;

  @Column(name = "student_computers")
  private Integer studentComputers;

  @Column(name = "teacher_computers")
  private Integer teacherComputers;

  @Column(name = "has_projector", nullable = false)
  private boolean hasProjector;

  @Column(name = "has_digital_board", nullable = false)
  private boolean hasDigitalBoard;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "status", nullable = false)
  private SpaceStatus status = SpaceStatus.AVAILABLE;

  @OneToMany(mappedBy = "space")
  private Set<SpaceCareer> careers = new LinkedHashSet<>();

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public SpaceType getType() {
    return type;
  }

  public void setType(SpaceType type) {
    this.type = type;
  }

  public Integer getPeopleCapacity() {
    return peopleCapacity;
  }

  public void setPeopleCapacity(Integer peopleCapacity) {
    this.peopleCapacity = peopleCapacity;
  }

  public Integer getStudentComputers() {
    return studentComputers;
  }

  public void setStudentComputers(Integer studentComputers) {
    this.studentComputers = studentComputers;
  }

  public Integer getTeacherComputers() {
    return teacherComputers;
  }

  public void setTeacherComputers(Integer teacherComputers) {
    this.teacherComputers = teacherComputers;
  }

  public boolean isHasProjector() {
    return hasProjector;
  }

  public void setHasProjector(boolean hasProjector) {
    this.hasProjector = hasProjector;
  }

  public boolean isHasDigitalBoard() {
    return hasDigitalBoard;
  }

  public void setHasDigitalBoard(boolean hasDigitalBoard) {
    this.hasDigitalBoard = hasDigitalBoard;
  }

  public SpaceStatus getStatus() {
    return status;
  }

  public void setStatus(SpaceStatus status) {
    this.status = status;
  }

  public Set<SpaceCareer> getCareers() {
    return careers;
  }

  public void setCareers(Set<SpaceCareer> careers) {
    this.careers = careers;
  }
}
