import { ApiProperty } from "@nestjs/swagger";
import { IsString, Length } from "class-validator";

export class CreateCareerDto {
  @ApiProperty({ example: "Desarrollo de Software" })
  @IsString()
  @Length(2, 150)
  name!: string;
}
