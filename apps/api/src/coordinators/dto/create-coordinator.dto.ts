import { ApiProperty } from "@nestjs/swagger";
import {
  ArrayUnique,
  ArrayMinSize,
  IsArray,
  IsEmail,
  IsString,
  IsUUID,
  Length
} from "class-validator";

export class CreateCoordinatorDto {
  @ApiProperty({ example: "1712345678" })
  @IsString()
  @Length(6, 20)
  nationalId!: string;

  @ApiProperty({ example: "María Fernanda" })
  @IsString()
  @Length(2, 100)
  firstNames!: string;

  @ApiProperty({ example: "Pérez López" })
  @IsString()
  @Length(2, 100)
  lastNames!: string;

  @ApiProperty({ example: "maria.perez@instituto.edu.ec" })
  @IsEmail()
  @Length(5, 180)
  email!: string;

  @ApiProperty({ type: [String] })
  @IsArray()
  @ArrayMinSize(1)
  @ArrayUnique()
  @IsUUID("4", { each: true })
  careerIds!: string[];
}
