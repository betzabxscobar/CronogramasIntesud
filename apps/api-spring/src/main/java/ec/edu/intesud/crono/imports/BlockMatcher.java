package ec.edu.intesud.crono.imports;

import static ec.edu.intesud.crono.excel.ImportText.comparable;

import ec.edu.intesud.crono.excel.ImportText;
import ec.edu.intesud.crono.excel.ImportText.SplitPerson;
import ec.edu.intesud.crono.imports.ScheduleSheetParser.DraftBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Resuelve las referencias que traen las clases del libro: el texto suelto "Fundamentos de
 * Programación / Tnlgo(e). Rivera Jeremy / Aula 3-D Laboratorio 04" hay que convertirlo en la
 * materia, el docente y el aula reales, y el libro y la base no siempre escriben igual:
 *
 * <ul>
 *   <li>El libro alterna "Apellido Nombre" y "Nombre Apellido".
 *   <li>El libro añade al aula un sufijo que la base no tiene ("Aula 3-D Laboratorio 04" frente
 *       a "Aula 3-D").
 * </ul>
 *
 * Se prueban varias reglas, de la más estricta a la más floja; las coincidencias flojas llevan
 * una explicación ("detail") que se muestra como advertencia. El catálogo incluye lo que ya está
 * en la base y lo que la propia importación va a crear (con id {@link #PENDING_ID}).
 */
public final class BlockMatcher {

  /** Id de los registros que esta misma importación va a crear. */
  public static final String PENDING_ID = "__pendiente__";

  public record SubjectRef(String id, String name) {}

  public record ProfessorRef(String id, String firstNames, String lastNames) {}

  public record SpaceRef(String id, String name, String code) {}

  /** Fila del catálogo: clave de comparación (y la alternativa con el nombre invertido). */
  public record Entry<T>(String key, String alternateKey, T value) {}

  public record Catalog(
      List<Entry<SubjectRef>> subjects,
      List<Entry<ProfessorRef>> professors,
      List<Entry<SpaceRef>> spaces) {}

  /** Resultado: item es null si no se encontró; detail explica el fallo o la coincidencia floja. */
  public record Resolved<T>(T item, String detail) {}

  /** Choque de una clase con otra anterior del mismo nivel. */
  public record Conflict(String kind, int withIndex, String message) {}

  /** Claves del docente y del aula de una clase, ya resueltos (null si no se encontraron). */
  public record ResolvedKeys(String professorKey, String spaceKey) {}

  private static final String[] DAY_NAMES = {
    "", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"
  };

  private BlockMatcher() {}

  public static Entry<SubjectRef> subjectEntry(String id, String name) {
    return new Entry<>(comparable(name), null, new SubjectRef(id, name));
  }

  public static Entry<ProfessorRef> professorEntry(String id, String firstNames, String lastNames) {
    return new Entry<>(
        comparable(lastNames + " " + firstNames),
        comparable(firstNames + " " + lastNames),
        new ProfessorRef(id, firstNames, lastNames));
  }

  public static Entry<SpaceRef> spaceEntry(String id, String name, String code) {
    return new Entry<>(comparable(name), null, new SpaceRef(id, name, code));
  }

  /** La materia cuyo nombre coincide exactamente, ignorando acentos y mayúsculas. */
  public static Resolved<SubjectRef> resolveSubject(String name, Catalog catalog) {
    String key = comparable(name);
    if (key.isEmpty()) return new Resolved<>(null, "La celda de materia está vacía.");
    for (Entry<SubjectRef> row : catalog.subjects()) {
      if (row.key().equals(key)) return new Resolved<>(row.value(), "");
    }
    return new Resolved<>(
        null,
        "La materia «"
            + name
            + "» no existe en esta carrera. Impórtala desde el catálogo o créala en Estructura"
            + " académica.");
  }

  /** El docente, probando las dos ordenaciones del nombre. */
  public static Resolved<ProfessorRef> resolveProfessor(String cell, Catalog catalog) {
    if (comparable(cell).isEmpty()) return new Resolved<>(null, "La celda de docente está vacía.");
    SplitPerson person = ImportText.splitPersonName(ImportText.extractTitle(cell).rest());
    if (person.firstNames().isEmpty() || person.lastNames().isEmpty()) {
      return new Resolved<>(null, "No se pudo separar nombre y apellido de «" + cell + "».");
    }
    String forward = comparable(person.lastNames() + " " + person.firstNames());
    String backward = comparable(person.firstNames() + " " + person.lastNames());

    for (Entry<ProfessorRef> row : catalog.professors()) {
      if (row.key().equals(forward) || forward.equals(row.alternateKey())) {
        return new Resolved<>(row.value(), "");
      }
    }
    for (Entry<ProfessorRef> row : catalog.professors()) {
      if (row.key().equals(backward) || backward.equals(row.alternateKey())) {
        ProfessorRef found = row.value();
        return new Resolved<>(
            found,
            "El libro escribe «"
                + cell
                + "» en orden nombre-apellido y el registro dice «"
                + found.lastNames()
                + " "
                + found.firstNames()
                + "». Se asociaron por el nombre invertido.");
      }
    }
    // Último recurso: todas las palabras presentes, en cualquier orden.
    String[] words = forward.split(" ");
    for (Entry<ProfessorRef> row : catalog.professors()) {
      boolean allPresent = true;
      for (String word : words) allPresent &= row.key().contains(word);
      if (allPresent) {
        ProfessorRef found = row.value();
        return new Resolved<>(
            found,
            "No se encontró «"
                + cell
                + "» de forma exacta; se asoció a «"
                + found.lastNames()
                + " "
                + found.firstNames()
                + "» comparando solo las palabras. Revísalo.");
      }
    }
    return new Resolved<>(null, "El docente «" + cell + "» no existe todavía.");
  }

  /** El aula; si no coincide exacta se acepta que el nombre registrado sea su prefijo. */
  public static Resolved<SpaceRef> resolveSpace(String cell, Catalog catalog) {
    String key = comparable(cell);
    if (key.isEmpty()) return new Resolved<>(null, "La franja no indica aula.");
    for (Entry<SpaceRef> row : catalog.spaces()) {
      if (row.key().equals(key)) return new Resolved<>(row.value(), "");
    }
    for (Entry<SpaceRef> row : catalog.spaces()) {
      if (!row.key().isEmpty() && key.startsWith(row.key())) {
        return new Resolved<>(
            row.value(),
            "El libro escribe «"
                + cell
                + "» y el aula registrada es «"
                + row.value().name()
                + "» ("
                + row.value().code()
                + "). Se asociaron por coincidencia parcial.");
      }
    }
    return new Resolved<>(null, "El aula «" + cell + "» no existe todavía.");
  }

  /**
   * Choques entre las clases que se van a importar, por índice de la clase que choca.
   *
   * <p>Cada nivel es un horario independiente: solo se comparan clases del mismo nivel, y dentro
   * de él, del mismo día (diurno) o del mismo módulo (nocturno).
   */
  public static Map<Integer, List<Conflict>> detectConflicts(
      List<DraftBlock> blocks, boolean night, Function<DraftBlock, ResolvedKeys> resolve) {
    Map<Integer, List<Integer>> byLevel = new LinkedHashMap<>();
    for (int index = 0; index < blocks.size(); index++) {
      Integer level = blocks.get(index).levelOrder();
      byLevel.computeIfAbsent(level == null ? 0 : level, ignored -> new ArrayList<>()).add(index);
    }

    Map<Integer, List<Conflict>> found = new LinkedHashMap<>();
    for (List<Integer> indices : byLevel.values()) {
      for (int i = 0; i < indices.size(); i++) {
        DraftBlock a = blocks.get(indices.get(i));
        ResolvedKeys ra = resolve.apply(a);
        for (int j = i + 1; j < indices.size(); j++) {
          DraftBlock b = blocks.get(indices.get(j));
          if (overlapGroup(a, night) != overlapGroup(b, night)) continue;
          boolean overlaps =
              minutes(a.startTime()) < minutes(b.endTime())
                  && minutes(b.startTime()) < minutes(a.endTime());
          if (!overlaps) continue;

          ResolvedKeys rb = resolve.apply(b);
          String when =
              (a.dayOfWeek() != null && a.dayOfWeek() > 0 && a.dayOfWeek() < DAY_NAMES.length
                      ? DAY_NAMES[a.dayOfWeek()] + " "
                      : "")
                  + a.startTime().substring(0, 5)
                  + "-"
                  + a.endTime().substring(0, 5);
          boolean sameProfessor =
              ra.professorKey() != null && ra.professorKey().equals(rb.professorKey());
          boolean sameSpace = ra.spaceKey() != null && ra.spaceKey().equals(rb.spaceKey());
          List<Conflict> list =
              found.computeIfAbsent(indices.get(j), ignored -> new ArrayList<>());
          if (sameProfessor) {
            list.add(
                new Conflict(
                    "profesor",
                    indices.get(i),
                    "El docente ya está asignado a «"
                        + a.subjectName()
                        + "» a esa misma hora ("
                        + when
                        + ")."));
          }
          if (sameSpace) {
            list.add(
                new Conflict(
                    "aula",
                    indices.get(i),
                    "El aula ya está ocupada por «"
                        + a.subjectName()
                        + "» a esa misma hora ("
                        + when
                        + ")."));
          }
          if (!sameProfessor && !sameSpace) {
            list.add(
                new Conflict(
                    "solape", indices.get(i), "Se pisa con «" + a.subjectName() + "» (" + when + ")."));
          }
        }
      }
    }
    return found;
  }

  /** En el nocturno lo que separa dos clases es el módulo; en el diurno, el día. */
  private static int overlapGroup(DraftBlock block, boolean night) {
    Integer value = night ? block.moduleNumber() : block.dayOfWeek();
    return value == null ? 0 : value;
  }

  /** Minutos desde medianoche a partir de "HH:mm:ss". */
  private static int minutes(String clock) {
    return Integer.parseInt(clock.substring(0, 2)) * 60 + Integer.parseInt(clock.substring(3, 5));
  }
}
