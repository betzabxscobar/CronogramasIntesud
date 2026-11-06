import { ApiProperty } from "@nestjs/swagger";
import { IsDateString, IsString, Length } from "class-validator";

export class CreatePeriodDto {
  @ApiProperty({ example: "2026 - 2026" })
  @IsString()
  @Length(3, 80)
  name!: string;

  @ApiProperty({ example: "2026-05-11" })
  @IsDateString()
  startDate!: string;

  @ApiProperty({ example: "2026-09-18" })
  @IsDateString()
  endDate!: string;
}
