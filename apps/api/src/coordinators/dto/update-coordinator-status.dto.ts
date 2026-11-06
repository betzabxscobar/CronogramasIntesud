import { ApiProperty } from "@nestjs/swagger";
import { RecordStatus } from "@prisma/client";
import { IsEnum } from "class-validator";

export class UpdateCoordinatorStatusDto {
  @ApiProperty({ enum: RecordStatus })
  @IsEnum(RecordStatus)
  status!: RecordStatus;
}
