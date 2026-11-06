import { ApiProperty, ApiPropertyOptional } from "@nestjs/swagger";
import { DayOfWeek } from "@prisma/client";
import { IsEnum, IsInt, IsOptional, IsString, IsUUID, Matches, Max, MaxLength, Min } from "class-validator";

export class SaveBlockDto {
  @ApiProperty() @IsUUID("4") subjectId!: string;
  @ApiProperty() @IsUUID("4") professorId!: string;
  @ApiPropertyOptional() @IsOptional() @IsUUID("4") spaceId?: string;
  @ApiProperty({ enum: DayOfWeek }) @IsEnum(DayOfWeek) dayOfWeek!: DayOfWeek;
  @ApiProperty({ example: "08:00" }) @Matches(/^([01]\d|2[0-3]):[0-5]\d$/) startTime!: string;
  @ApiProperty({ example: "10:00" }) @Matches(/^([01]\d|2[0-3]):[0-5]\d$/) endTime!: string;
  @ApiPropertyOptional() @IsOptional() @IsInt() @Min(1) @Max(20) moduleNumber?: number;
  @ApiPropertyOptional() @IsOptional() @IsString() @MaxLength(250) notes?: string;
}
