import {
  Body,
  Controller,
  Delete,
  Get,
  HttpCode,
  Param,
  ParseUUIDPipe,
  Post,
  UploadedFile,
  UseInterceptors
} from "@nestjs/common";
import { FileInterceptor } from "@nestjs/platform-express";
import { ApiBearerAuth, ApiBody, ApiConsumes, ApiOperation, ApiTags } from "@nestjs/swagger";
import { UserRole } from "@prisma/client";
import type { AuthenticatedUser } from "../auth/auth.types";
import { CurrentUser } from "../auth/decorators/current-user.decorator";
import { Roles } from "../auth/decorators/roles.decorator";
import { PreviewImportDto } from "./dto/preview-import.dto";
import { ImportsService } from "./imports.service";

@ApiTags("imports")
@ApiBearerAuth()
@Roles(UserRole.ADMIN, UserRole.COORDINATOR)
@Controller("imports")
export class ImportsController {
  constructor(private readonly imports: ImportsService) {}

  @Get()
  @ApiOperation({ summary: "Importaciones recientes" })
  list(@CurrentUser() actor: AuthenticatedUser) {
    return this.imports.list(actor);
  }

  @Post("preview")
  @ApiOperation({
    summary:
      "Sube un libro .xlsx, lo valida y devuelve la vista previa. No escribe nada en las tablas de dominio."
  })
  @ApiConsumes("multipart/form-data")
  @ApiBody({
    schema: {
      type: "object",
      required: ["file", "careerId"],
      properties: {
        file: { type: "string", format: "binary" },
        careerId: { type: "string", format: "uuid" },
        catalogColumn: { type: "integer", description: "Columna del bloque de asignaturas a importar" },
        includeSchedule: {
          type: "boolean",
          description: "Traer también los bloques de horario de las hojas diurna y nocturna"
        },
        periodId: {
          type: "string",
          format: "uuid",
          description: "Periodo académico de los horarios; obligatorio si includeSchedule"
        }
      }
    }
  })
  @UseInterceptors(FileInterceptor("file", { limits: { fileSize: 15 * 1024 * 1024 } }))
  preview(
    @UploadedFile() file: { buffer: Buffer; originalname: string },
    @Body() body: PreviewImportDto,
    @CurrentUser() actor: AuthenticatedUser
  ) {
    return this.imports.preview(file, actor, body.careerId, {
      catalogColumn: body.catalogColumn,
      includeSchedule: body.includeSchedule,
      periodId: body.periodId,
      shift: body.shift
    });
  }

  @Post(":id/commit")
  @ApiOperation({ summary: "Aplica las filas válidas de una importación vista en previa" })
  commit(@Param("id", ParseUUIDPipe) id: string, @CurrentUser() actor: AuthenticatedUser) {
    return this.imports.commit(id, actor);
  }

  @Get(":id")
  @ApiOperation({ summary: "Detalle de una importación" })
  find(@Param("id", ParseUUIDPipe) id: string) {
    return this.imports.findJob(id);
  }

  @Delete(":id")
  @HttpCode(200)
  @ApiOperation({ summary: "Cancela una importación que todavía no se aplicó" })
  cancel(@Param("id", ParseUUIDPipe) id: string, @CurrentUser() actor: AuthenticatedUser) {
    return this.imports.cancel(id, actor);
  }
}
