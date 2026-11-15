package ec.edu.intesud.crono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.edu.intesud.crono.domain.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Recorre los módulos de administración (carreras, coordinadores, laboratorios, estructura
 * académica y periodos) como lo haría el frontend, comprobando respuestas y permisos.
 */
class AdministrationIntegrationTest extends IntegrationTestBase {

  @Test
  void administrationModulesWorkEndToEnd() throws Exception {
    String admin = bearer(createUser(UserRole.ADMIN));

    // ---------- Carreras ----------
    MvcResult createdCareer =
        mvc.perform(json(post("/careers"), admin, "{'name':'  Diseño   Gráfico '}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Diseño Gráfico"))
            .andExpect(jsonPath("$.slug").value("diseno-grafico"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$._count").doesNotExist())
            .andReturn();
    String careerId = field(createdCareer, "id");

    mvc.perform(json(post("/careers"), admin, "{'name':'diseño gráfico'}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("La carrera ya está registrada"))
        .andExpect(jsonPath("$.error").value("Conflict"));
    mvc.perform(json(post("/careers"), admin, "{'name':'x'}")).andExpect(status().isBadRequest());

    String otherCareerId =
        field(
            mvc.perform(json(post("/careers"), admin, "{'name':'Contabilidad'}")).andReturn(),
            "id");

    // ---------- Coordinadores ----------
    MvcResult createdCoordinator =
        mvc.perform(
                json(
                    post("/coordinators"),
                    admin,
                    "{'nationalId':'1711111111','firstNames':' María ','lastNames':'Pérez',"
                        + "'email':'Maria.Perez@Example.com','careerIds':['"
                        + careerId
                        + "']}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.firstNames").value("María"))
            .andExpect(jsonPath("$.email").value("maria.perez@example.com"))
            .andExpect(jsonPath("$.lastLoginAt").value((Object) null))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.careers[0].name").value("Diseño Gráfico"))
            .andExpect(jsonPath("$.careers[0].status").value("ACTIVE"))
            .andReturn();
    UUID coordinatorId = UUID.fromString(field(createdCoordinator, "id"));
    String coordinator = bearer(coordinatorId);

    mvc.perform(
            json(
                post("/coordinators"),
                admin,
                "{'nationalId':'1722222222','firstNames':'Otro','lastNames':'Usuario',"
                    + "'email':'maria.perez@example.com','careerIds':['"
                    + careerId
                    + "']}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("El correo ya está registrado"));

    mvc.perform(get("/coordinators?search=PÉREZ&pageSize=5").header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.pagination.page").value(1))
        .andExpect(jsonPath("$.pagination.pageSize").value(5))
        .andExpect(jsonPath("$.pagination.total").value(1))
        .andExpect(jsonPath("$.pagination.totalPages").value(1));
    mvc.perform(get("/coordinators?pageSize=500").header("Authorization", admin))
        .andExpect(status().isBadRequest());

    // Un coordinador no entra a los módulos del administrador.
    mvc.perform(get("/careers").header("Authorization", coordinator))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("No tiene permisos para esta operación"))
        .andExpect(jsonPath("$.error").value("Forbidden"))
        .andExpect(jsonPath("$.statusCode").value(403));

    mvc.perform(get("/careers").header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.name=='Diseño Gráfico')]._count.coordinators").value(1))
        .andExpect(jsonPath("$[?(@.name=='Diseño Gráfico')]._count.professors").value(0));

    // Una carrera con información vinculada no se puede eliminar; una sin uso, sí.
    mvc.perform(delete("/careers/" + careerId).header("Authorization", admin))
        .andExpect(status().isConflict());
    mvc.perform(delete("/careers/" + otherCareerId).header("Authorization", admin))
        .andExpect(status().isNoContent());
    mvc.perform(delete("/careers/" + otherCareerId).header("Authorization", admin))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Carrera no encontrada"));
    mvc.perform(delete("/careers/no-es-uuid").header("Authorization", admin))
        .andExpect(status().isBadRequest());

    // ---------- Laboratorios ----------
    MvcResult createdLab =
        mvc.perform(
                json(
                    post("/laboratories"),
                    admin,
                    "{'type':'LABORATORY','name':'Sala de Cómputo 1','studentComputers':24,"
                        + "'hasProjector':true,'careerIds':['"
                        + careerId
                        + "']}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("LAB-SALA-DE-COMPUTO-1"))
            .andExpect(jsonPath("$.location").value((Object) null))
            .andExpect(jsonPath("$.peopleCapacity").value((Object) null))
            .andExpect(jsonPath("$.studentComputers").value(24))
            .andExpect(jsonPath("$.teacherComputers").value(0))
            .andExpect(jsonPath("$.hasProjector").value(true))
            .andExpect(jsonPath("$.hasDigitalBoard").value(false))
            .andExpect(jsonPath("$.status").value("AVAILABLE"))
            .andExpect(jsonPath("$.careers[0].id").value(careerId))
            .andExpect(jsonPath("$._count.scheduleBlocks").value(0))
            .andReturn();
    String labId = field(createdLab, "id");

    mvc.perform(json(post("/laboratories"), coordinator, "{}")).andExpect(status().isForbidden());
    mvc.perform(get("/laboratories?search=cómputo").header("Authorization", coordinator))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(labId))
        .andExpect(jsonPath("$.pagination.pageSize").value(6));
    // Otro coordinador, sin carreras, no ve ese laboratorio.
    mvc.perform(
            get("/laboratories").header("Authorization", bearer(createUser(UserRole.COORDINATOR))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.pagination.totalPages").value(1));
    mvc.perform(
            json(patch("/laboratories/" + labId + "/status"), admin, "{'status':'MAINTENANCE'}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("MAINTENANCE"));
    mvc.perform(get("/laboratories/careers").header("Authorization", coordinator))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Diseño Gráfico"));

    // ---------- Estructura académica ----------
    MvcResult createdLevel =
        mvc.perform(
                json(
                    post("/academic-structure/levels"),
                    coordinator,
                    "{'careerId':'" + careerId + "','name':'Primer  nivel','order':1}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.careerId").value(careerId))
            .andExpect(jsonPath("$.name").value("Primer nivel"))
            .andExpect(jsonPath("$.order").value(1))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn();
    String levelId = field(createdLevel, "id");

    mvc.perform(
            json(
                post("/academic-structure/levels"),
                admin,
                "{'careerId':'" + careerId + "','name':'Otro','order':1}"))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message").value("Ya existe un nivel con ese nombre u orden en la carrera"));

    String subjectBody =
        "{'careerId':'"
            + careerId
            + "','levelId':'"
            + levelId
            + "','name':'Fundamentos de diseño','code':' dg-101 ','weeklyHours':4}";
    MvcResult createdSubject =
        mvc.perform(json(post("/academic-structure/subjects"), coordinator, subjectBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("DG-101"))
            .andExpect(jsonPath("$.levelId").value(levelId))
            .andExpect(jsonPath("$.requiresIndividualComputer").value(false))
            .andReturn();
    String subjectId = field(createdSubject, "id");
    mvc.perform(json(post("/academic-structure/subjects"), coordinator, subjectBody))
        .andExpect(status().isConflict());
    mvc.perform(
            json(
                patch("/academic-structure/subjects/" + subjectId),
                coordinator,
                "{'requiresIndividualComputer':true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requiresIndividualComputer").value(true))
        .andExpect(jsonPath("$.name").value("Fundamentos de diseño"));

    mvc.perform(
            get("/academic-structure?careerId=" + careerId).header("Authorization", coordinator))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Diseño Gráfico"))
        .andExpect(jsonPath("$.levels[0].order").value(1))
        .andExpect(jsonPath("$.levels[0]._count.subjects").value(1))
        .andExpect(jsonPath("$.levels[0]._count.courses").value(0))
        .andExpect(jsonPath("$.levels[0].subjects[0].code").value("DG-101"))
        .andExpect(jsonPath("$.levels[0].subjects[0]._count.scheduleBlocks").value(0));

    // Un coordinador no administra carreras ajenas.
    String foreignCareer =
        field(mvc.perform(json(post("/careers"), admin, "{'name':'Enfermería'}")).andReturn(), "id");
    mvc.perform(
            get("/academic-structure?careerId=" + foreignCareer)
                .header("Authorization", coordinator))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("No puedes administrar esta carrera"));
    mvc.perform(delete("/academic-structure/levels/" + levelId).header("Authorization", admin))
        .andExpect(status().isConflict());

    // ---------- Periodos académicos ----------
    MvcResult createdPeriod =
        mvc.perform(
                json(
                    post("/academic-periods"),
                    admin,
                    "{'name':'Octubre 2026 - Febrero 2027','startDate':'2026-10-05',"
                        + "'endDate':'2027-02-26'}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.startDate").value("2026-10-05T00:00:00.000Z"))
            .andExpect(jsonPath("$.endDate").value("2027-02-26T00:00:00.000Z"))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andReturn();
    String periodId = field(createdPeriod, "id");

    mvc.perform(
            json(
                post("/academic-periods"),
                admin,
                "{'name':'Fechas al revés','startDate':'2027-02-26','endDate':'2026-10-05'}"))
        .andExpect(status().isConflict());
    mvc.perform(json(post("/academic-periods"), coordinator, "{}"))
        .andExpect(status().isForbidden());

    // Al crear el periodo se generó el "Curso único" del nivel activo.
    MvcResult detail =
        mvc.perform(get("/academic-periods/" + periodId).header("Authorization", coordinator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shifts[0]").value("DAY"))
            .andExpect(jsonPath("$.shifts[1]").value("NIGHT"))
            .andExpect(jsonPath("$.courses.length()").value(1))
            .andExpect(jsonPath("$.courses[0].name").value("Curso único"))
            .andExpect(jsonPath("$.courses[0].studentCount").value((Object) null))
            .andExpect(jsonPath("$.courses[0].level.order").value(1))
            .andExpect(jsonPath("$.courses[0].level.career.name").value("Diseño Gráfico"))
            .andExpect(jsonPath("$.courses[0]._count.schedules").value(0))
            .andReturn();
    String courseId = field(detail, "courses[0].id");

    mvc.perform(get("/academic-periods").header("Authorization", coordinator))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + periodId + "')]._count.courses").value(1));
    mvc.perform(
            json(patch("/academic-periods/courses/" + courseId), coordinator, "{'studentCount':28}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.studentCount").value(28))
        .andExpect(jsonPath("$.periodId").value(periodId));
    mvc.perform(
            json(patch("/academic-periods/" + periodId + "/status"), admin, "{'status':'ACTIVE'}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    mvc.perform(
            post("/academic-periods/" + periodId + "/sync-courses").header("Authorization", admin))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.created").value(0));

    // ---------- Coordinador: desactivar y eliminar ----------
    mvc.perform(
            json(patch("/coordinators/" + coordinatorId + "/status"), admin, "{'status':'INACTIVE'}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INACTIVE"));
    // Su token deja de servir en cuanto se desactiva.
    mvc.perform(get("/laboratories").header("Authorization", coordinator))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete("/coordinators/" + coordinatorId).header("Authorization", admin))
        .andExpect(status().isNoContent());

    assertThat(
            jdbc()
                .queryForList(
                    "select distinct action from audit_logs where action not like 'AUTH_%'",
                    String.class))
        .contains(
            "CAREER_CREATED",
            "CAREER_DELETED",
            "COORDINATOR_CREATED",
            "COORDINATOR_DEACTIVATED",
            "COORDINATOR_DELETED",
            "ACADEMIC_SPACE_CREATED",
            "ACADEMIC_SPACE_STATUS_CHANGED",
            "LEVEL_CREATED",
            "SUBJECT_CREATED",
            "SUBJECT_UPDATED",
            "ACADEMIC_PERIOD_CREATED",
            "ACADEMIC_PERIOD_STATUS_CHANGED",
            "ACADEMIC_COURSES_SYNCHRONIZED",
            "ACADEMIC_COURSE_UPDATED");
  }
}
