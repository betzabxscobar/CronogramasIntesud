import { ApiProperty, ApiPropertyOptional } from "@nestjs/swagger";
import {
  ArrayMinSize,
  ArrayUnique,
  IsArray,
  IsBoolean,
  IsOptional,
  IsString,
  IsUUID,
  Length,
  MaxLength,
  Matches
} from "class-validator";

export class CreateProfessorDto {
  @ApiPropertyOptional({ example: "1712345678" })
  @IsOptional()
  @IsString()
  @Length(6, 20)
  nationalId?: string;

  @ApiProperty({ example: "Ana María" })
  @IsString()
  @Length(2, 100)
  firstNames!: string;

  @ApiProperty({ example: "Torres Molina" })
  @IsString()
  @Length(2, 100)
  lastNames!: string;

  @ApiPropertyOptional({ example: "Magíster en Educación" })
  @IsOptional()
  @IsString()
  @MaxLength(180)
  professionalTitle?: string;

  @ApiPropertyOptional({ example: "#A9CBE4", default: "#A9CBE4" })
  @IsOptional()
  @IsString()
  @Matches(/^#[0-9A-Fa-f]{6}$/, { message: "El color debe tener formato hexadecimal #RRGGBB" })
  scheduleColor?: string;

  @ApiPropertyOptional({ default: true })
  @IsOptional()
  @IsBoolean()
  publicProfileEnabled?: boolean;

  @ApiProperty({ type: [String] })
  @IsArray()
  @ArrayMinSize(1)
  @ArrayUnique()
  @IsUUID("4", { each: true })
  careerIds!: string[];
}
