import { Controller, Get, Header, Query, Res } from "@nestjs/common";
import { ApiBearerAuth, ApiOperation, ApiProduces, ApiQuery, ApiTags } from "@nestjs/swagger";
import type { Response } from "express";
import { UserRole } from "@prisma/client";
import type { AuthenticatedUser } from "../auth/auth.types";
import { CurrentUser } from "../auth/decorators/current-user.decorator";
import { Roles } from "../auth/decorators/roles.decorator";
import { ExportsService } from "./exports.service";

@ApiTags("exports")
@ApiBearerAuth()
@Roles(UserRole.ADMIN, UserRole.COORDINATOR)
@Controller("exports")
export class ExportsController {
  constructor(private readonly exports: ExportsService) {}

  @Get("horarios")
  @ApiOperation({
    summary:
      "Descarga el libro de horarios en .xlsx, con la hoja Inicio, el horario diurno y el nocturno. " +
      "Solo incluye horarios publicados."
  })
  @ApiQuery({ name: "periodId", description: "Periodo académico" })
  @ApiQuery({ name: "careerId", description: "Carrera" })
  @ApiProduces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
  @Header("Cache-Control", "no-store")
  async horarios(
    @Query("periodId") periodId: string,
    @Query("careerId") careerId: string,
    @CurrentUser() actor: AuthenticatedUser,
    @Res() res: Response
  ) {
    if (!periodId || !careerId) {
      res.status(400).json({ message: "Indica el periodo y la carrera que quieres exportar." });
      return;
    }
    const { buffer, fileName } = await this.exports.buildWorkbook(periodId, careerId);
    void actor;
    res.setHeader(
      "Content-Disposition",
      `attachment; filename="${fileName.replace(/"/g, "")}"; filename*=UTF-8''${encodeURIComponent(fileName)}`
    );
    res.setHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    res.setHeader("Content-Length", buffer.length);
    res.end(buffer);
  }
}