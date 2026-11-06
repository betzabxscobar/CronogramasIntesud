import { Body, Controller, Delete, Get, HttpCode, Param, ParseUUIDPipe, Patch, Post } from "@nestjs/common";
import { ApiBearerAuth, ApiTags } from "@nestjs/swagger";
import { UserRole } from "@prisma/client";
import type { AuthenticatedUser } from "../auth/auth.types";
import { CurrentUser } from "../auth/decorators/current-user.decorator";
import { Roles } from "../auth/decorators/roles.decorator";
import { AcademicPeriodsService } from "./academic-periods.service";
import { CreatePeriodDto } from "./dto/create-period.dto";
import { UpdateCourseDto } from "./dto/update-course.dto";
import { UpdatePeriodStatusDto } from "./dto/update-period-status.dto";
import { UpdatePeriodDto } from "./dto/update-period.dto";

@ApiTags("academic-periods")
@ApiBearerAuth()
@Roles(UserRole.ADMIN, UserRole.COORDINATOR)
@Controller("academic-periods")
export class AcademicPeriodsController {
  constructor(private readonly periods: AcademicPeriodsService) {}

  @Get()
  list(@CurrentUser() actor: AuthenticatedUser) { return this.periods.list(actor); }

  @Get(":id")
  detail(@Param("id", ParseUUIDPipe) id: string, @CurrentUser() actor: AuthenticatedUser) { return this.periods.detail(id, actor); }

  @Post()
  @Roles(UserRole.ADMIN)
  create(@Body() input: CreatePeriodDto, @CurrentUser() actor: AuthenticatedUser) { return this.periods.create(input, actor.id); }

  @Patch(":id")
  @Roles(UserRole.ADMIN)
  update(@Param("id", ParseUUIDPipe) id: string, @Body() input: UpdatePeriodDto, @CurrentUser() actor: AuthenticatedUser) { return this.periods.update(id, input, actor.id); }

  @Patch(":id/status")
  @Roles(UserRole.ADMIN)
  updateStatus(@Param("id", ParseUUIDPipe) id: string, @Body() input: UpdatePeriodStatusDto, @CurrentUser() actor: AuthenticatedUser) { return this.periods.updateStatus(id, input.status, actor.id); }

  @Post(":id/sync-courses")
  @Roles(UserRole.ADMIN)
  syncCourses(@Param("id", ParseUUIDPipe) id: string, @CurrentUser() actor: AuthenticatedUser) { return this.periods.syncCourses(id, actor.id); }

  @Patch("courses/:id")
  updateCourse(@Param("id", ParseUUIDPipe) id: string, @Body() input: UpdateCourseDto, @CurrentUser() actor: AuthenticatedUser) { return this.periods.updateCourse(id, input, actor); }

  @Delete("courses/:id")
  @HttpCode(204)
  @Roles(UserRole.ADMIN)
  removeCourse(@Param("id", ParseUUIDPipe) id: string, @CurrentUser() actor: AuthenticatedUser) { return this.periods.removeCourse(id, actor.id); }
}
