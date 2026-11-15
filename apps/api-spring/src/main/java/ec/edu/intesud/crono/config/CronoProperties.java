package ec.edu.intesud.crono.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuración propia de la aplicación; los valores salen del archivo .env. */
@ConfigurationProperties(prefix = "crono")
public record CronoProperties(
    String environment,
    String webOrigin,
    String databaseUrl,
    String sessionSecret,
    long accessTokenTtlSeconds,
    long sessionHours,
    Smtp smtp) {

  public boolean production() {
    return "production".equalsIgnoreCase(environment);
  }

  public record Smtp(
      String host, int port, boolean secure, String user, String password, String from) {

    public boolean configured() {
      return hasText(host) && hasText(user) && hasText(password);
    }

    private static boolean hasText(String value) {
      return value != null && !value.isBlank();
    }
  }
}
