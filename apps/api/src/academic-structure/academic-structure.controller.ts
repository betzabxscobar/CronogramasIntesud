import { Body, Controller, Delete, Get, HttpCode, Param, ParseUUIDPipe, Patch, Post, Query } from "@nestjs/common";
import { ApiBearerAuth, ApiTags } from "@nestjs/swagger";
import { UserRole } from "@prisma/client";
import type { AuthenticatedUser } from "../auth/auth.types";
import { CurrentUser } from "../auth/decorators/current-user.decorator";
import { Roles } from "../auth/decorators/roles.decorator";
import { AcademicStructureService } from "./academic-structure.service";
import { CreateLevelDto } from "./dto/create-level.dto";
import { CreateSubjectDto } from "./dto/create-subject.dto";
import { UpdateLevelDto } from "./dto/update-level.dto";
import { UpdateSubjectDto } from "./dto/update-subject.dto";

@ApiTags("academic-structure")
@ApiBearerAuth()
@Roles(UserRole.ADMIN, UserRole.COORDINATOR)
@Controller("academic-structure")
export class AcademicStructureController {
  constructor(private readonly structure: AcademicStructureService) {}

  @Get("careers")
  careers(@CurrentUser() actor: AuthenticatedUser) { return this.structure.listCareers(actor); }

  @Get()
  detail(@Query("careerId", ParseUUIDPipe) careerId: string, @CurrentUser() actor: AuthenticatedUser) {
    return this.structure.detail(careerId, actor);
  }

  @Post("levels")
  createLevel(@Body() input: CreateLevelDto, @CurrentUser() actor: AuthenticatedUser) {
    return this.structure.createLevel(input, actor);
  }

  @Patch("levels/:id")
  updateLevel(@Param("id", ParseUUIDPipe) id: string, @Body() input: UpdateLevelDto, @CurrentUser() actor: AuthenticatedUser) {
    return this.structure.updateLevel(id, input, actor);
  }

  @Delete("levels/:id")
  @HttpCode(204)
  removeLevel(@Param("id", ParseUUIDPipe) id: string, @CurrentUser() actor: AuthenticatedUser) {
    return this.structure.removeLevel(id, actor);
  }

  @Post("subjects")
  createSubject(@Body() input: CreateSubjectDto, @CurrentUser() actor: AuthenticatedUser) {
    return this.structure.createSubject(input, actor);
  }

  @Patch("subjects/:id")
  updateSubject(@Param("id", ParseUUIDPipe) id: string, @Body() input: UpdateSubjectDto, @CurrentUser() actor: AuthenticatedUser) {
    return this.structure.updateSubject(id, input, actor);
  }

  @Delete("subjects/:id")
  @HttpCode(204)
  removeSubject(@Param("id", ParseUUIDPipe) id: string, @CurrentUser() actor: AuthenticatedUser) {
    return this.structure.removeSubject(id, actor);
  }
}
