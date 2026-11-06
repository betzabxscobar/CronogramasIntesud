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
import { CoordinatorsService } from "./coordinators.service";
import { CreateCoordinatorDto } from "./dto/create-coordinator.dto";
import { ListCoordinatorsDto } from "./dto/list-coordinators.dto";
import { UpdateCoordinatorStatusDto } from "./dto/update-coordinator-status.dto";
import { UpdateCoordinatorDto } from "./dto/update-coordinator.dto";

@ApiTags("coordinators")
@ApiBearerAuth()
@Roles(UserRole.ADMIN)
@Controller("coordinators")
export class CoordinatorsController {
  constructor(private readonly coordinators: CoordinatorsService) {}

  @Get()
  @ApiOperation({ summary: "Lista coordinadores con búsqueda y paginación" })
  list(@Query() query: ListCoordinatorsDto) {
    return this.coordinators.list(query);
  }

  @Get("careers")
  @ApiOperation({ summary: "Lista carreras disponibles para asignación" })
  careers() {
    return this.coordinators.listCareers();
  }

  @Post()
  @ApiOperation({ summary: "Registra un coordinador" })
  create(
    @Body() input: CreateCoordinatorDto,
    @CurrentUser() actor: AuthenticatedUser
  ) {
    return this.coordinators.create(input, actor.id);
  }

  @Patch(":id")
  @ApiOperation({ summary: "Edita un coordinador y sus carreras" })
  update(
    @Param("id", ParseUUIDPipe) id: string,
    @Body() input: UpdateCoordinatorDto,
    @CurrentUser() actor: AuthenticatedUser
  ) {
    return this.coordinators.update(id, input, actor.id);
  }

  @Patch(":id/status")
  @ApiOperation({ summary: "Activa o desactiva un coordinador" })
  updateStatus(
    @Param("id", ParseUUIDPipe) id: string,
    @Body() input: UpdateCoordinatorStatusDto,
    @CurrentUser() actor: AuthenticatedUser
  ) {
    return this.coordinators.updateStatus(id, input.status, actor.id);
  }

  @Delete(":id")
  @HttpCode(204)
  @ApiOperation({ summary: "Elimina un coordinador sin actividad académica" })
  remove(
    @Param("id", ParseUUIDPipe) id: string,
    @CurrentUser() actor: AuthenticatedUser
  ) {
    return this.coordinators.remove(id, actor.id);
  }
}
