import { ApiProperty, ApiPropertyOptional } from "@nestjs/swagger";
import { IsBoolean, IsInt, IsOptional, IsString, IsUUID, Length, Max, MaxLength, Min } from "class-validator";

export class CreateSubjectDto {
  @ApiProperty()
  @IsUUID("4")
  careerId!: string;

  @ApiProperty()
  @IsUUID("4")
  levelId!: string;

  @ApiProperty({ example: "Programación I" })
  @IsString()
  @Length(2, 140)
  name!: string;

  @ApiPropertyOptional({ example: "DS-PRO-101" })
  @IsOptional()
  @IsString()
  @MaxLength(30)
  code?: string;

  @ApiPropertyOptional({ example: 4 })
  @IsOptional()
  @IsInt()
  @Min(1)
  @Max(40)
  weeklyHours?: number;

  @ApiPropertyOptional({ default: false })
  @IsOptional()
  @IsBoolean()
  requiresIndividualComputer?: boolean;
}
