import { ApiProperty } from "@nestjs/swagger";
import { AcademicPeriodStatus } from "@prisma/client";
import { IsEnum } from "class-validator";

export class UpdatePeriodStatusDto {
  @ApiProperty({ enum: AcademicPeriodStatus })
  @IsEnum(AcademicPeriodStatus)
  status!: AcademicPeriodStatus;
}
