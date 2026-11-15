package ec.edu.intesud.crono;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import ec.edu.intesud.crono.config.CronoProperties;
import ec.edu.intesud.crono.domain.Career;
import ec.edu.intesud.crono.domain.CoordinatorCareer;
import ec.edu.intesud.crono.domain.CoordinatorCareerId;
import ec.edu.intesud.crono.domain.User;
import ec.edu.intesud.crono.domain.UserRole;
import ec.edu.intesud.crono.mail.MailService;
import ec.edu.intesud.crono.repository.UserRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base de las pruebas de integración: arranca la aplicación completa contra un PostgreSQL real y
 * desechable (compartido por todas las clases de prueba) con el esquema creado por Flyway.
 */
@SpringBootTest
@Import(IntegrationTestBase.MailTestConfiguration.class)
public abstract class IntegrationTestBase {

  protected static final String SECRET = "test-secret-with-at-least-32-characters!!";

  private static final EmbeddedPostgres POSTGRES = startPostgres();
  private static final AtomicInteger SEQUENCE = new AtomicInteger(1);

  private static EmbeddedPostgres startPostgres() {
    try {
      return EmbeddedPostgres.builder().start();
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add(
        "crono.database-url",
        () -> "postgresql://postgres@localhost:" + POSTGRES.getPort() + "/postgres?schema=public");
    registry.add("crono.session-secret", () -> SECRET);
    registry.add("spring.flyway.enabled", () -> "true");
  }

  /** Sustituye el envío de correo: guarda el último código en lugar de enviarlo. */
  public static class CapturingMailService extends MailService {
    public volatile String lastCode;

    CapturingMailService(CronoProperties properties) {
      super(properties);
    }

    @Override
    public boolean sendLoginCode(String email, String displayName, String code) {
      lastCode = code;
      return true;
    }
  }

  @TestConfiguration
  static class MailTestConfiguration {
    @Bean
    @Primary
    CapturingMailService capturingMailService(CronoProperties properties) {
      return new CapturingMailService(properties);
    }
  }

  @Autowired protected WebApplicationContext context;
  @Autowired protected CapturingMailService mail;
  @Autowired protected UserRepository users;
  @Autowired protected EntityManager entityManager;
  @Autowired protected PlatformTransactionManager transactionManager;
  @Autowired protected DataSource dataSource;
  @Autowired protected JwtEncoder jwtEncoder;

  protected MockMvc mvc;

  @BeforeEach
  void setUpMockMvc() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  protected <T> T inTransaction(Supplier<T> work) {
    return new TransactionTemplate(transactionManager).execute(status -> work.get());
  }

  protected JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  /** Crea un usuario con datos únicos y devuelve su id. */
  protected UUID createUser(UserRole role, UUID... careerIds) {
    int number = SEQUENCE.getAndIncrement();
    return inTransaction(
        () -> {
          User user = new User();
          user.setNationalId(String.format("09%08d", number));
          user.setFirstNames("Usuario");
          user.setLastNames("Prueba " + number);
          user.setEmail("usuario" + number + "@example.com");
          user.setRole(role);
          entityManager.persist(user);
          for (UUID careerId : careerIds) {
            CoordinatorCareer link = new CoordinatorCareer();
            link.setId(new CoordinatorCareerId(user.getId(), careerId));
            link.setUser(user);
            link.setCareer(entityManager.getReference(Career.class, careerId));
            entityManager.persist(link);
          }
          return user.getId();
        });
  }

  /** Cabecera Authorization con un token de acceso válido para ese usuario. */
  protected String bearer(UUID userId) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(userId.toString())
            .claim("type", "access")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
    return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  /** Petición autenticada con cuerpo JSON (se admiten comillas simples para escribirlo cómodo). */
  protected static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder request, String authorization, String body) {
    return request
        .header("Authorization", authorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body.replace('\'', '"'));
  }

  protected static String field(MvcResult result, String name) throws Exception {
    return String.valueOf(
        (Object)
            com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$." + name));
  }
}
