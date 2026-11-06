import { ApiProperty } from "@nestjs/swagger";
import { IsInt, IsString, IsUUID, Length, Max, Min } from "class-validator";

export class CreateLevelDto {
  @ApiProperty()
  @IsUUID("4")
  careerId!: string;

  @ApiProperty({ example: "Primer nivel" })
  @IsString()
  @Length(2, 80)
  name!: string;

  @ApiProperty({ example: 1 })
  @IsInt()
  @Min(1)
  @Max(20)
  order!: number;
}
