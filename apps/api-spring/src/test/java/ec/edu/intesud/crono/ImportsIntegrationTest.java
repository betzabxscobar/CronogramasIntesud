package ec.edu.intesud.crono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.edu.intesud.crono.domain.UserRole;
import ec.edu.intesud.crono.exports.ExportModel;
import ec.edu.intesud.crono.exports.ExportModel.Catalog;
import ec.edu.intesud.crono.exports.ExportModel.ExportBlock;
import ec.edu.intesud.crono.exports.ExportModel.ExportLevel;
import ec.edu.intesud.crono.exports.ExportModel.ExportSubject;
import ec.edu.intesud.crono.exports.ScheduleWorkbookWriter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/** Importación del libro de horarios: vista previa, confirmación, consulta y cancelación. */
class ImportsIntegrationTest extends IntegrationTestBase {

  private static final String XLSX =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

  @Test
  void workbookIsPreviewedAndApplied() throws Exception {
    String admin = bearer(createUser(UserRole.ADMIN));
    String careerId =
        field(mvc.perform(json(post("/careers"), admin, "{'name':'Redes y Datos'}")).andReturn(), "id");
    String coordinator = bearer(createUser(UserRole.COORDINATOR, UUID.fromString(careerId)));
    String periodId =
        field(
            mvc.perform(
                    json(
                        post("/academic-periods"),
                        admin,
                        "{'name':'Periodo de importación 2033','startDate':'2033-03-07',"
                            + "'endDate':'2033-07-29'}"))
                .andExpect(status().isCreated())
                .andReturn(),
            "id");

    // El libro se genera con el mismo escritor de la exportación: lo exportado se puede importar.
    byte[] book =
        ScheduleWorkbookWriter.write(
            new ExportModel(
                "Redes y Datos",
                "Periodo de importación 2033",
                "",
                List.of(),
                List.of(new ExportLevel(1, "PRIMERO")),
                List.of(
                    new Catalog(
                        "Redes y Datos",
                        List.of(
                            new ExportSubject("Redes básicas", "RD101", 1, 4),
                            new ExportSubject("Bases de datos", "RD102", 1, 3)))),
                List.of("Ing. Salazar Marta", "Mtr. Paredes Julio"),
                List.of("Aula 9-Z"),
                List.of(
                    new ExportBlock(
                        1, 1, "08:00", "09:00", null, "Redes básicas", null, "Ing. Salazar Marta",
                        "Aula 9-Z"),
                    new ExportBlock(
                        1, 2, "09:00", "10:00", null, "Bases de datos", null, "Mtr. Paredes Julio",
                        "Aula 9-Z")),
                List.of(),
                List.of()));
    MockMultipartFile file = new MockMultipartFile("file", "Horarios Redes.xlsx", XLSX, book);

    // ---------- Validaciones ----------
    mvc.perform(multipart("/imports/preview").file(file).header("Authorization", coordinator))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message[0]").value("careerId must be a UUID"));
    mvc.perform(
            multipart("/imports/preview")
                .param("careerId", careerId)
                .header("Authorization", coordinator))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("No se recibió ningún archivo."));
    mvc.perform(
            multipart("/imports/preview")
                .file(new MockMultipartFile("file", "roto.xlsx", XLSX, "no es un libro".getBytes()))
                .param("careerId", careerId)
                .header("Authorization", coordinator))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "No se pudo leer el archivo. Verifica que sea un .xlsx válido y no esté"
                        + " protegido con contraseña."));
    mvc.perform(
            multipart("/imports/preview")
                .file(file)
                .param("careerId", careerId)
                .param("includeSchedule", "true")
                .header("Authorization", coordinator))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Para importar bloques de horario hay que elegir el periodo académico al que"
                        + " pertenecen."));
    mvc.perform(
            multipart("/imports/preview")
                .file(file)
                .param("careerId", UUID.randomUUID().toString())
                .header("Authorization", coordinator))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("La carrera indicada no existe."));

    // ---------- Vista previa ----------
    MvcResult preview =
        mvc.perform(
                multipart("/imports/preview")
                    .file(file)
                    .param("careerId", careerId)
                    .param("includeSchedule", "true")
                    .param("periodId", periodId)
                    .param("shift", "DAY")
                    .header("Authorization", coordinator))
            .andExpect(status().isCreated())
            .andReturn();
    String jobId = field(preview, "job.id");
    mvc.perform(get("/imports/" + jobId).header("Authorization", coordinator))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(jobId))
        .andExpect(jsonPath("$.careerId").value(careerId))
        .andExpect(jsonPath("$.periodId").value(periodId))
        .andExpect(jsonPath("$.fileName").value("Horarios Redes.xlsx"))
        .andExpect(jsonPath("$.status").value("READY"))
        .andExpect(jsonPath("$.rows[0].rawData.entity").value("level"))
        .andExpect(jsonPath("$.rows[0].messages.entity").value("level"));
    assertThat(field(preview, "counts.subjects")).isEqualTo("2");
    assertThat(field(preview, "counts.professors")).isEqualTo("2");
    assertThat(field(preview, "counts.spaces")).isEqualTo("1");
    assertThat(field(preview, "counts.levels")).isEqualTo("1");
    assertThat(field(preview, "counts.blocks")).isEqualTo("2");
    assertThat(field(preview, "job.errorRows")).isEqualTo("0");
    assertThat(field(preview, "canCommit")).isEqualTo("true");
    assertThat(field(preview, "blocks.periodName")).isEqualTo("Periodo de importación 2033");
    assertThat(field(preview, "blocks.shifts[0].shift")).isEqualTo("DAY");
    assertThat(field(preview, "blocks.conflicts")).isEqualTo("0");

    // ---------- Confirmación ----------
    mvc.perform(post("/imports/" + jobId + "/commit").header("Authorization", coordinator))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.jobId").value(jobId))
        .andExpect(jsonPath("$.career").value("Redes y Datos"))
        .andExpect(jsonPath("$.applied.levels").value(1))
        .andExpect(jsonPath("$.applied.subjects").value(2))
        .andExpect(jsonPath("$.applied.professors").value(2))
        .andExpect(jsonPath("$.applied.spaces").value(1))
        .andExpect(jsonPath("$.applied.blocks").value(2));
    mvc.perform(post("/imports/" + jobId + "/commit").header("Authorization", coordinator))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Esta importación ya fue aplicada."));
    mvc.perform(delete("/imports/" + jobId).header("Authorization", coordinator))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("No se puede cancelar una importación ya aplicada."));

    assertThat(
            jdbc()
                .queryForObject(
                    "select count(*) from schedule_blocks b"
                        + " join schedule_versions v on v.id = b.version_id"
                        + " join schedules s on s.id = v.schedule_id"
                        + " join academic_courses c on c.id = s.course_id"
                        + " where c.period_id = ?::uuid and v.status = 'DRAFT' and s.shift = 'DAY'",
                    Integer.class,
                    periodId))
        .isEqualTo(2);
    assertThat(
            jdbc()
                .queryForObject(
                    "select weekly_hours from subjects where career_id = ?::uuid and code = 'RD101'",
                    Integer.class,
                    careerId))
        .isEqualTo(4);
    assertThat(
            jdbc()
                .queryForObject(
                    "select count(*) from professor_careers where career_id = ?::uuid",
                    Integer.class,
                    careerId))
        .isEqualTo(2);

    // ---------- Subir el mismo libro otra vez no duplica nada ----------
    MvcResult again =
        mvc.perform(
                multipart("/imports/preview")
                    .file(file)
                    .param("careerId", careerId)
                    .param("includeSchedule", "true")
                    .param("periodId", periodId)
                    .header("Authorization", coordinator))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.canCommit").value(true))
            .andReturn();
    String secondJob = field(again, "job.id");
    mvc.perform(post("/imports/" + secondJob + "/commit").header("Authorization", coordinator))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.applied.blocks").value(0));

    // ---------- Cancelación y listado ----------
    String thirdJob =
        field(
            mvc.perform(
                    multipart("/imports/preview")
                        .file(file)
                        .param("careerId", careerId)
                        .header("Authorization", coordinator))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.blocks").doesNotExist())
                .andReturn(),
            "job.id");
    mvc.perform(delete("/imports/" + thirdJob).header("Authorization", coordinator))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(thirdJob))
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    mvc.perform(post("/imports/" + thirdJob + "/commit").header("Authorization", coordinator))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Esta importación fue cancelada."));
    mvc.perform(get("/imports/" + UUID.randomUUID()).header("Authorization", coordinator))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("La importación no existe."));

    mvc.perform(get("/imports").header("Authorization", coordinator))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].id").value(thirdJob))
        .andExpect(jsonPath("$[0].career.name").value("Redes y Datos"))
        .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
    mvc.perform(get("/imports").header("Authorization", bearer(createUser(UserRole.COORDINATOR))))
        .andExpect(jsonPath("$.length()").value(0));

    assertThat(
            jdbc()
                .queryForList(
                    "select distinct action from audit_logs where action like 'IMPORT_%'",
                    String.class))
        .contains("IMPORT_APPLIED", "IMPORT_CANCELLED");
  }
}
