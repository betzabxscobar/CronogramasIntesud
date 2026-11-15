package ec.edu.intesud.crono;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CronoApiApplication {

  static {
    // La base guarda fechas y horas en UTC (así las escribe Prisma). Fijar la zona
    // de la JVM evita que las horas de clase (columnas TIME) se desplacen al leerlas.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  public static void main(String[] args) {
    SpringApplication.run(CronoApiApplication.class, args);
  }
}
