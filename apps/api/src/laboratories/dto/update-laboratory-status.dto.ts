import { ApiProperty } from "@nestjs/swagger";
import { SpaceStatus } from "@prisma/client";
import { IsEnum } from "class-validator";

export class UpdateLaboratoryStatusDto {
  @ApiProperty({ enum: SpaceStatus })
  @IsEnum(SpaceStatus)
  status!: SpaceStatus;
}
