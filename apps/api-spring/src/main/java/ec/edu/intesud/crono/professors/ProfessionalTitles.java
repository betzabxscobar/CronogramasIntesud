package ec.edu.intesud.crono.professors;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Títulos profesionales: se guardan completos ("Ingeniero/a en Sistemas") y se muestran además
 * con su abreviatura ("Ing."). Mismas reglas que professional-title.ts de la API de NestJS.
 */
public final class ProfessionalTitles {

  private record Rule(Pattern pattern, String replacement) {}

  private static Rule rule(String regex, String replacement) {
    return new Rule(
        Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), replacement);
  }

  /** Título completo -> abreviatura. El orden importa: primero los más específicos. */
  private static final List<Rule> ABBREVIATIONS =
      List.of(
          rule("^magister scientiae\\b", "MSc."),
          rule("^master of science\\b", "MSc."),
          rule("^máster of science\\b", "MSc."),
          rule("^doctor of philosophy\\b", "PhD."),
          rule("^tecn[oó]logo superior universitario\\b", "Tnlgo. S.U."),
          rule("^tecn[oó]loga superior universitaria\\b", "Tnlga. S.U."),
          rule("^t[eé]cnic[oa] superior\\b", "Téc."),
          rule("^ingenier[oa]\\b", "Ing."),
          rule("^licenciado\\b", "Lcdo."),
          rule("^licenciada\\b", "Lcda."),
          rule("^mag[ií]ster\\b", "Mtr."),
          rule("^máster\\b", "Mtr."),
          rule("^master\\b", "Mtr."),
          rule("^doctor\\b", "Dr."),
          rule("^doctora\\b", "Dra."),
          rule("^arquitect[oa]\\b", "Arq."),
          rule("^abogad[oa]\\b", "Abg."),
          rule("^economista\\b", "Econ."),
          rule("^psic[oó]logo\\b", "Psic."),
          rule("^psic[oó]loga\\b", "Psic."),
          rule("^m[eé]dic[oa]\\b", "Méd."),
          rule("^odont[oó]log[oa]\\b", "Odont."),
          rule("^profesor\\b", "Prof."),
          rule("^profesora\\b", "Profa."),
          rule("^tecn[oó]logo\\b", "Tnlgo."),
          rule("^tecn[oó]loga\\b", "Tnlga."));

  /** Abreviatura -> título completo. */
  private static final List<Rule> EXPANSIONS =
      List.of(
          rule("^Tnlgo\\. S\\.U\\.", "Tecnólogo Superior Universitario"),
          rule("^Tnlga\\. S\\.U\\.", "Tecnóloga Superior Universitaria"),
          rule("^MSc\\.", "Master of Science"),
          rule("^PhD\\.", "Doctor of Philosophy"),
          rule("^Tnlgo\\.", "Tecnólogo"),
          rule("^Tnlga\\.", "Tecnóloga"),
          rule("^Lcdo\\.", "Licenciado"),
          rule("^Lcda\\.", "Licenciada"),
          rule("^Mtr\\.", "Magíster"),
          rule("^Ing\\.", "Ingeniero/a"),
          rule("^Dra\\.", "Doctora"),
          rule("^Dr\\.", "Doctor"),
          rule("^Arq\\.", "Arquitecto/a"),
          rule("^Abg\\.", "Abogado/a"),
          rule("^Econ\\.", "Economista"),
          rule("^Psic\\.", "Psicólogo/a"),
          rule("^Méd\\.", "Médico/a"),
          rule("^Odont\\.", "Odontólogo/a"),
          rule("^Profa\\.", "Profesora"),
          rule("^Prof\\.", "Profesor"),
          rule("^Téc\\.", "Técnico/a Superior"));

  private ProfessionalTitles() {}

  /** "Ingeniero en Sistemas" -> "Ing. en Sistemas". */
  public static String abbreviate(String title) {
    return apply(ABBREVIATIONS, title);
  }

  /** "Ing. en Sistemas" -> "Ingeniero/a en Sistemas". */
  public static String expand(String title) {
    return apply(EXPANSIONS, title);
  }

  /** Solo la abreviatura inicial ("Ing."), o null si el título no tiene una conocida. */
  public static String prefix(String title) {
    String abbreviated = abbreviate(title);
    if (abbreviated == null) return null;
    for (Rule rule : EXPANSIONS) {
      Matcher matcher = rule.pattern().matcher(abbreviated);
      if (matcher.find()) return matcher.group();
    }
    return null;
  }

  private static String apply(List<Rule> rules, String title) {
    String normalized = title == null ? "" : title.strip();
    if (normalized.isEmpty()) return null;
    for (Rule rule : rules) {
      Matcher matcher = rule.pattern().matcher(normalized);
      if (matcher.find()) {
        return matcher.replaceFirst(Matcher.quoteReplacement(rule.replacement()));
      }
    }
    return normalized;
  }
}
