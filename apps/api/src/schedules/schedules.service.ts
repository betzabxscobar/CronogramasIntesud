import { ConflictException, ForbiddenException, Injectable, NotFoundException } from "@nestjs/common";
import { DayOfWeek, Prisma, RecordStatus, ScheduleStatus, Shift, SpaceStatus, UserRole } from "@prisma/client";
import type { AuthenticatedUser } from "../auth/auth.types";
import { PrismaService } from "../prisma/prisma.service";
import type { CreateScheduleDto } from "./dto/create-schedule.dto";
import type { SaveBlockDto } from "./dto/save-block.dto";
import type { UpdateScheduleModuleDto } from "./dto/update-schedule-module.dto";
import { professionalTitlePrefix } from "../professors/professional-title";

const blockSelect = {
  id: true, dayOfWeek: true, startTime: true, endTime: true, validFrom: true, validUntil: true, moduleNumber: true, notes: true,
  subject: { select: { id: true, name: true } },
  professor: { select: { id: true, firstNames: true, lastNames: true, professionalTitle: true, scheduleColor: true } },
  space: { select: { id: true, name: true, code: true, location: true, type: true } }
} satisfies Prisma.ScheduleBlockSelect;

@Injectable()
export class SchedulesService {
  constructor(private readonly prisma: PrismaService) {}

  context(actor: AuthenticatedUser) {
    const access = actor.role === UserRole.ADMIN ? undefined : { in: actor.careerIds };
    return this.prisma.academicPeriod.findMany({
      where: { status: { in: ["ACTIVE", "DRAFT"] } },
      select: { id: true, name: true, status: true, courses: { where: { status: RecordStatus.ACTIVE, level: { careerId: access } }, orderBy: [{ level: { career: { name: "asc" } } }, { level: { order: "asc" } }], select: { id: true, name: true, studentCount: true, level: { select: { id: true, name: true, order: true, career: { select: { id: true, name: true } } } } } } },
      orderBy: { startDate: "desc" }
    });
  }

  async list(periodId: string, actor: AuthenticatedUser) {
    return this.prisma.schedule.findMany({
      where: { course: { periodId, level: { careerId: actor.role === UserRole.ADMIN ? undefined : { in: actor.careerIds } } } },
      select: { id: true, shift: true, updatedAt: true, course: { select: { id: true, name: true, level: { select: { name: true, order: true, career: { select: { id: true, name: true } } } } } }, versions: { orderBy: { version: "desc" }, take: 1, select: { id: true, version: true, status: true, updatedAt: true, _count: { select: { blocks: true } } } } },
      orderBy: [{ course: { level: { career: { name: "asc" } } } }, { course: { level: { order: "asc" } } }, { shift: "asc" }]
    });
  }

  async detail(id: string, actor: AuthenticatedUser) {
    const schedule = await this.findSchedule(id, actor);
    const version = await this.prisma.scheduleVersion.findFirst({ where: { scheduleId: id }, orderBy: { version: "desc" }, select: { id: true, version: true, status: true, blocks: { select: blockSelect, orderBy: [{ dayOfWeek: "asc" }, { startTime: "asc" }] } } });
    const careerId = schedule.course.level.career.id;
    const [subjects, professors, spaces] = await Promise.all([
      this.prisma.subject.findMany({ where: { careerId, levelId: schedule.course.level.id, status: RecordStatus.ACTIVE }, select: { id: true, name: true, weeklyHours: true }, orderBy: { name: "asc" } }),
      this.prisma.professor.findMany({ where: { status: RecordStatus.ACTIVE, careers: { some: { careerId, status: RecordStatus.ACTIVE } } }, select: { id: true, firstNames: true, lastNames: true, professionalTitle: true, scheduleColor: true }, orderBy: [{ lastNames: "asc" }, { firstNames: "asc" }] }),
      this.prisma.academicSpace.findMany({ where: { status: SpaceStatus.AVAILABLE, careers: { some: { careerId } } }, select: { id: true, name: true, code: true, location: true, type: true, peopleCapacity: true }, orderBy: [{ type: "asc" }, { name: "asc" }] })
    ]);
    const modules = schedule.shift === Shift.NIGHT ? await this.prisma.scheduleModule.findMany({ where: { scheduleId: id }, orderBy: { moduleNumber: "asc" } }) : [];
    // La versión vigente es la que se exporta y reparte; se expone aparte para
    // que la interfaz pueda decir si el horario ya se puede descargar.
    const publicada = await this.prisma.scheduleVersion.findFirst({ where: { scheduleId: id, status: ScheduleStatus.PUBLISHED }, orderBy: { version: "desc" }, select: { version: true, publishedAt: true } });
    return { ...schedule, modules, publishedVersion: publicada, version: version ? { ...version, blocks: version.blocks.map((block) => ({ ...block, startTime: this.timeString(block.startTime), endTime: this.timeString(block.endTime), professor: { ...block.professor, professionalTitleAbbreviation: professionalTitlePrefix(block.professor.professionalTitle) } })) } : null, catalogs: { subjects, professors: professors.map((professor) => ({ ...professor, professionalTitleAbbreviation: professionalTitlePrefix(professor.professionalTitle) })), spaces }, days: Object.values(DayOfWeek) };
  }

  async create(input: CreateScheduleDto, actor: AuthenticatedUser) {
    const course = await this.prisma.academicCourse.findUnique({ where: { id: input.courseId }, select: { id: true, period: { select: { startDate: true } }, level: { select: { careerId: true } } } });
    if (!course) throw new NotFoundException("Curso no encontrado");
    this.assertCareer(course.level.careerId, actor);
    return this.prisma.$transaction(async (tx) => {
      const schedule = await tx.schedule.upsert({ where: { courseId_shift: { courseId: input.courseId, shift: input.shift } }, update: {}, create: { courseId: input.courseId, shift: input.shift } });
      if (input.shift === Shift.NIGHT) await this.ensureNightModules(tx, schedule.id, course.period.startDate);
      let version = await tx.scheduleVersion.findFirst({ where: { scheduleId: schedule.id, status: ScheduleStatus.DRAFT }, orderBy: { version: "desc" } });
      if (!version) {
        const latest = await tx.scheduleVersion.aggregate({ where: { scheduleId: schedule.id }, _max: { version: true } });
        version = await tx.scheduleVersion.create({ data: { scheduleId: schedule.id, version: (latest._max.version ?? 0) + 1, createdById: actor.id, modifiedById: actor.id } });
        await tx.auditLog.create({ data: { userId: actor.id, action: "SCHEDULE_DRAFT_CREATED", entityType: "Schedule", entityId: schedule.id, newData: { courseId: input.courseId, shift: input.shift, version: version.version } } });
      }
      return { id: schedule.id, versionId: version.id };
    });
  }

  async addBlock(scheduleId: string, input: SaveBlockDto, actor: AuthenticatedUser) {
    const context = await this.blockContext(scheduleId, actor);
    await this.validateBlock(context, input);
    return this.prisma.$transaction(async (tx) => {
      const block = await tx.scheduleBlock.create({ data: { versionId: context.version.id, ...await this.blockData(context, input) }, select: blockSelect });
      await tx.scheduleVersion.update({ where: { id: context.version.id }, data: { modifiedById: actor.id } });
      await tx.auditLog.create({ data: { userId: actor.id, action: "SCHEDULE_BLOCK_CREATED", entityType: "ScheduleBlock", entityId: block.id, newData: { scheduleId, subjectId: input.subjectId, professorId: input.professorId, spaceId: input.spaceId, dayOfWeek: input.dayOfWeek, startTime: input.startTime, endTime: input.endTime } } });
      return block;
    });
  }

  async updateBlock(scheduleId: string, blockId: string, input: SaveBlockDto, actor: AuthenticatedUser) {
    const context = await this.blockContext(scheduleId, actor);
    const current = await this.prisma.scheduleBlock.findFirst({ where: { id: blockId, versionId: context.version.id } });
    if (!current) throw new NotFoundException("Bloque de horario no encontrado");
    await this.validateBlock(context, input, blockId);
    return this.prisma.$transaction(async (tx) => {
      const block = await tx.scheduleBlock.update({ where: { id: blockId }, data: await this.blockData(context, input), select: blockSelect });
      await tx.scheduleVersion.update({ where: { id: context.version.id }, data: { modifiedById: actor.id } });
      await tx.auditLog.create({ data: { userId: actor.id, action: "SCHEDULE_BLOCK_UPDATED", entityType: "ScheduleBlock", entityId: blockId, previousData: { subjectId: current.subjectId, professorId: current.professorId, spaceId: current.spaceId, dayOfWeek: current.dayOfWeek }, newData: { subjectId: input.subjectId, professorId: input.professorId, spaceId: input.spaceId, dayOfWeek: input.dayOfWeek, startTime: input.startTime, endTime: input.endTime } } });
      return block;
    });
  }

  async removeBlock(scheduleId: string, blockId: string, actor: AuthenticatedUser) {
    const context = await this.blockContext(scheduleId, actor);
    const block = await this.prisma.scheduleBlock.findFirst({ where: { id: blockId, versionId: context.version.id } });
    if (!block) throw new NotFoundException("Bloque de horario no encontrado");
    await this.prisma.$transaction([this.prisma.auditLog.create({ data: { userId: actor.id, action: "SCHEDULE_BLOCK_DELETED", entityType: "ScheduleBlock", entityId: blockId, previousData: { scheduleId, subjectId: block.subjectId, professorId: block.professorId, spaceId: block.spaceId } } }), this.prisma.scheduleBlock.delete({ where: { id: blockId } })]);
  }

  async remove(scheduleId: string, actor: AuthenticatedUser) {
    const schedule = await this.findSchedule(scheduleId, actor);
    const protectedVersion = await this.prisma.scheduleVersion.findFirst({ where: { scheduleId, status: { not: ScheduleStatus.DRAFT } }, select: { id: true } });
    if (protectedVersion) throw new ConflictException("No se puede eliminar porque el horario ya fue publicado o cerrado");
    await this.prisma.$transaction(async (tx) => {
      await tx.auditLog.create({ data: { userId: actor.id, action: "SCHEDULE_DRAFT_DELETED", entityType: "Schedule", entityId: scheduleId, previousData: { career: schedule.course.level.career.name, level: schedule.course.level.name, shift: schedule.shift } } });
      await tx.schedule.delete({ where: { id: scheduleId } });
    });
  }

  /**
   * Publica el borrador actual y abre el siguiente con las mismas clases.
   *
   * Publicar no deja al usuario sin horario: se congela la versión que se
   * reparte y se abre la siguiente como borrador, ya que el sistema solo edita
   * borradores. Sin esto, publicar vaciaría la pantalla y sería una trampa.
   */
  async publish(scheduleId: string, actor: AuthenticatedUser) {
    const schedule = await this.findSchedule(scheduleId, actor);
    const borrador = await this.prisma.scheduleVersion.findFirst({
      where: { scheduleId, status: ScheduleStatus.DRAFT },
      orderBy: { version: "desc" }
    });
    if (!borrador) throw new ConflictException("El horario no tiene un borrador para publicar");

    const bloques = await this.prisma.scheduleBlock.findMany({
      where: { versionId: borrador.id },
      select: {
        subjectId: true, professorId: true, spaceId: true, dayOfWeek: true,
        startTime: true, endTime: true, validFrom: true, validUntil: true,
        moduleNumber: true, notes: true
      }
    });
    if (!bloques.length) throw new ConflictException("No se puede publicar un horario sin clases");

    return this.prisma.$transaction(async (tx) => {
      const publicada = await tx.scheduleVersion.update({
        where: { id: borrador.id },
        data: {
          status: ScheduleStatus.PUBLISHED,
          publishedById: actor.id,
          publishedAt: new Date(),
          reason: "Publicado desde el horario"
        }
      });

      // Solo puede haber una versión vigente por horario.
      await tx.scheduleVersion.updateMany({
        where: { scheduleId, status: ScheduleStatus.PUBLISHED, id: { not: publicada.id } },
        data: { status: ScheduleStatus.UNPUBLISHED, unpublishedAt: new Date() }
      });

      const nueva = await tx.scheduleVersion.create({
        data: {
          scheduleId,
          version: publicada.version + 1,
          status: ScheduleStatus.DRAFT,
          reason: "Borrador abierto tras la publicación",
          createdById: actor.id,
          modifiedById: actor.id
        }
      });
      if (bloques.length) {
        await tx.scheduleBlock.createMany({
          data: bloques.map((block) => ({ ...block, versionId: nueva.id }))
        });
      }

      await tx.auditLog.create({
        data: {
          userId: actor.id,
          action: "SCHEDULE_PUBLISHED",
          entityType: "Schedule",
          entityId: scheduleId,
          newData: {
            career: schedule.course.level.career.name,
            level: schedule.course.level.name,
            shift: schedule.shift,
            publishedVersion: publicada.version,
            blocks: bloques.length,
            newDraftVersion: nueva.version
          }
        }
      });
      return { publishedVersion: publicada.version, draftVersion: nueva.version, blocks: bloques.length };
    });
  }

  async updateModule(scheduleId: string, moduleNumber: number, input: UpdateScheduleModuleDto, actor: AuthenticatedUser) {
    const schedule = await this.findSchedule(scheduleId, actor);
    if (schedule.shift !== Shift.NIGHT) throw new ConflictException("Los módulos corresponden únicamente a horarios nocturnos");
    if (!Number.isInteger(moduleNumber) || moduleNumber < 1 || moduleNumber > 6) throw new ConflictException("El módulo debe estar entre 1 y 6");
    const startDate = this.date(input.startDate), endDate = this.date(input.endDate);
    if (endDate < startDate) throw new ConflictException("La fecha final debe ser igual o posterior a la fecha inicial");
    return this.prisma.$transaction(async (tx) => {
      const module = await tx.scheduleModule.update({ where: { scheduleId_moduleNumber: { scheduleId, moduleNumber } }, data: { startDate, endDate } });
      const version = await tx.scheduleVersion.findFirst({ where: { scheduleId, status: ScheduleStatus.DRAFT }, orderBy: { version: "desc" } });
      if (version) await tx.scheduleBlock.updateMany({ where: { versionId: version.id, moduleNumber }, data: { validFrom: startDate, validUntil: endDate } });
      await tx.auditLog.create({ data: { userId: actor.id, action: "SCHEDULE_MODULE_DATES_UPDATED", entityType: "ScheduleModule", entityId: module.id, newData: { scheduleId, moduleNumber, startDate: input.startDate, endDate: input.endDate } } });
      return module;
    });
  }

  private async blockContext(scheduleId: string, actor: AuthenticatedUser) {
    const schedule = await this.findSchedule(scheduleId, actor);
    const version = await this.prisma.scheduleVersion.findFirst({ where: { scheduleId, status: ScheduleStatus.DRAFT }, orderBy: { version: "desc" } });
    if (!version) throw new ConflictException("El horario no tiene un borrador editable");
    return { schedule, version };
  }

  private async validateBlock(context: Awaited<ReturnType<SchedulesService["blockContext"]>>, input: SaveBlockDto, excludeId?: string) {
    const startTime = this.time(input.startTime), endTime = this.time(input.endTime);
    if (endTime <= startTime) throw new ConflictException("La hora de finalización debe ser posterior a la hora de inicio");
    const careerId = context.schedule.course.level.career.id, levelId = context.schedule.course.level.id, periodId = context.schedule.course.period.id;
    const scheduleModule = context.schedule.shift === Shift.NIGHT && input.moduleNumber
      ? await this.prisma.scheduleModule.findUnique({ where: { scheduleId_moduleNumber: { scheduleId: context.schedule.id, moduleNumber: input.moduleNumber } } })
      : null;
    if (context.schedule.shift === Shift.NIGHT && !scheduleModule) throw new ConflictException("Selecciona un módulo nocturno válido");
    const [subject, professor, space] = await Promise.all([
      this.prisma.subject.findFirst({ where: { id: input.subjectId, careerId, levelId, status: RecordStatus.ACTIVE } }),
      this.prisma.professor.findFirst({ where: { id: input.professorId, status: RecordStatus.ACTIVE, careers: { some: { careerId, status: RecordStatus.ACTIVE } } } }),
      input.spaceId ? this.prisma.academicSpace.findFirst({ where: { id: input.spaceId, status: SpaceStatus.AVAILABLE, careers: { some: { careerId } } } }) : null
    ]);
    if (!subject) throw new ConflictException("La materia no pertenece al nivel seleccionado");
    if (!professor) throw new ConflictException("El profesor no está vinculado con esta carrera");
    if (context.schedule.shift === Shift.DAY && !space) throw new ConflictException("El aula o laboratorio no está disponible para esta carrera");
    if (space?.peopleCapacity && context.schedule.course.studentCount && context.schedule.course.studentCount > space.peopleCapacity) throw new ConflictException(`La capacidad del espacio es insuficiente: ${space.peopleCapacity} lugares para ${context.schedule.course.studentCount} estudiantes`);
    const temporalScope = scheduleModule ? { moduleNumber: input.moduleNumber } : { dayOfWeek: input.dayOfWeek };
    const overlap = { ...temporalScope, startTime: { lt: endTime }, endTime: { gt: startTime }, id: excludeId ? { not: excludeId } : undefined, version: { status: { in: [ScheduleStatus.DRAFT, ScheduleStatus.PUBLISHED] }, schedule: { course: { periodId } } } } satisfies Prisma.ScheduleBlockWhereInput;
    const [courseConflict, professorConflict, spaceConflict] = await Promise.all([
      this.prisma.scheduleBlock.findFirst({ where: { ...overlap, version: { status: { in: [ScheduleStatus.DRAFT, ScheduleStatus.PUBLISHED] }, schedule: { courseId: context.schedule.course.id } } }, select: { id: true } }),
      this.prisma.scheduleBlock.findFirst({ where: { ...overlap, professorId: input.professorId }, select: { id: true } }),
      input.spaceId ? this.prisma.scheduleBlock.findFirst({ where: { ...overlap, spaceId: input.spaceId }, select: { id: true } }) : null
    ]);
    const conflicts = [courseConflict && "El curso ya tiene una clase en ese horario", professorConflict && "El profesor ya está asignado en ese horario", spaceConflict && "El aula o laboratorio ya está ocupado en ese horario"].filter(Boolean);
    if (conflicts.length) throw new ConflictException(conflicts);
  }

  private async findSchedule(id: string, actor: AuthenticatedUser) {
    const schedule = await this.prisma.schedule.findUnique({ where: { id }, select: { id: true, shift: true, course: { select: { id: true, name: true, studentCount: true, period: { select: { id: true, name: true, startDate: true, endDate: true } }, level: { select: { id: true, name: true, order: true, career: { select: { id: true, name: true } } } } } } } });
    if (!schedule) throw new NotFoundException("Horario no encontrado");
    this.assertCareer(schedule.course.level.career.id, actor); return schedule;
  }
  private assertCareer(careerId: string, actor: AuthenticatedUser) { if (actor.role === UserRole.COORDINATOR && !actor.careerIds.includes(careerId)) throw new ForbiddenException("No puedes administrar esta carrera"); }
  private async ensureNightModules(tx: Prisma.TransactionClient, scheduleId: string, periodStart: Date) {
    for (let moduleNumber = 1; moduleNumber <= 6; moduleNumber++) {
      const startDate = new Date(periodStart); startDate.setUTCDate(startDate.getUTCDate() + (moduleNumber - 1) * 21);
      const endDate = new Date(startDate); endDate.setUTCDate(endDate.getUTCDate() + 18);
      await tx.scheduleModule.upsert({ where: { scheduleId_moduleNumber: { scheduleId, moduleNumber } }, update: {}, create: { scheduleId, moduleNumber, startDate, endDate } });
    }
  }
  private time(value: string) { return new Date(`1970-01-01T${value}:00.000Z`); }
  private date(value: string) { return new Date(`${value}T00:00:00.000Z`); }
  private timeString(value: Date) { return value.toISOString().slice(11, 16); }
  private async blockData(context: Awaited<ReturnType<SchedulesService["blockContext"]>>, input: SaveBlockDto) {
    const module = context.schedule.shift === Shift.NIGHT && input.moduleNumber ? await this.prisma.scheduleModule.findUnique({ where: { scheduleId_moduleNumber: { scheduleId: context.schedule.id, moduleNumber: input.moduleNumber } } }) : null;
    return { subjectId: input.subjectId, professorId: input.professorId, spaceId: input.spaceId ?? null, dayOfWeek: input.dayOfWeek, startTime: this.time(input.startTime), endTime: this.time(input.endTime), moduleNumber: input.moduleNumber, validFrom: module?.startDate ?? null, validUntil: module?.endDate ?? null, notes: null };
  }
}
