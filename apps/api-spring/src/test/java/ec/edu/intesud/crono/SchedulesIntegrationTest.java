package ec.edu.intesud.crono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.edu.intesud.crono.domain.UserRole;
import ec.edu.intesud.crono.professors.ProfessionalTitles;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Profesores y horarios (diurno, nocturno, choques y publicación) como los usa el frontend. */
class SchedulesIntegrationTest extends IntegrationTestBase {

  @Test
  void professionalTitlesAreExpandedAndAbbreviated() {
    assertThat(ProfessionalTitles.expand(" Ing. en Sistemas ")).isEqualTo("Ingeniero/a en Sistemas");
    assertThat(ProfessionalTitles.expand("Tnlgo. S.U. en Redes"))
        .isEqualTo("Tecnólogo Superior Universitario en Redes");
    assertThat(ProfessionalTitles.abbreviate("MAGÍSTER en Educación")).isEqualTo("Mtr. en Educación");
    assertThat(ProfessionalTitles.prefix("Ingeniero/a en Sistemas")).isEqualTo("Ing.");
    assertThat(ProfessionalTitles.prefix("Tecnóloga Superior Universitaria")).isEqualTo("Tnlga. S.U.");
    assertThat(ProfessionalTitles.prefix("Chef")).isNull();
    assertThat(ProfessionalTitles.expand("  ")).isNull();
  }

  @Test
  void professorsAndSchedulesWorkEndToEnd() throws Exception {
    String admin = bearer(createUser(UserRole.ADMIN));
    String careerId =
        field(mvc.perform(json(post("/careers"), admin, "{'name':'Gastronomía'}")).andReturn(), "id");
    String otherCareerId =
        field(mvc.perform(json(post("/careers"), admin, "{'name':'Turismo'}")).andReturn(), "id");
    String coordinator = bearer(createUser(UserRole.COORDINATOR, UUID.fromString(careerId)));
    String outsider = bearer(createUser(UserRole.COORDINATOR, UUID.fromString(otherCareerId)));

    // ---------- Profesores ----------
    String professorBody =
        "{'nationalId':' 1733333333 ','firstNames':' Ana ','lastNames':'Torres',"
            + "'professionalTitle':'Ing. en Alimentos','scheduleColor':'#a1b2c3','careerIds':['"
            + careerId
            + "']}";
    MvcResult createdProfessor =
        mvc.perform(json(post("/professors"), coordinator, professorBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nationalId").value("1733333333"))
            .andExpect(jsonPath("$.firstNames").value("Ana"))
            .andExpect(jsonPath("$.professionalTitle").value("Ingeniero/a en Alimentos"))
            .andExpect(jsonPath("$.professionalTitleAbbreviation").value("Ing."))
            .andExpect(jsonPath("$.scheduleColor").value("#A1B2C3"))
            .andExpect(jsonPath("$.publicProfileEnabled").value(true))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.careers[0].name").value("Gastronomía"))
            .andExpect(jsonPath("$.careers[0].status").value("ACTIVE"))
            .andReturn();
    String professorId = field(createdProfessor, "id");

    mvc.perform(json(post("/professors"), coordinator, professorBody))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("La cédula del profesor ya está registrada"));
    mvc.perform(
            json(
                post("/professors"),
                coordinator,
                "{'firstNames':'Luis','lastNames':'Mora','careerIds':['" + otherCareerId + "']}"))
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.message").value("No puede asignar profesores a carreras no autorizadas"));
    mvc.perform(
            json(
                post("/professors"),
                coordinator,
                "{'firstNames':'Luis','lastNames':'Mora','scheduleColor':'rojo','careerIds':['"
                    + careerId
                    + "']}"))
        .andExpect(status().isBadRequest());

    mvc.perform(get("/professors?search=torres").header("Authorization", coordinator))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(professorId))
        .andExpect(jsonPath("$.pagination.pageSize").value(6));
    mvc.perform(get("/professors").header("Authorization", outsider))
        .andExpect(jsonPath("$.items.length()").value(0));
    mvc.perform(get("/professors?careerId=" + careerId).header("Authorization", outsider))
        .andExpect(status().isForbidden());
    mvc.perform(json(patch("/professors/" + professorId), outsider, "{'firstNames':'Otra'}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            json(
                patch("/professors/" + professorId),
                admin,
                "{'lastNames':'Torres Vega','careerIds':['" + careerId + "','" + otherCareerId + "']}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lastNames").value("Torres Vega"))
        .andExpect(jsonPath("$.nationalId").value("1733333333"))
        .andExpect(jsonPath("$.careers.length()").value(2));
    // El coordinador solo reemplaza sus carreras: la otra se conserva.
    mvc.perform(
            json(
                patch("/professors/" + professorId),
                coordinator,
                "{'careerIds':['" + careerId + "']}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.careers.length()").value(2));
    mvc.perform(get("/professors/careers").header("Authorization", coordinator))
        .andExpect(jsonPath("$.length()").value(1));

    // Directorio público, sin token.
    mvc.perform(get("/public/professors?search=TORRES&careerId=" + careerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].professionalTitleAbbreviation").value("Ing."))
        .andExpect(jsonPath("$[0].nationalId").doesNotExist());
    mvc.perform(
            json(patch("/professors/" + professorId + "/status"), coordinator, "{'status':'INACTIVE'}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVE"));
    mvc.perform(get("/public/professors/" + professorId)).andExpect(status().isNotFound());
    mvc.perform(
            json(patch("/professors/" + professorId + "/status"), coordinator, "{'status':'ACTIVE'}"))
        .andExpect(status().isOk());
    mvc.perform(get("/public/professors/" + professorId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.careers.length()").value(2));
    mvc.perform(delete("/professors/" + professorId).header("Authorization", coordinator))
        .andExpect(status().isForbidden());

    // ---------- Datos para el horario ----------
    String levelId =
        field(
            mvc.perform(
                    json(
                        post("/academic-structure/levels"),
                        admin,
                        "{'careerId':'" + careerId + "','name':'Primer nivel','order':1}"))
                .andReturn(),
            "id");
    String subjectId =
        field(
            mvc.perform(
                    json(
                        post("/academic-structure/subjects"),
                        admin,
                        "{'careerId':'"
                            + careerId
                            + "','levelId':'"
                            + levelId
                            + "','name':'Cocina básica','weeklyHours':6}"))
                .andReturn(),
            "id");
    String spaceId =
        field(
            mvc.perform(
                    json(
                        post("/laboratories"),
                        admin,
                        "{'type':'CLASSROOM','name':'Aula Cocina 1','peopleCapacity':30,"
                            + "'careerIds':['"
                            + careerId
                            + "']}"))
                .andReturn(),
            "id");
    String periodId =
        field(
            mvc.perform(
                    json(
                        post("/academic-periods"),
                        admin,
                        "{'name':'Periodo de horarios 2031','startDate':'2031-03-03',"
                            + "'endDate':'2031-07-25'}"))
                .andExpect(status().isCreated())
                .andReturn(),
            "id");
    String courseId =
        jdbc()
            .queryForObject(
                "select c.id::text from academic_courses c join levels l on l.id = c.level_id"
                    + " where c.period_id = ?::uuid and l.career_id = ?::uuid",
                String.class,
                periodId,
                careerId);

    mvc.perform(get("/schedules/context").header("Authorization", coordinator))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + periodId + "')].courses.length()").value(1))
        .andExpect(jsonPath("$[?(@.id=='" + periodId + "')].courses[0].level.order").value(1));

    // ---------- Horario diurno ----------
    String scheduleBody = "{'courseId':'" + courseId + "','shift':'DAY'}";
    MvcResult createdSchedule =
        mvc.perform(json(post("/schedules"), coordinator, scheduleBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.versionId").isNotEmpty())
            .andReturn();
    String scheduleId = field(createdSchedule, "id");
    mvc.perform(json(post("/schedules"), coordinator, scheduleBody))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(scheduleId))
        .andExpect(jsonPath("$.versionId").value(field(createdSchedule, "versionId")));
    mvc.perform(json(post("/schedules"), outsider, scheduleBody)).andExpect(status().isForbidden());
    mvc.perform(get("/schedules/" + scheduleId).header("Authorization", outsider))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("No puedes administrar esta carrera"));

    String blockBody =
        "{'subjectId':'"
            + subjectId
            + "','professorId':'"
            + professorId
            + "','spaceId':'"
            + spaceId
            + "','dayOfWeek':'MONDAY','startTime':'08:00','endTime':'10:00'}";
    MvcResult createdBlock =
        mvc.perform(json(post("/schedules/" + scheduleId + "/blocks"), coordinator, blockBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.dayOfWeek").value("MONDAY"))
            .andExpect(jsonPath("$.startTime").value("1970-01-01T08:00:00.000Z"))
            .andExpect(jsonPath("$.endTime").value("1970-01-01T10:00:00.000Z"))
            .andExpect(jsonPath("$.validFrom").value((Object) null))
            .andExpect(jsonPath("$.subject.name").value("Cocina básica"))
            .andExpect(jsonPath("$.professor.scheduleColor").value("#A1B2C3"))
            .andExpect(jsonPath("$.space.code").value("AULA-AULA-COCINA-1"))
            .andReturn();
    String blockId = field(createdBlock, "id");

    // La misma clase otra vez choca por curso, profesor y espacio.
    mvc.perform(
            json(
                post("/schedules/" + scheduleId + "/blocks"),
                coordinator,
                blockBody.replace("08:00", "09:00").replace("10:00", "11:00")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message.length()").value(3))
        .andExpect(jsonPath("$.message[0]").value("El curso ya tiene una clase en ese horario"))
        .andExpect(jsonPath("$.error").value("Conflict"));
    mvc.perform(
            json(
                post("/schedules/" + scheduleId + "/blocks"),
                coordinator,
                blockBody.replace("10:00", "07:00")))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message")
                .value("La hora de finalización debe ser posterior a la hora de inicio"));
    mvc.perform(
            json(
                post("/schedules/" + scheduleId + "/blocks"),
                coordinator,
                blockBody.replace("08:00", "8am")))
        .andExpect(status().isBadRequest());
    // Un curso con más estudiantes que el aula no cabe.
    mvc.perform(json(patch("/academic-periods/courses/" + courseId), admin, "{'studentCount':45}"))
        .andExpect(status().isOk());
    mvc.perform(
            json(
                post("/schedules/" + scheduleId + "/blocks"),
                coordinator,
                blockBody.replace("MONDAY", "FRIDAY")))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message")
                .value("La capacidad del espacio es insuficiente: 30 lugares para 45 estudiantes"));
    mvc.perform(json(patch("/academic-periods/courses/" + courseId), admin, "{'studentCount':25}"))
        .andExpect(status().isOk());

    mvc.perform(
            json(
                patch("/schedules/" + scheduleId + "/blocks/" + blockId),
                coordinator,
                blockBody.replace("MONDAY", "TUESDAY")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(blockId))
        .andExpect(jsonPath("$.dayOfWeek").value("TUESDAY"));

    mvc.perform(get("/schedules/" + scheduleId).header("Authorization", coordinator))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.shift").value("DAY"))
        .andExpect(jsonPath("$.course.studentCount").value(25))
        .andExpect(jsonPath("$.course.period.startDate").value("2031-03-03T00:00:00.000Z"))
        .andExpect(jsonPath("$.course.level.career.name").value("Gastronomía"))
        .andExpect(jsonPath("$.modules.length()").value(0))
        .andExpect(jsonPath("$.publishedVersion").value((Object) null))
        .andExpect(jsonPath("$.version.version").value(1))
        .andExpect(jsonPath("$.version.status").value("DRAFT"))
        .andExpect(jsonPath("$.version.blocks[0].startTime").value("08:00"))
        .andExpect(jsonPath("$.version.blocks[0].endTime").value("10:00"))
        .andExpect(jsonPath("$.version.blocks[0].professor.professionalTitleAbbreviation").value("Ing."))
        .andExpect(jsonPath("$.catalogs.subjects[0].weeklyHours").value(6))
        .andExpect(jsonPath("$.catalogs.professors[0].id").value(professorId))
        .andExpect(jsonPath("$.catalogs.spaces[0].peopleCapacity").value(30))
        .andExpect(jsonPath("$.days.length()").value(7))
        .andExpect(jsonPath("$.days[0]").value("MONDAY"));

    mvc.perform(get("/schedules?periodId=" + periodId).header("Authorization", coordinator))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(scheduleId))
        .andExpect(jsonPath("$[0].course.level.career.name").value("Gastronomía"))
        .andExpect(jsonPath("$[0].versions[0].version").value(1))
        .andExpect(jsonPath("$[0].versions[0]._count.blocks").value(1));
    mvc.perform(get("/schedules").header("Authorization", coordinator))
        .andExpect(status().isBadRequest());

    // ---------- Publicación ----------
    String exportUrl = "/exports/horarios?periodId=" + periodId + "&careerId=" + careerId;
    mvc.perform(get(exportUrl).header("Authorization", coordinator))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "No hay ningún horario publicado para esta carrera en este periodo."
                        + " Publica al menos un horario antes de exportar."));
    mvc.perform(get("/exports/horarios").header("Authorization", coordinator))
        .andExpect(status().isBadRequest());
    // Una segunda clase de una hora, para que la rejilla del libro tenga una franja.
    mvc.perform(
            json(
                post("/schedules/" + scheduleId + "/blocks"),
                coordinator,
                blockBody
                    .replace("MONDAY", "WEDNESDAY")
                    .replace("10:00", "11:00")
                    .replace("08:00", "10:00")))
        .andExpect(status().isCreated());
    mvc.perform(post("/schedules/" + scheduleId + "/publish").header("Authorization", coordinator))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.publishedVersion").value(1))
        .andExpect(jsonPath("$.draftVersion").value(2))
        .andExpect(jsonPath("$.blocks").value(2));
    mvc.perform(get("/schedules/" + scheduleId).header("Authorization", coordinator))
        .andExpect(jsonPath("$.publishedVersion.version").value(1))
        .andExpect(jsonPath("$.publishedVersion.publishedAt").isNotEmpty())
        .andExpect(jsonPath("$.version.version").value(2))
        .andExpect(jsonPath("$.version.status").value("DRAFT"))
        .andExpect(jsonPath("$.version.blocks.length()").value(2));
    mvc.perform(delete("/schedules/" + scheduleId).header("Authorization", coordinator))
        .andExpect(status().isConflict());

    // ---------- Exportación a Excel ----------
    MvcResult exported =
        mvc.perform(get(exportUrl).header("Authorization", coordinator))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        containsString("filename=\"Horarios GASTRONOMÍA Periodo de horarios 2031.xlsx\"")))
            .andReturn();
    try (XSSFWorkbook book =
        new XSSFWorkbook(new ByteArrayInputStream(exported.getResponse().getContentAsByteArray()))) {
      assertThat(book.getNumberOfSheets()).isEqualTo(3);
      XSSFSheet start = book.getSheet("Inicio");
      assertThat(start.getRow(0).getCell(0).getStringCellValue()).isEqualTo("CARRERA");
      assertThat(start.getRow(1).getCell(0).getStringCellValue()).isEqualTo("GASTRONOMÍA");
      assertThat(start.getRow(2).getCell(3).getStringCellValue()).isEqualTo("Ing. Torres Vega Ana");
      assertThat(start.getRow(1).getCell(5).getStringCellValue()).isEqualTo("Aula Cocina 1");
      assertThat(start.getRow(1).getCell(7).getStringCellValue()).isEqualTo("Primer nivel");

      XSSFSheet day = book.getSheet("HorariosClasesDiurno");
      assertThat(day.getRow(2).getCell(2).getStringCellValue()).isEqualTo("Primer nivel");
      assertThat(day.getRow(3).getCell(4).getStringCellValue()).isEqualTo("Miércoles");
      assertThat(day.getRow(4).getCell(1).getStringCellValue()).isEqualTo("10:00h-11:00h");
      assertThat(day.getRow(4).getCell(4).getStringCellValue()).isEqualTo("Cocina básica");
      assertThat(day.getRow(5).getCell(4).getStringCellValue()).isEqualTo("Ing. Torres Vega Ana");
      assertThat(day.getRow(6).getCell(4).getStringCellValue()).isEqualTo("Aula Cocina 1");
      // La clase lleva de fondo el color del docente.
      assertThat(day.getRow(4).getCell(4).getCellStyle().getFillForegroundColorColor().getARGBHex())
          .isEqualTo("FFA1B2C3");
      assertThat(book.getSheet("HorariosClasesNocturno")).isNotNull();
    }
    mvc.perform(delete("/professors/" + professorId).header("Authorization", admin))
        .andExpect(status().isConflict());

    // ---------- Horario nocturno ----------
    String nightId =
        field(
            mvc.perform(
                    json(post("/schedules"), coordinator, scheduleBody.replace("DAY", "NIGHT")))
                .andExpect(status().isCreated())
                .andReturn(),
            "id");
    mvc.perform(get("/schedules/" + nightId).header("Authorization", coordinator))
        .andExpect(jsonPath("$.modules.length()").value(6))
        .andExpect(jsonPath("$.modules[0].startDate").value("2031-03-03T00:00:00.000Z"))
        .andExpect(jsonPath("$.modules[0].endDate").value("2031-03-21T00:00:00.000Z"))
        .andExpect(jsonPath("$.modules[1].startDate").value("2031-03-24T00:00:00.000Z"))
        .andExpect(jsonPath("$.modules[0].scheduleId").value(nightId));

    String nightBlock =
        "{'subjectId':'"
            + subjectId
            + "','professorId':'"
            + professorId
            + "','dayOfWeek':'MONDAY','startTime':'18:00','endTime':'20:00'}";
    mvc.perform(json(post("/schedules/" + nightId + "/blocks"), coordinator, nightBlock))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("Selecciona un módulo nocturno válido"));
    String nightBlockId =
        field(
            mvc.perform(
                    json(
                        post("/schedules/" + nightId + "/blocks"),
                        coordinator,
                        nightBlock.replace("}", ",'moduleNumber':1}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.moduleNumber").value(1))
                .andExpect(jsonPath("$.space").value((Object) null))
                .andExpect(jsonPath("$.validFrom").value("2031-03-03T00:00:00.000Z"))
                .andExpect(jsonPath("$.validUntil").value("2031-03-21T00:00:00.000Z"))
                .andReturn(),
            "id");

    mvc.perform(
            json(
                patch("/schedules/" + nightId + "/modules/1"),
                coordinator,
                "{'startDate':'2031-03-10','endDate':'2031-03-28'}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.moduleNumber").value(1))
        .andExpect(jsonPath("$.startDate").value("2031-03-10T00:00:00.000Z"));
    mvc.perform(get("/schedules/" + nightId).header("Authorization", coordinator))
        .andExpect(jsonPath("$.version.blocks[0].validFrom").value("2031-03-10T00:00:00.000Z"))
        .andExpect(jsonPath("$.version.blocks[0].validUntil").value("2031-03-28T00:00:00.000Z"));
    mvc.perform(
            json(
                patch("/schedules/" + nightId + "/modules/9"),
                coordinator,
                "{'startDate':'2031-03-10','endDate':'2031-03-28'}"))
        .andExpect(status().isConflict());
    mvc.perform(
            json(
                patch("/schedules/" + scheduleId + "/modules/1"),
                coordinator,
                "{'startDate':'2031-03-10','endDate':'2031-03-28'}"))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message").value("Los módulos corresponden únicamente a horarios nocturnos"));

    mvc.perform(
            delete("/schedules/" + nightId + "/blocks/" + nightBlockId)
                .header("Authorization", coordinator))
        .andExpect(status().isNoContent());
    mvc.perform(
            delete("/schedules/" + nightId + "/blocks/" + nightBlockId)
                .header("Authorization", coordinator))
        .andExpect(status().isNotFound());
    mvc.perform(delete("/schedules/" + nightId).header("Authorization", coordinator))
        .andExpect(status().isNoContent());
    mvc.perform(get("/schedules/" + nightId).header("Authorization", coordinator))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Horario no encontrado"));

    assertThat(
            jdbc()
                .queryForList(
                    "select distinct action from audit_logs where action not like 'AUTH_%'",
                    String.class))
        .contains(
            "PROFESSOR_CREATED",
            "PROFESSOR_UPDATED",
            "PROFESSOR_DEACTIVATED",
            "PROFESSOR_ACTIVATED",
            "SCHEDULE_DRAFT_CREATED",
            "SCHEDULE_BLOCK_CREATED",
            "SCHEDULE_BLOCK_UPDATED",
            "SCHEDULE_BLOCK_DELETED",
            "SCHEDULE_PUBLISHED",
            "SCHEDULE_MODULE_DATES_UPDATED",
            "SCHEDULE_DRAFT_DELETED");
  }
}
