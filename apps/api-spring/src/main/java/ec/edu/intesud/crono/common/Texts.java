package ec.edu.intesud.crono.common;

import java.text.Normalizer;
import java.util.Locale;

/** Limpieza de textos que llegan del cliente. */
public final class Texts {

  private Texts() {}

  /** Quita espacios de los extremos y deja un solo espacio entre palabras. */
  public static String clean(String value) {
    return value.strip().replaceAll("\\s+", " ");
  }

  /** Recorta; devuelve null si queda vacío. */
  public static String trimToNull(String value) {
    if (value == null) return null;
    String trimmed = value.strip();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public static String withoutAccents(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("[\\u0300-\\u036f]", "");
  }

  /** "Desarrollo de Software" -> "desarrollo-de-software". */
  public static String slugify(String value) {
    return withoutAccents(value)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-+|-+$", "");
  }

  /** Patrón para LIKE con el texto escapado: busca "contiene", sin distinguir mayúsculas. */
  public static String containsPattern(String term) {
    String escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped.toLowerCase(Locale.ROOT) + "%";
  }
}
