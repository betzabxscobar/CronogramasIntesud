package ec.edu.intesud.crono.excel;

import java.util.List;
import java.util.Locale;

/**
 * Colores del libro de horarios. El color de una clase es el del docente (scheduleColor), el
 * mismo que se ve en la plataforma: el libro no inventa colores propios.
 */
public final class ScheduleColors {

  /** Los seis tonos del encabezado de módulos del horario nocturno. */
  public static final List<String> MODULE_TONES =
      List.of("#385724", "#548235", "#BFBFBF", "#806000", "#BF9000", "#773F19");

  private ScheduleColors() {}

  /** Normaliza un color a "#RRGGBB" en mayúsculas, o null si no es utilizable. */
  public static String normalize(String color) {
    if (color == null || color.isEmpty()) return null;
    String clean = color.strip().replaceFirst("^#", "").toUpperCase(Locale.ROOT);
    return clean.matches("[0-9A-F]{6}") ? "#" + clean : null;
  }

  /** Color del texto que se lee bien sobre ese fondo: oscuro sobre claro y blanco sobre oscuro. */
  public static String readableInk(String background) {
    String hex = background.replace("#", "");
    int red = Integer.parseInt(hex.substring(0, 2), 16);
    int green = Integer.parseInt(hex.substring(2, 4), 16);
    int blue = Integer.parseInt(hex.substring(4, 6), 16);
    return red * 0.299 + green * 0.587 + blue * 0.114 > 155 ? "#102D41" : "#FFFFFF";
  }
}
