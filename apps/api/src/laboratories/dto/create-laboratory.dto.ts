import { ApiProperty, ApiPropertyOptional } from "@nestjs/swagger";
import {
  ArrayMinSize,
  ArrayUnique,
  IsArray,
  IsBoolean,
  IsEnum,
  IsInt,
  IsOptional,
  IsString,
  IsUUID,
  Length,
  Max,
  MaxLength,
  Min
} from "class-validator";
import { SpaceType } from "@prisma/client";

export class CreateLaboratoryDto {
  @ApiProperty({ enum: SpaceType, example: SpaceType.LABORATORY })
  @IsEnum(SpaceType)
  type!: SpaceType;

  @ApiProperty({ example: "Laboratorio de Software" })
  @IsString()
  @Length(2, 120)
  name!: string;

  @ApiPropertyOptional({ example: "LAB-SW-01", description: "Identificador interno generado automáticamente" })
  @IsOptional()
  @IsString()
  @Length(2, 30)
  code?: string;

  @ApiPropertyOptional({ example: "Edificio principal, segundo piso" })
  @IsOptional()
  @IsString()
  @MaxLength(180)
  location?: string;

  @ApiPropertyOptional({ example: 30 })
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(1000)
  peopleCapacity?: number;

  @ApiPropertyOptional({ default: 0 })
  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(1000)
  studentComputers?: number;

  @ApiPropertyOptional({ default: 0 })
  @IsOptional()
  @IsInt()
  @Min(0)
  @Max(100)
  teacherComputers?: number;

  @ApiPropertyOptional({ default: false })
  @IsOptional()
  @IsBoolean()
  hasProjector?: boolean;

  @ApiPropertyOptional({ default: false })
  @IsOptional()
  @IsBoolean()
  hasDigitalBoard?: boolean;

  @ApiProperty({ type: [String] })
  @IsArray()
  @ArrayMinSize(1)
  @ArrayUnique()
  @IsUUID("4", { each: true })
  careerIds!: string[];
}
