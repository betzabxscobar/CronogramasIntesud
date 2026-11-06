import { ApiProperty } from "@nestjs/swagger";
import { Shift } from "@prisma/client";
import { IsEnum, IsUUID } from "class-validator";

export class CreateScheduleDto {
  @ApiProperty()
  @IsUUID("4")
  courseId!: string;

  @ApiProperty({ enum: Shift })
  @IsEnum(Shift)
  shift!: Shift;
}
