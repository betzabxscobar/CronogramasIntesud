package ec.edu.intesud.crono.excel;

import ec.edu.intesud.crono.domain.SpaceType;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalización de los valores que llegan del libro Excel de INTESUD.
 *
 * <p>El libro no es una fuente limpia: mezcla títulos abreviados, ordinales con punto, celdas de
 * relleno ("---", "- Sin Profesor -", "Aula XX") y dos docentes en una misma celda. Aquí se
 * decide qué es un dato, qué es relleno y qué es ambiguo y necesita revisión humana.
 */
public final class ImportText {

  private static final Pattern ODD_SPACES = Pattern.compile("[\\u00a0\\u2007\\u202f]");
  private static final Pattern SPACES = Pattern.compile("(?U)\\s+");

  private ImportText() {}

  /** Quita acentos y mayúsculas para poder comparar sin falsos negativos. */
  public static String comparable(String value) {
    if (value == null || value.isEmpty()) return "";
    String plain = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("[\\u0300-\\u036f]", "");
    return tidy(plain).toLowerCase(Locale.ROOT);
  }

  /** Limpieza de presentación: conserva acentos, quita espacios raros. */
  public static String tidy(String value) {
    if (value == null || value.isEmpty()) return "";
    return SPACES.matcher(ODD_SPACES.matcher(value).replaceAll(" ")).replaceAll(" ").strip();
  }

  /** Texto de un valor guardado en JSON (texto, número o booleano); "" para lo demás. */
  public static String asText(Object value) {
    if (value instanceof String text) return tidy(text);
    if (value instanceof Number number) {
      double asDouble = number.doubleValue();
      return Double.isFinite(asDouble) ? SheetReader.numberText(asDouble) : "";
    }
    if (value instanceof Boolean flag) return flag.toString();
    return "";
  }

  // ----- Relleno -----

  /** Patrones de relleno del libro: no son datos. Se comparan sin acentos ni mayúsculas. */
  private static final List<Pattern> FILLER =
      List.of(
          Pattern.compile("^-+$"), // --- o --
          Pattern.compile("^-\\s*sin\\s+profesor\\s*-?$"), // - Sin Profesor -
          Pattern.compile("^-\\s*sin\\s+asignar\\s*-?$"),
          Pattern.compile("^profesor\\s+n\\.?n\\.?$"), // Profesor N.N.
          Pattern.compile("^profesor\\s+xx$"),
          Pattern.compile("^aula\\s+xx$"), // Aula XX
          Pattern.compile("^lista\\s+de\\s+sus\\s+profesores$"), // etiqueta usada como valor
          Pattern.compile("^escoja\\b"), // "ESCOJA PRIMERO LA ESCUELA"
          Pattern.compile("^elige\\b"),
          Pattern.compile("^seleccione\\b"),
          Pattern.compile("^\\d+(\\.\\d+)?$")); // 0, 1, 3.3333: créditos o índices, nunca un nombre

  private static final Pattern HAS_DASH = Pattern.compile("[-–—]");
  /** Separadores decorados entre bloques: " --- v --- ", " --- ^ --- ", " --- | --- ". */
  private static final Pattern DECORATED = Pattern.compile("^[\\s\\-–—|^~vVx×.·]+$");

  /** true si la celda es relleno del libro (o está vacía) y no un dato. */
  public static boolean isFiller(String value) {
    String text = comparable(value);
    if (text.isEmpty()) return true;
    if (HAS_DASH.matcher(text).find() && DECORATED.matcher(text).matches()) return true;
    return FILLER.stream().anyMatch(pattern -> pattern.matcher(text).find());
  }

  // ----- Títulos y nombres -----

  private record TitleRule(Pattern pattern, String title) {}

  private static TitleRule title(String regex, String title) {
    return new TitleRule(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), title);
  }

  /** Títulos que usa el libro, traducidos a las abreviaturas que entiende la aplicación. */
  private static final List<TitleRule> TITLES =
      List.of(
          title("^tnlga\\.?\\s*s\\.?u\\.?$", "Tnlga. S.U."),
          title("^tnlgo\\.?\\s*s\\.?u\\.?$", "Tnlgo. S.U."),
          title("^tnlga\\.?\\(e\\)\\.?$", "Tnlga."),
          title("^tnlgo\\.?\\(e\\)\\.?$", "Tnlgo."),
          title("^tnlga\\.?$", "Tnlga."),
          title("^tnlgo\\.?$", "Tnlgo."),
          title("^msc\\.?$", "MSc."),
          title("^phd\\.?$", "PhD."),
          title("^mtro?\\.?$", "Mtr."),
          title("^mtra\\.?$", "Mtr."),
          title("^ing\\.?$", "Ing."),
          title("^lcdo\\.?$", "Lcdo."),
          title("^lcda\\.?$", "Lcda."),
          title("^arq\\.?$", "Arq."),
          title("^dr\\.?$", "Dr."),
          title("^dra\\.?$", "Dra."),
          title("^prof\\.?$", "Prof."),
          title("^profa\\.?$", "Profa."));

  /** El título siempre es el primer tramo terminado en punto. */
  private static final Pattern TITLE_AND_REST =
      Pattern.compile("^([A-Za-zÁÉÍÓÚÑáéíóúñ().\\s]{1,24}?\\.)\\s+(.+)$");

  /** Título abreviado (o null si la celda no trae uno conocido) y el resto del texto. */
  public record ExtractedTitle(String title, String rest) {}

  public static ExtractedTitle extractTitle(String raw) {
    String text = tidy(raw);
    if (text.isEmpty()) return new ExtractedTitle(null, "");
    Matcher match = TITLE_AND_REST.matcher(text);
    if (!match.matches()) return new ExtractedTitle(null, text);
    String candidate = match.group(1).strip();
    for (TitleRule rule : TITLES) {
      if (rule.pattern().matcher(candidate).find()) {
        return new ExtractedTitle(rule.title(), tidy(match.group(2)));
      }
    }
    return new ExtractedTitle(null, text);
  }

  /** Nombre separado; "ambiguous" indica que hay que revisarlo a mano (con el motivo). */
  public record SplitPerson(String firstNames, String lastNames, boolean ambiguous, String reason) {}

  /**
   * El libro escribe "Apellido Nombre", con excepciones: dos personas en una celda separadas por
   * «/» o el orden invertido. Cuando no hay certeza se marca para revisarlo en vez de adivinar.
   */
  public static SplitPerson splitPersonName(String rest) {
    String text = tidy(rest);
    if (text.isEmpty()) return new SplitPerson("", "", true, "Nombre vacío");
    if (text.contains("/")) {
      return new SplitPerson(
          "",
          "",
          true,
          "La celda contiene más de un docente separado por «/»: se debe registrar por separado");
    }
    String[] words = text.split(" ");
    if (words.length == 1) {
      return new SplitPerson(
          words[0], "", true, "Solo una palabra: no se puede separar nombre y apellido");
    }
    if (words.length == 2) {
      // "Morales Gabriela" -> apellido Morales, nombre Gabriela
      return new SplitPerson(words[1], words[0], false, null);
    }
    // Tres o más: "Flores Juan José" (apellido + nombre doble) es lo normal; se asume y se avisa.
    return new SplitPerson(
        text.substring(words[0].length() + 1),
        words[0],
        true,
        "El nombre tiene 3 o más palabras: se asumió que la primera es el apellido. Confirma si es"
            + " correcto.");
  }

  // ----- Niveles -----

  private static final Map<String, Integer> ORDINAL_WORDS =
      Map.of("primero", 1, "segundo", 2, "tercero", 3, "cuarto", 4, "quinto", 5);
  private static final Pattern ORDINAL = Pattern.compile("^(\\d{1,2})");

  /** "1ro.", "2do." o "PRIMERO" -> número de nivel; null si no se reconoce. */
  public static Integer levelLabelToOrder(String label) {
    String text = comparable(label);
    if (text.isEmpty()) return null;
    Matcher ordinal = ORDINAL.matcher(text);
    if (ordinal.find()) {
      int value = Integer.parseInt(ordinal.group(1));
      if (value >= 1 && value <= 20) return value;
    }
    return ORDINAL_WORDS.get(text.replaceAll("[.\\s°º]", ""));
  }

  /** Respaldo: el segundo tramo del código cuando es un número, p. ej. "DS-01-001" -> 1. */
  public static Integer levelFromSubjectCode(String code) {
    String text = comparable(code);
    if (text.isEmpty()) return null;
    String[] parts = text.split("-");
    if (parts.length < 3 || !parts[1].matches("\\d{1,2}")) return null;
    int candidate = Integer.parseInt(parts[1]);
    return candidate >= 1 && candidate <= 20 ? candidate : null;
  }

  // ----- Aulas -----

  /** Deriva del nombre un código de aula estable y que no esté ya en "taken". */
  public static String spaceCodeFromName(String name, Set<String> taken) {
    String base =
        comparable(name)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "")
            .toUpperCase(Locale.ROOT);
    if (base.length() > 24) base = base.substring(0, 24);
    if (base.isEmpty()) base = "ESPACIO";
    String code = base;
    for (int n = 2; taken.contains(code); n++) code = base + "-" + n;
    taken.add(code);
    return code;
  }

  private static final Pattern LABORATORY =
      Pattern.compile("laboratorio|lab\\s*\\d|taller", Pattern.CASE_INSENSITIVE);

  /** "Aula 3-A Laboratorio 01" es laboratorio; "Aula 1-D" es aula. */
  public static SpaceType spaceTypeFromName(String name) {
    return LABORATORY.matcher(name).find() ? SpaceType.LABORATORY : SpaceType.CLASSROOM;
  }
}
