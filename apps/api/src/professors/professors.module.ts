import { Module } from "@nestjs/common";
import { ProfessorsController } from "./professors.controller";
import { ProfessorsService } from "./professors.service";
import { PublicProfessorsController } from "./public-professors.controller";

@Module({
  controllers: [ProfessorsController, PublicProfessorsController],
  providers: [ProfessorsService]
})
export class ProfessorsModule {}
