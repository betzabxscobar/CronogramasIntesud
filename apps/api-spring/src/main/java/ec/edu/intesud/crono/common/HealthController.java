package ec.edu.intesud.crono.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  /** Comprueba que la API está disponible. Ruta pública. */
  @GetMapping("/health")
  public Map<String, String> health() {
    Map<String, String> body = new LinkedHashMap<>();
    body.put("status", "ok");
    body.put("service", "crono-intesud-api");
    return body;
  }
}
