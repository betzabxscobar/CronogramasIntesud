import { Body, Controller, Delete, Get, HttpCode, Param, ParseUUIDPipe, Patch, Post, Query } from "@nestjs/common";
import { ApiBearerAuth, ApiTags } from "@nestjs/swagger";
import { UserRole } from "@prisma/client";
import type { AuthenticatedUser } from "../auth/auth.types";
import { CurrentUser } from "../auth/decorators/current-user.decorator";
import { Roles } from "../auth/decorators/roles.decorator";
import { CreateScheduleDto } from "./dto/create-schedule.dto";
import { SaveBlockDto } from "./dto/save-block.dto";
import { UpdateScheduleModuleDto } from "./dto/update-schedule-module.dto";
import { SchedulesService } from "./schedules.service";

@ApiTags("schedules") @ApiBearerAuth() @Roles(UserRole.ADMIN, UserRole.COORDINATOR) @Controller("schedules")
export class SchedulesController {
  constructor(private readonly schedules: SchedulesService) {}
  @Get("context") context(@CurrentUser() actor: AuthenticatedUser) { return this.schedules.context(actor); }
  @Get() list(@Query("periodId", ParseUUIDPipe) periodId: string, @CurrentUser() actor: AuthenticatedUser) { return this.schedules.list(periodId, actor); }
  @Get(":id") detail(@Param("id", ParseUUIDPipe) id: string, @CurrentUser() actor: AuthenticatedUser) { return this.schedules.detail(id, actor); }
  @Post() create(@Body() input: CreateScheduleDto, @CurrentUser() actor: AuthenticatedUser) { return this.schedules.create(input, actor); }
  @Post(":id/blocks") addBlock(@Param("id", ParseUUIDPipe) id: string, @Body() input: SaveBlockDto, @CurrentUser() actor: AuthenticatedUser) { return this.schedules.addBlock(id, input, actor); }
  @Patch(":id/blocks/:blockId") updateBlock(@Param("id", ParseUUIDPipe) id: string, @Param("blockId", ParseUUIDPipe) blockId: string, @Body() input: SaveBlockDto, @CurrentUser() actor: AuthenticatedUser) { return this.schedules.updateBlock(id, blockId, input, actor); }
  // Publica el borrador actual y abre el siguiente con las mismas clases: lo
  // publicado queda congelado y es lo que se exporta.
  @Post(":id/publish")
  publish(@Param("id", ParseUUIDPipe) id: string, @CurrentUser() actor: AuthenticatedUser) {
    return this.schedules.publish(id, actor);
  }

  @Patch(":id/modules/:moduleNumber") updateModule(@Param("id", ParseUUIDPipe) id: string, @Param("moduleNumber") moduleNumber: string, @Body() input: UpdateScheduleModuleDto, @CurrentUser() actor: AuthenticatedUser) { return this.schedules.updateModule(id, Number(moduleNumber), input, actor); }
  @Delete(":id/blocks/:blockId") @HttpCode(204) removeBlock(@Param("id", ParseUUIDPipe) id: string, @Param("blockId", ParseUUIDPipe) blockId: string, @CurrentUser() actor: AuthenticatedUser) { return this.schedules.removeBlock(id, blockId, actor); }
  @Delete(":id") @HttpCode(204) remove(@Param("id", ParseUUIDPipe) id: string, @CurrentUser() actor: AuthenticatedUser) { return this.schedules.remove(id, actor); }
}
