import { ApiProperty, ApiPropertyOptional } from "@nestjs/swagger";
import { Type } from "class-transformer";
import { IsBoolean, IsIn, IsInt, IsOptional, IsUUID, Max, Min } from "class-validator";

export class PreviewImportDto {
  @ApiProperty({ description: "Carrera a la que se asignará lo importado" })
  @IsUUID("4")
  careerId!: string;

  @ApiPropertyOptional({
    description:
      "Columna del bloque de asignaturas a importar. Si se omite se leen todos " +
      "los catálogos del libro y se avisa de ello.",
    example: 42
  })
  @IsOptional()
  // En multipart los campos llegan como texto: sin esta pista @IsInt falla.
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(400)
  catalogColumn?: number;

  @ApiPropertyOptional({
    description:
      "Si se marca, también se leen las hojas de horario (diurna y nocturna) para " +
      "traer los bloques de clase.",
    default: false
  })
  @IsOptional()
  @Type(() => Boolean)
  @IsBoolean()
  includeSchedule?: boolean;

  @ApiPropertyOptional({
    description:
      "Periodo académico al que pertenecen los horarios. Necesario cuando se " +
      "importan bloques, porque cada bloque va a un curso de un nivel de ese periodo."
  })
  @IsOptional()
  @IsUUID("4")
  periodId?: string;

  @ApiPropertyOptional({
    description:
      "Qué turnos traer de las hojas de horario. Permite migrar el horario diurno " +
      "aunque el nocturno tenga problemas, porque se validan por separado.",
    enum: ["ALL", "DAY", "NIGHT"],
    default: "ALL"
  })
  @IsOptional()
  @IsIn(["ALL", "DAY", "NIGHT"])
  shift?: "ALL" | "DAY" | "NIGHT";
}