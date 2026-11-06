import { Body, Controller, Delete, Get, HttpCode, Param, ParseUUIDPipe, Patch, Post, Query } from "@nestjs/common";
import { ApiBearerAuth, ApiOperation, ApiTags } from "@nestjs/swagger";
import { UserRole } from "@prisma/client";
import type { AuthenticatedUser } from "../auth/auth.types";
import { CurrentUser } from "../auth/decorators/current-user.decorator";
import { Roles } from "../auth/decorators/roles.decorator";
import { CreateLaboratoryDto } from "./dto/create-laboratory.dto";
import { ListLaboratoriesDto } from "./dto/list-laboratories.dto";
import { UpdateLaboratoryStatusDto } from "./dto/update-laboratory-status.dto";
import { UpdateLaboratoryDto } from "./dto/update-laboratory.dto";
import { LaboratoriesService } from "./laboratories.service";

@ApiTags("laboratories")
@ApiBearerAuth()
@Roles(UserRole.ADMIN, UserRole.COORDINATOR)
@Controller("laboratories")
export class LaboratoriesController {
  constructor(private readonly laboratories: LaboratoriesService) {}

  @Get()
  list(@Query() query: ListLaboratoriesDto, @CurrentUser() actor: AuthenticatedUser) {
    return this.laboratories.list(query, actor);
  }

  @Get("careers")
  careers(@CurrentUser() actor: AuthenticatedUser) {
    return this.laboratories.listCareers(actor);
  }

  @Post()
  @Roles(UserRole.ADMIN)
  @ApiOperation({ summary: "Registra un aula o laboratorio" })
  create(@Body() input: CreateLaboratoryDto, @CurrentUser() actor: AuthenticatedUser) {
    return this.laboratories.create(input, actor.id);
  }

  @Patch(":id")
  @Roles(UserRole.ADMIN)
  update(@Param("id", ParseUUIDPipe) id: string, @Body() input: UpdateLaboratoryDto, @CurrentUser() actor: AuthenticatedUser) {
    return this.laboratories.update(id, input, actor.id);
  }

  @Patch(":id/status")
  @Roles(UserRole.ADMIN)
  updateStatus(@Param("id", ParseUUIDPipe) id: string, @Body() input: UpdateLaboratoryStatusDto, @CurrentUser() actor: AuthenticatedUser) {
    return this.laboratories.updateStatus(id, input.status, actor.id);
  }

  @Delete(":id")
  @HttpCode(204)
  @Roles(UserRole.ADMIN)
  remove(@Param("id", ParseUUIDPipe) id: string, @CurrentUser() actor: AuthenticatedUser) {
    return this.laboratories.remove(id, actor.id);
  }
}
