import { Module } from "@nestjs/common";
import { ConfigModule } from "@nestjs/config";
import { ThrottlerModule } from "@nestjs/throttler";
import { AppController } from "./app.controller";
import { AuthModule } from "./auth/auth.module";
import { CoordinatorsModule } from "./coordinators/coordinators.module";
import { CareersModule } from "./careers/careers.module";
import { ProfessorsModule } from "./professors/professors.module";
import { LaboratoriesModule } from "./laboratories/laboratories.module";
import { MailModule } from "./mail/mail.module";
import { PrismaModule } from "./prisma/prisma.module";
import { AcademicStructureModule } from "./academic-structure/academic-structure.module";
import { AcademicPeriodsModule } from "./academic-periods/academic-periods.module";
import { SchedulesModule } from "./schedules/schedules.module";
import { ImportsModule } from "./imports/imports.module";
import { ExportsModule } from "./exports/exports.module";

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: ["../../.env", ".env"]
    }),
    ThrottlerModule.forRoot([
      {
        ttl: 60_000,
        limit: 120
      }
    ]),
    PrismaModule,
    MailModule,
    AuthModule,
    CoordinatorsModule,
    CareersModule,
    ProfessorsModule,
    LaboratoriesModule,
    AcademicStructureModule,
    AcademicPeriodsModule,
    SchedulesModule,
    ImportsModule,
    ExportsModule,
    ExportsModule
  ],
  controllers: [AppController]
})
export class AppModule {}
