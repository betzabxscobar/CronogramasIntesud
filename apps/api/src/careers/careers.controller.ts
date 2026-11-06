import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  Param,
  ParseUUIDPipe,
  Patch,
  Post
} from "@nestjs/common";
import { ApiBearerAuth, ApiOperation, ApiTags } from "@nestjs/swagger";
import { UserRole } from "@prisma/client";
import type { AuthenticatedUser } from "../auth/auth.types";
import { CurrentUser } from "../auth/decorators/current-user.decorator";
import { Roles } from "../auth/decorators/roles.decorator";
import { CareersService } from "./careers.service";
import { CreateCareerDto } from "./dto/create-career.dto";
import { UpdateCareerDto } from "./dto/update-career.dto";
import { UpdateCareerStatusDto } from "./dto/update-career-status.dto";

@ApiTags("careers")
@ApiBearerAuth()
@Roles(UserRole.ADMIN)
@Controller("careers")
export class CareersController {
  constructor(private readonly careers: CareersService) {}

  @Get()
  @ApiOperation({ summary: "Lista las carreras institucionales" })
  list() {
    return this.careers.list();
  }

  @Post()
  @ApiOperation({ summary: "Registra una carrera institucional" })
  create(
    @Body() input: CreateCareerDto,
    @CurrentUser() actor: AuthenticatedUser
  ) {
    return this.careers.create(input, actor.id);
  }

  @Patch(":id")
  @ApiOperation({ summary: "Edita una carrera institucional" })
  update(
    @Param("id", ParseUUIDPipe) id: string,
    @Body() input: UpdateCareerDto,
    @CurrentUser() actor: AuthenticatedUser
  ) {
    return this.careers.update(id, input, actor.id);
  }

  @Patch(":id/status")
  @ApiOperation({ summary: "Activa o desactiva una carrera" })
  updateStatus(
    @Param("id", ParseUUIDPipe) id: string,
    @Body() input: UpdateCareerStatusDto,
    @CurrentUser() actor: AuthenticatedUser
  ) {
    return this.careers.updateStatus(id, input.status, actor.id);
  }

  @Delete(":id")
  @HttpCode(204)
  @ApiOperation({ summary: "Elimina una carrera que todavía no está en uso" })
  remove(
    @Param("id", ParseUUIDPipe) id: string,
    @CurrentUser() actor: AuthenticatedUser
  ) {
    return this.careers.remove(id, actor.id);
  }
}
