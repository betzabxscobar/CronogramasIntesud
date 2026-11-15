package ec.edu.intesud.crono.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "space_careers")
public class SpaceCareer {
  @EmbeddedId
  private SpaceCareerId id;

  @MapsId("spaceId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "space_id", nullable = false)
  private AcademicSpace space;

  @MapsId("careerId")
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "career_id", nullable = false)
  private Career career;

  public SpaceCareerId getId() {
    return id;
  }

  public void setId(SpaceCareerId id) {
    this.id = id;
  }

  public AcademicSpace getSpace() {
    return space;
  }

  public void setSpace(AcademicSpace space) {
    this.space = space;
  }

  public Career getCareer() {
    return career;
  }

  public void setCareer(Career career) {
    this.career = career;
  }
}
