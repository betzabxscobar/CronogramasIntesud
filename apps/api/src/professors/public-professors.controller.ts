import {
  Controller,
  Get,
  Param,
  ParseUUIDPipe,
  Query
} from "@nestjs/common";
import { ApiOperation, ApiTags } from "@nestjs/swagger";
import { Public } from "../auth/decorators/public.decorator";
import { ProfessorsService } from "./professors.service";

@Public()
@ApiTags("public-professors")
@Controller("public/professors")
export class PublicProfessorsController {
  constructor(private readonly professors: ProfessorsService) {}

  @Get()
  @ApiOperation({ summary: "Lista perfiles públicos de profesores" })
  list(@Query("search") search?: string, @Query("careerId") careerId?: string) {
    return this.professors.listPublic(search, careerId);
  }

  @Get(":id")
  @ApiOperation({ summary: "Obtiene el perfil público de un profesor" })
  detail(@Param("id", ParseUUIDPipe) id: string) {
    return this.professors.publicDetail(id);
  }
}
