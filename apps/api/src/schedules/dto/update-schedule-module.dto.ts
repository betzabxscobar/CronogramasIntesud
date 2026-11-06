import { ApiProperty } from "@nestjs/swagger";
import { IsDateString } from "class-validator";

export class UpdateScheduleModuleDto {
  @ApiProperty({ example: "2026-05-11" })
  @IsDateString()
  startDate!: string;

  @ApiProperty({ example: "2026-05-29" })
  @IsDateString()
  endDate!: string;
}
