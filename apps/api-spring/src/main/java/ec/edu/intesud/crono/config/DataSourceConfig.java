package ec.edu.intesud.crono.config;

import com.zaxxer.hikari.HikariDataSource;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Crea la conexión a PostgreSQL a partir de DATABASE_URL, que tiene el formato de Prisma
 * (postgresql://usuario:clave@host:5432/base?schema=public) y no el de JDBC.
 */
@Configuration
public class DataSourceConfig {

  private static final Pattern PRISMA_URL =
      Pattern.compile(
          "^postgres(?:ql)?://(?:([^:@/]+)(?::([^@/]*))?@)?([^:/?]+)(?::(\\d+))?/([^?]+)(?:\\?(.*))?$");

  @Bean
  public DataSource dataSource(CronoProperties properties) {
    HikariDataSource dataSource = new HikariDataSource();
    String url = properties.databaseUrl();
    if (url == null || url.isBlank()) {
      throw new IllegalStateException("Falta DATABASE_URL en el archivo .env");
    }
    if (url.startsWith("jdbc:")) {
      dataSource.setJdbcUrl(url);
      return dataSource;
    }

    Matcher matcher = PRISMA_URL.matcher(url.trim());
    if (!matcher.matches()) {
      throw new IllegalStateException(
          "DATABASE_URL debe tener el formato postgresql://usuario:clave@host:puerto/base");
    }
    String port = matcher.group(4) == null ? "5432" : matcher.group(4);
    String jdbcUrl = "jdbc:postgresql://" + matcher.group(3) + ":" + port + "/" + matcher.group(5);
    String schema = queryParameter(matcher.group(6), "schema");
    if (schema != null) {
      jdbcUrl += "?currentSchema=" + schema;
    }
    dataSource.setJdbcUrl(jdbcUrl);
    if (matcher.group(1) != null) {
      dataSource.setUsername(decode(matcher.group(1)));
    }
    if (matcher.group(2) != null) {
      dataSource.setPassword(decode(matcher.group(2)));
    }
    return dataSource;
  }

  private static String queryParameter(String query, String name) {
    if (query == null) return null;
    for (String pair : query.split("&")) {
      int separator = pair.indexOf('=');
      if (separator > 0 && pair.substring(0, separator).equals(name)) {
        return pair.substring(separator + 1);
      }
    }
    return null;
  }

  private static String decode(String value) {
    // Solo se decodifican los %XX; un "+" literal en la clave se conserva.
    return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
  }
}
