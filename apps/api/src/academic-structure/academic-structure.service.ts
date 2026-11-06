import { ConflictException, ForbiddenException, Injectable, NotFoundException } from "@nestjs/common";
import { Prisma, RecordStatus, UserRole } from "@prisma/client";
import type { AuthenticatedUser } from "../auth/auth.types";
import { PrismaService } from "../prisma/prisma.service";
import type { CreateLevelDto } from "./dto/create-level.dto";
import type { CreateSubjectDto } from "./dto/create-subject.dto";
import type { UpdateLevelDto } from "./dto/update-level.dto";
import type { UpdateSubjectDto } from "./dto/update-subject.dto";

@Injectable()
export class AcademicStructureService {
  constructor(private readonly prisma: PrismaService) {}

  listCareers(actor: AuthenticatedUser) {
    return this.prisma.career.findMany({
      where: {
        status: RecordStatus.ACTIVE,
        id: actor.role === UserRole.ADMIN ? undefined : { in: actor.careerIds }
      },
      select: { id: true, name: true },
      orderBy: { name: "asc" }
    });
  }

  async detail(careerId: string, actor: AuthenticatedUser) {
    await this.assertCareerAccess(careerId, actor);
    const career = await this.prisma.career.findUnique({
      where: { id: careerId },
      select: {
        id: true,
        name: true,
        levels: {
          orderBy: { order: "asc" },
          select: {
            id: true,
            name: true,
            order: true,
            status: true,
            subjects: {
              orderBy: { name: "asc" },
              select: {
                id: true,
                name: true,
                code: true,
                weeklyHours: true,
                requiresIndividualComputer: true,
                status: true,
                _count: { select: { scheduleBlocks: true } }
              }
            },
            _count: { select: { subjects: true, courses: true } }
          }
        }
      }
    });
    if (!career) throw new NotFoundException("Carrera no encontrada");
    return career;
  }

  async createLevel(input: CreateLevelDto, actor: AuthenticatedUser) {
    await this.assertCareerAccess(input.careerId, actor);
    try {
      return await this.prisma.$transaction(async (tx) => {
        const level = await tx.level.create({ data: { careerId: input.careerId, name: this.clean(input.name), order: input.order } });
        await tx.auditLog.create({ data: { userId: actor.id, action: "LEVEL_CREATED", entityType: "Level", entityId: level.id, newData: { careerId: level.careerId, name: level.name, order: level.order } } });
        return level;
      });
    } catch (error) { this.handleLevelConflict(error); throw error; }
  }

  async updateLevel(id: string, input: UpdateLevelDto, actor: AuthenticatedUser) {
    const current = await this.findLevel(id);
    if (input.careerId && input.careerId !== current.careerId) throw new ConflictException("No se puede trasladar un nivel a otra carrera");
    const careerId = current.careerId;
    await this.assertCareerAccess(current.careerId, actor);
    await this.assertCareerAccess(careerId, actor);
    try {
      return await this.prisma.$transaction(async (tx) => {
        const level = await tx.level.update({ where: { id }, data: { careerId, name: input.name ? this.clean(input.name) : undefined, order: input.order } });
        await tx.auditLog.create({ data: { userId: actor.id, action: "LEVEL_UPDATED", entityType: "Level", entityId: id, previousData: { careerId: current.careerId, name: current.name, order: current.order }, newData: { careerId: level.careerId, name: level.name, order: level.order } } });
        return level;
      });
    } catch (error) { this.handleLevelConflict(error); throw error; }
  }

  async removeLevel(id: string, actor: AuthenticatedUser) {
    const level = await this.prisma.level.findUnique({ where: { id }, include: { _count: { select: { subjects: true, courses: true } } } });
    if (!level) throw new NotFoundException("Nivel no encontrado");
    await this.assertCareerAccess(level.careerId, actor);
    if (level._count.subjects + level._count.courses > 0) throw new ConflictException("No se puede eliminar un nivel que contiene materias o cursos");
    await this.prisma.$transaction([
      this.prisma.auditLog.create({ data: { userId: actor.id, action: "LEVEL_DELETED", entityType: "Level", entityId: id, previousData: { careerId: level.careerId, name: level.name, order: level.order } } }),
      this.prisma.level.delete({ where: { id } })
    ]);
  }

  async createSubject(input: CreateSubjectDto, actor: AuthenticatedUser) {
    await this.assertCareerAccess(input.careerId, actor);
    await this.assertLevelCareer(input.levelId, input.careerId);
    try {
      return await this.prisma.$transaction(async (tx) => {
        const subject = await tx.subject.create({ data: {
          careerId: input.careerId,
          levelId: input.levelId,
          name: this.clean(input.name),
          code: input.code?.trim().toUpperCase() || null,
          weeklyHours: input.weeklyHours,
          requiresIndividualComputer: input.requiresIndividualComputer ?? false
        } });
        await tx.auditLog.create({ data: { userId: actor.id, action: "SUBJECT_CREATED", entityType: "Subject", entityId: subject.id, newData: { careerId: subject.careerId, levelId: subject.levelId, name: subject.name, code: subject.code } } });
        return subject;
      });
    } catch (error) { this.handleSubjectConflict(error); throw error; }
  }

  async updateSubject(id: string, input: UpdateSubjectDto, actor: AuthenticatedUser) {
    const current = await this.findSubject(id);
    if (input.careerId && input.careerId !== current.careerId) throw new ConflictException("No se puede trasladar una materia a otra carrera");
    const careerId = current.careerId;
    const levelId = input.levelId ?? current.levelId;
    if (!levelId) throw new ConflictException("La materia debe pertenecer a un nivel");
    await this.assertCareerAccess(current.careerId, actor);
    await this.assertCareerAccess(careerId, actor);
    await this.assertLevelCareer(levelId, careerId);
    try {
      return await this.prisma.$transaction(async (tx) => {
        const subject = await tx.subject.update({ where: { id }, data: this.subjectData({ ...input, careerId, levelId }) });
        await tx.auditLog.create({ data: { userId: actor.id, action: "SUBJECT_UPDATED", entityType: "Subject", entityId: id, previousData: { name: current.name, code: current.code, levelId: current.levelId }, newData: { name: subject.name, code: subject.code, levelId: subject.levelId } } });
        return subject;
      });
    } catch (error) { this.handleSubjectConflict(error); throw error; }
  }

  async removeSubject(id: string, actor: AuthenticatedUser) {
    const subject = await this.prisma.subject.findUnique({ where: { id }, include: { _count: { select: { scheduleBlocks: true } } } });
    if (!subject) throw new NotFoundException("Materia no encontrada");
    await this.assertCareerAccess(subject.careerId, actor);
    if (subject._count.scheduleBlocks > 0) throw new ConflictException("No se puede eliminar una materia que ya tiene horarios");
    await this.prisma.$transaction([
      this.prisma.auditLog.create({ data: { userId: actor.id, action: "SUBJECT_DELETED", entityType: "Subject", entityId: id, previousData: { name: subject.name, code: subject.code, levelId: subject.levelId } } }),
      this.prisma.subject.delete({ where: { id } })
    ]);
  }

  private async assertCareerAccess(careerId: string, actor: AuthenticatedUser) {
    if (actor.role === UserRole.COORDINATOR && !actor.careerIds.includes(careerId)) throw new ForbiddenException("No puedes administrar esta carrera");
    const exists = await this.prisma.career.count({ where: { id: careerId, status: RecordStatus.ACTIVE } });
    if (!exists) throw new NotFoundException("Carrera no encontrada o inactiva");
  }

  private async assertLevelCareer(levelId: string, careerId: string) {
    const exists = await this.prisma.level.count({ where: { id: levelId, careerId } });
    if (!exists) throw new ConflictException("El nivel no pertenece a la carrera seleccionada");
  }

  private async findLevel(id: string) {
    const value = await this.prisma.level.findUnique({ where: { id } });
    if (!value) throw new NotFoundException("Nivel no encontrado");
    return value;
  }

  private async findSubject(id: string) {
    const value = await this.prisma.subject.findUnique({ where: { id } });
    if (!value) throw new NotFoundException("Materia no encontrada");
    return value;
  }

  private subjectData(input: Partial<CreateSubjectDto> & Pick<CreateSubjectDto, "careerId" | "levelId">) {
    return {
      careerId: input.careerId,
      levelId: input.levelId,
      name: input.name ? this.clean(input.name) : undefined,
      code: input.code === undefined ? undefined : input.code.trim().toUpperCase() || null,
      weeklyHours: input.weeklyHours,
      requiresIndividualComputer: input.requiresIndividualComputer
    };
  }

  private clean(value: string) { return value.trim().replace(/\s+/g, " "); }

  private handleLevelConflict(error: unknown): never | void {
    if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === "P2002") throw new ConflictException("Ya existe un nivel con ese nombre u orden en la carrera");
  }

  private handleSubjectConflict(error: unknown): never | void {
    if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === "P2002") throw new ConflictException("El código de la materia ya está registrado en esta carrera");
  }
}
