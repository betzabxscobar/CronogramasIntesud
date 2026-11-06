import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  Param,
  ParseUUIDPipe,
  Patch,
  Post,
  Query
} from "@nestjs/common";
import { ApiBearerAuth, ApiOperation, ApiTags } from "@nestjs/swagger";
import { UserRole } from "@prisma/client";
import type { AuthenticatedUser } from "../auth/auth.types";
import { CurrentUser } from "../auth/decorators/current-user.decorator";
import { Roles } from "../auth/decorators/roles.decorator";
import { CreateProfessorDto } from "./dto/create-professor.dto";
import { ListProfessorsDto } from "./dto/list-professors.dto";
import { UpdateProfessorStatusDto } from "./dto/update-professor-status.dto";
import { UpdateProfessorDto } from "./dto/update-professor.dto";
import { ProfessorsService } from "./professors.service";

@ApiTags("professors")
@ApiBearerAuth()
@Roles(UserRole.ADMIN, UserRole.COORDINATOR)
@Controller("professors")
export class ProfessorsController {
  constructor(private readonly professors: ProfessorsService) {}

  @Get()
  @ApiOperation({ summary: "Lista profesores según el alcance del usuario" })
  list(
    @Query() query: ListProfessorsDto,
    @CurrentUser() actor: AuthenticatedUser
  ) {
    return this.professors.list(query, actor);
  }

  @Get("careers")
  @ApiOperation({ summary: "Lista carreras disponibles para el usuario" })
  careers(@CurrentUser() actor: AuthenticatedUser) {
    return this.professors.listCareers(actor);
  }

  @Post()
  @ApiOperation({ summary: "Registra un profesor" })
  create(
    @Body() input: CreateProfessorDto,
    @CurrentUser() actor: AuthenticatedUser
  ) {
    return this.professors.create(input, actor);
  }

  @Patch(":id")
  @ApiOperation({ summary: "Edita un profesor" })
  update(
    @Param("id", ParseUUIDPipe) id: string,
    @Body() input: UpdateProfessorDto,
    @CurrentUser() actor: AuthenticatedUser
  ) {
    return this.professors.update(id, input, actor);
  }

  @Patch(":id/status")
  @ApiOperation({ summary: "Activa o desactiva un profesor" })
  updateStatus(
    @Param("id", ParseUUIDPipe) id: string,
    @Body() input: UpdateProfessorStatusDto,
    @CurrentUser() actor: AuthenticatedUser
  ) {
    return this.professors.updateStatus(id, input.status, actor);
  }

  @Delete(":id")
  @HttpCode(204)
  @Roles(UserRole.ADMIN)
  @ApiOperation({ summary: "Elimina un profesor sin horarios asignados" })
  remove(
    @Param("id", ParseUUIDPipe) id: string,
    @CurrentUser() actor: AuthenticatedUser
  ) {
    return this.professors.remove(id, actor.id);
  }
}
