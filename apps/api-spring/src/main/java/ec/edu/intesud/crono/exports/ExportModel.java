package ec.edu.intesud.crono.exports;

import java.time.LocalDate;
import java.util.List;

/** Todo lo que se escribe en el libro de horarios de una carrera en un periodo. */
public record ExportModel(
    String careerName,
    String periodName,
    String coordinator,
    List<Signature> signatures,
    List<ExportLevel> levels,
    List<Catalog> catalogs,
    List<String> professors,
    List<String> spaces,
    List<ExportBlock> dayBlocks,
    List<ExportBlock> nightBlocks,
    List<ExportModule> nightModules) {

  public record ExportLevel(int order, String name) {}

  public record ExportSubject(String name, String code, Integer levelOrder, Integer weeklyHours) {}

  /** Asignaturas de una carrera, para los catálogos de la hoja Inicio. */
  public record Catalog(String careerName, List<ExportSubject> subjects) {}

  /**
   * Una clase. dayOfWeek va de 1 (lunes) a 7 y es null en el nocturno; las horas son "HH:mm"; el
   * color es el del docente.
   */
  public record ExportBlock(
      Integer levelOrder,
      Integer dayOfWeek,
      String startTime,
      String endTime,
      Integer moduleNumber,
      String subject,
      String color,
      String professor,
      String space) {}

  public record ExportModule(int moduleNumber, LocalDate startDate, LocalDate endDate) {}

  /** Línea de firma de la hoja Inicio; sin nombre queda en blanco para llenarla a mano. */
  public record Signature(String label, String name) {}
}
