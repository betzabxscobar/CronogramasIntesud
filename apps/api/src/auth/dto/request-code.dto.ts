import { ApiProperty } from "@nestjs/swagger";
import { IsNotEmpty, IsString, Length } from "class-validator";

export class RequestCodeDto {
  @ApiProperty({ example: "1712345678" })
  @IsString()
  @IsNotEmpty()
  @Length(6, 20)
  nationalId!: string;
}
