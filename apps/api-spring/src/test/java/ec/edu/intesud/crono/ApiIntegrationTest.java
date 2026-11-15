package ec.edu.intesud.crono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.edu.intesud.crono.config.CronoProperties;
import ec.edu.intesud.crono.domain.AcademicCourse;
import ec.edu.intesud.crono.domain.AcademicPeriod;
import ec.edu.intesud.crono.domain.AcademicSpace;
import ec.edu.intesud.crono.domain.AuditLog;
import ec.edu.intesud.crono.domain.Career;
import ec.edu.intesud.crono.domain.CoordinatorCareer;
import ec.edu.intesud.crono.domain.CoordinatorCareerId;
import ec.edu.intesud.crono.domain.DayOfWeek;
import ec.edu.intesud.crono.domain.Level;
import ec.edu.intesud.crono.domain.Professor;
import ec.edu.intesud.crono.domain.Schedule;
import ec.edu.intesud.crono.domain.ScheduleBlock;
import ec.edu.intesud.crono.domain.ScheduleVersion;
import ec.edu.intesud.crono.domain.Shift;
import ec.edu.intesud.crono.domain.SpaceType;
import ec.edu.intesud.crono.domain.Subject;
import ec.edu.intesud.crono.domain.User;
import ec.edu.intesud.crono.domain.UserRole;
import ec.edu.intesud.crono.mail.MailService;
import ec.edu.intesud.crono.repository.UserRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

/**
 * Arranca la aplicación completa contra un PostgreSQL real y desechable. Comprueba que:
 *
 * <ul>
 *   <li>el esquema base de Flyway se aplica y las entidades JPA coinciden con él (ddl validate);
 *   <li>el inicio de sesión funciona de punta a punta y responde igual que la API de NestJS;
 *   <li>los enums, horas, fechas y JSON se guardan como los espera el resto del sistema.
 * </ul>
 */
@SpringBootTest
class ApiIntegrationTest extends IntegrationTestBase {

  @org.springframework.beans.factory.annotation.Autowired JwtDecoder jwtDecoder;

  /** Token firmado con Node (HS256, mismo formato que @nestjs/jwt); vence en el año 2100. */
  private static final String NODE_TOKEN =
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
          + "eyJzdWIiOiIxMTExMTExMS0yMjIyLTQzMzMtODQ0NC01NTU1NTU1NTU1NTUiLCJ0eXBlIjoiYWNjZXNzIiwiaWF0IjoxNzAwMDAwMDAwLCJleHAiOjQxMDI0NDQ4MDB9."
          + "J_4SuRnK0rD19dG3SEehKvJt9PTu24bhTkqOY2OD6vQ";

  @Test
  void healthIsPublic() throws Exception {
    mvc.perform(get("/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.service").value("crono-intesud-api"));
  }

  @Test
  void protectedRouteWithoutTokenAnswersLikeNest() throws Exception {
    mvc.perform(get("/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Acceso no autorizado"))
        .andExpect(jsonPath("$.error").value("Unauthorized"))
        .andExpect(jsonPath("$.statusCode").value(401));

    mvc.perform(get("/auth/me").header("Authorization", "Bearer no-es-un-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Sesión inválida o vencida"));
  }

  @Test
  void invalidBodyIsRejected() throws Exception {
    mvc.perform(
            post("/auth/verify-code")
                .with(request -> { request.setRemoteAddr("10.0.0.9"); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nationalId\":\"1712345678\",\"code\":\"12\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Bad Request"))
        .andExpect(jsonPath("$.statusCode").value(400))
        .andExpect(jsonPath("$.message").isArray());
  }

  @Test
  void acceptsAccessTokensSignedByTheNestApi() {
    Jwt jwt = jwtDecoder.decode(NODE_TOKEN);
    assertThat(jwt.getSubject()).isEqualTo("11111111-2222-4333-8444-555555555555");
    assertThat(jwt.getClaimAsString("type")).isEqualTo("access");
  }

  @Test
  void passwordlessLoginFlow() throws Exception {
    User coordinator = new User();
    coordinator.setNationalId("1712345678");
    coordinator.setFirstNames("Ana");
    coordinator.setLastNames("Pérez");
    coordinator.setEmail("ana@example.com");
    coordinator.setRole(UserRole.COORDINATOR);
    UUID userId = users.save(coordinator).getId();
    UUID careerId =
        inTransaction(
            () -> {
              Career career = new Career();
              career.setName("Desarrollo de Software");
              career.setSlug("desarrollo-de-software");
              entityManager.persist(career);
              CoordinatorCareer link = new CoordinatorCareer();
              link.setId(new CoordinatorCareerId(userId, career.getId()));
              link.setUser(entityManager.getReference(User.class, userId));
              link.setCareer(career);
              entityManager.persist(link);
              return career.getId();
            });

    // 1. Pedir el código: misma respuesta exista o no la cédula.
    mvc.perform(json(post("/auth/request-code"), Map.of("nationalId", "1712345678")))
        .andExpect(status().isAccepted())
        .andExpect(
            jsonPath("$.message")
                .value("Si la cédula está autorizada, recibirás un código de acceso."));
    String code = mail.lastCode;
    assertThat(code).matches("\\d{6}");

    // 2. Un código incorrecto se rechaza y cuenta como intento.
    String wrongCode = code.equals("000000") ? "000001" : "000000";
    mvc.perform(
            json(post("/auth/verify-code"), Map.of("nationalId", "1712345678", "code", wrongCode)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Código inválido o vencido"));
    assertThat(jdbc().queryForObject("select attempts from login_codes", Integer.class))
        .isEqualTo(1);

    // 3. El código correcto abre la sesión.
    MvcResult login =
        mvc.perform(
                json(post("/auth/verify-code"), Map.of("nationalId", "1712345678", "code", code)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.id").value(userId.toString()))
            .andExpect(jsonPath("$.user.role").value("COORDINATOR"))
            .andExpect(jsonPath("$.user.careerIds[0]").value(careerId.toString()))
            .andReturn();
    String accessToken = field(login, "accessToken");
    String setCookie = login.getResponse().getHeader("Set-Cookie");
    assertThat(setCookie)
        .startsWith("crono_refresh=")
        .contains("Path=/api/v1/auth")
        .contains("HttpOnly")
        .contains("SameSite=Lax");
    Cookie refreshCookie = login.getResponse().getCookie("crono_refresh");

    // El código ya no puede reutilizarse.
    mvc.perform(json(post("/auth/verify-code"), Map.of("nationalId", "1712345678", "code", code)))
        .andExpect(status().isUnauthorized());

    // 4. El token de acceso identifica al usuario.
    mvc.perform(get("/auth/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nationalId").value("1712345678"))
        .andExpect(jsonPath("$.firstNames").value("Ana"))
        // Exactamente los campos que devolvía NestJS, sin extras.
        .andExpect(jsonPath("$.length()").value(7));

    // 5. Renovar rota el token de refresco: el anterior deja de servir.
    MvcResult refreshed =
        mvc.perform(post("/auth/refresh").cookie(refreshCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();
    Cookie rotatedCookie = refreshed.getResponse().getCookie("crono_refresh");
    assertThat(rotatedCookie.getValue()).isNotEqualTo(refreshCookie.getValue());
    mvc.perform(post("/auth/refresh").cookie(refreshCookie))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Sesión inválida o vencida"));

    // 6. Cerrar sesión revoca la sesión, aunque el token de acceso enviado no sirva.
    mvc.perform(
            post("/auth/logout").cookie(rotatedCookie).header("Authorization", "Bearer vencido"))
        .andExpect(status().isNoContent());
    mvc.perform(post("/auth/refresh").cookie(rotatedCookie)).andExpect(status().isUnauthorized());

    assertThat(
            jdbc()
                .queryForList(
                    "select action from audit_logs where action like 'AUTH_%'", String.class))
        .containsExactlyInAnyOrder("AUTH_CODE_SENT", "AUTH_LOGIN");
  }

  @Test
  void entitiesAreStoredTheWayPrismaStoresThem() {
    UUID blockId =
        inTransaction(
            () -> {
              User admin = new User();
              admin.setNationalId("0999999999");
              admin.setFirstNames("Admin");
              admin.setLastNames("Principal");
              admin.setEmail("admin@example.com");
              admin.setRole(UserRole.ADMIN);
              entityManager.persist(admin);

              Career career = new Career();
              career.setName("Redes");
              career.setSlug("redes");
              entityManager.persist(career);

              Level level = new Level();
              level.setCareer(career);
              level.setName("Primer nivel");
              level.setSortOrder(1);
              entityManager.persist(level);

              Subject subject = new Subject();
              subject.setCareer(career);
              subject.setLevel(level);
              subject.setName("Programación I");
              entityManager.persist(subject);

              Professor professor = new Professor();
              professor.setFirstNames("Luis");
              professor.setLastNames("Mora");
              entityManager.persist(professor);

              AcademicSpace space = new AcademicSpace();
              space.setName("Laboratorio 1");
              space.setCode("LAB-1");
              space.setType(SpaceType.LABORATORY);
              entityManager.persist(space);

              AcademicPeriod period = new AcademicPeriod();
              period.setName("2026-2027");
              period.setStartDate(LocalDate.of(2026, 10, 5));
              period.setEndDate(LocalDate.of(2027, 2, 26));
              entityManager.persist(period);

              AcademicCourse course = new AcademicCourse();
              course.setPeriod(period);
              course.setLevel(level);
              course.setName("Primero A");
              entityManager.persist(course);

              Schedule schedule = new Schedule();
              schedule.setCourse(course);
              schedule.setShift(Shift.NIGHT);
              entityManager.persist(schedule);

              ScheduleVersion version = new ScheduleVersion();
              version.setSchedule(schedule);
              version.setVersion(1);
              version.setCreatedBy(admin);
              version.setModifiedBy(admin);
              entityManager.persist(version);

              ScheduleBlock block = new ScheduleBlock();
              block.setVersion(version);
              block.setSubject(subject);
              block.setProfessor(professor);
              block.setSpace(space);
              block.setDayOfWeek(DayOfWeek.MONDAY);
              block.setStartTime(LocalTime.of(18, 30));
              block.setEndTime(LocalTime.of(20, 0));
              block.setValidFrom(LocalDate.of(2026, 10, 5));
              entityManager.persist(block);

              AuditLog entry = new AuditLog();
              entry.setUser(admin);
              entry.setAction("TEST");
              entry.setEntityType("ScheduleBlock");
              entry.setNewData("{\"moduleNumber\": 1}");
              entityManager.persist(entry);
              return block.getId();
            });

    Map<String, Object> row =
        jdbc()
            .queryForMap(
                "select b.start_time::text as start_time, b.end_time::text as end_time,"
                    + " b.day_of_week::text as day_of_week, b.valid_from::text as valid_from,"
                    + " s.shift::text as shift, v.status::text as status, l.\"order\" as level_order,"
                    + " p.schedule_color"
                    + " from schedule_blocks b"
                    + " join schedule_versions v on v.id = b.version_id"
                    + " join schedules s on s.id = v.schedule_id"
                    + " join subjects su on su.id = b.subject_id"
                    + " join levels l on l.id = su.level_id"
                    + " join professors p on p.id = b.professor_id"
                    + " where b.id = ?",
                blockId);
    assertThat(row)
        .containsEntry("start_time", "18:30:00")
        .containsEntry("end_time", "20:00:00")
        .containsEntry("day_of_week", "MONDAY")
        .containsEntry("valid_from", "2026-10-05")
        .containsEntry("shift", "NIGHT")
        .containsEntry("status", "DRAFT")
        .containsEntry("level_order", 1)
        .containsEntry("schedule_color", "#A9CBE4");
    assertThat(
            jdbc()
                .queryForObject(
                    "select new_data->>'moduleNumber' from audit_logs where action = 'TEST'",
                    String.class))
        .isEqualTo("1");

    // Y se leen de vuelta con los mismos valores.
    ScheduleBlock stored =
        inTransaction(
            () -> {
              entityManager.clear();
              ScheduleBlock block = entityManager.find(ScheduleBlock.class, blockId);
              block.getSpace().getType(); // fuerza la carga perezosa
              return block;
            });
    assertThat(stored.getStartTime()).isEqualTo(LocalTime.of(18, 30));
    assertThat(stored.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    assertThat(stored.getSpace().getType()).isEqualTo(SpaceType.LABORATORY);
  }


  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder json(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
      Map<String, String> body) {
    StringBuilder content = new StringBuilder("{");
    body.forEach(
        (key, value) -> {
          if (content.length() > 1) content.append(',');
          content.append('"').append(key).append("\":\"").append(value).append('"');
        });
    return request.contentType(MediaType.APPLICATION_JSON).content(content.append('}').toString());
  }
}
