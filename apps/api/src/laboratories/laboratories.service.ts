import { ConflictException, Injectable, NotFoundException } from "@nestjs/common";
import { Prisma, RecordStatus, SpaceStatus, SpaceType, UserRole } from "@prisma/client";
import type { AuthenticatedUser } from "../auth/auth.types";
import { PrismaService } from "../prisma/prisma.service";
import type { CreateLaboratoryDto } from "./dto/create-laboratory.dto";
import type { ListLaboratoriesDto } from "./dto/list-laboratories.dto";
import type { UpdateLaboratoryDto } from "./dto/update-laboratory.dto";

const laboratorySelect = {
  id: true,
  name: true,
  code: true,
  location: true,
  type: true,
  peopleCapacity: true,
  studentComputers: true,
  teacherComputers: true,
  hasProjector: true,
  hasDigitalBoard: true,
  status: true,
  createdAt: true,
  updatedAt: true,
  careers: {
    select: { career: { select: { id: true, name: true } } },
    orderBy: { career: { name: "asc" as const } }
  },
  _count: { select: { scheduleBlocks: true, reservations: true, unavailabilities: true } }
} satisfies Prisma.AcademicSpaceSelect;

@Injectable()
export class LaboratoriesService {
  constructor(private readonly prisma: PrismaService) {}

  async list(query: ListLaboratoriesDto, actor: AuthenticatedUser) {
    const term = query.search?.trim();
    const where: Prisma.AcademicSpaceWhereInput = {
      type: query.type,
      status: query.status,
      careers: actor.role === UserRole.ADMIN ? undefined : { some: { careerId: { in: actor.careerIds } } },
      ...(term ? { OR: [
        { name: { contains: term, mode: "insensitive" } },
        { code: { contains: term, mode: "insensitive" } },
        { location: { contains: term, mode: "insensitive" } }
      ] } : {})
    };
    const [items, total] = await this.prisma.$transaction([
      this.prisma.academicSpace.findMany({
        where,
        select: laboratorySelect,
        orderBy: { name: "asc" },
        skip: (query.page - 1) * query.pageSize,
        take: query.pageSize
      }),
      this.prisma.academicSpace.count({ where })
    ]);
    return {
      items: items.map((item) => this.toResponse(item)),
      pagination: { page: query.page, pageSize: query.pageSize, total, totalPages: Math.max(1, Math.ceil(total / query.pageSize)) }
    };
  }

  listCareers(actor: AuthenticatedUser) {
    return this.prisma.career.findMany({
      where: { status: RecordStatus.ACTIVE, id: actor.role === UserRole.ADMIN ? undefined : { in: actor.careerIds } },
      select: { id: true, name: true },
      orderBy: { name: "asc" }
    });
  }

  async create(input: CreateLaboratoryDto, actorId: string) {
    await this.assertCareers(input.careerIds);
    try {
      const laboratory = await this.prisma.$transaction(async (tx) => {
        const created = await tx.academicSpace.create({
          data: { ...this.normalize(input), type: input.type, careers: { create: input.careerIds.map((careerId) => ({ careerId })) } },
          select: laboratorySelect
        });
        await tx.auditLog.create({ data: { userId: actorId, action: "ACADEMIC_SPACE_CREATED", entityType: "AcademicSpace", entityId: created.id, newData: { name: created.name, code: created.code, type: created.type, careerIds: input.careerIds } } });
        return created;
      });
      return this.toResponse(laboratory);
    } catch (error) { this.handleUniqueConflict(error); throw error; }
  }

  async update(id: string, input: UpdateLaboratoryDto, actorId: string) {
    const current = await this.findLaboratory(id);
    if (input.careerIds) await this.assertCareers(input.careerIds);
    try {
      const updated = await this.prisma.$transaction(async (tx) => {
        if (input.careerIds) await tx.spaceCareer.deleteMany({ where: { spaceId: id } });
        const laboratory = await tx.academicSpace.update({
          where: { id },
          data: { ...this.normalizePartial(input), careers: input.careerIds ? { create: input.careerIds.map((careerId) => ({ careerId })) } : undefined },
          select: laboratorySelect
        });
        await tx.auditLog.create({ data: { userId: actorId, action: "ACADEMIC_SPACE_UPDATED", entityType: "AcademicSpace", entityId: id, previousData: { name: current.name, code: current.code, type: current.type }, newData: { name: laboratory.name, code: laboratory.code, type: laboratory.type } } });
        return laboratory;
      });
      return this.toResponse(updated);
    } catch (error) { this.handleUniqueConflict(error); throw error; }
  }

  async updateStatus(id: string, status: SpaceStatus, actorId: string) {
    const current = await this.findLaboratory(id);
    const updated = await this.prisma.$transaction(async (tx) => {
      const laboratory = await tx.academicSpace.update({ where: { id }, data: { status }, select: laboratorySelect });
      await tx.auditLog.create({ data: { userId: actorId, action: "ACADEMIC_SPACE_STATUS_CHANGED", entityType: "AcademicSpace", entityId: id, previousData: { status: current.status }, newData: { status } } });
      return laboratory;
    });
    return this.toResponse(updated);
  }

  async remove(id: string, actorId: string) {
    const laboratory = await this.prisma.academicSpace.findFirst({
      where: { id },
      select: { id: true, name: true, code: true, _count: { select: { scheduleBlocks: true, reservations: true } } }
    });
    if (!laboratory) throw new NotFoundException("Espacio académico no encontrado");
    if (laboratory._count.scheduleBlocks + laboratory._count.reservations > 0) {
      throw new ConflictException("No se puede eliminar porque el espacio tiene horarios o reservas. Puedes marcarlo como inactivo.");
    }
    await this.prisma.$transaction([
      this.prisma.auditLog.create({ data: { userId: actorId, action: "ACADEMIC_SPACE_DELETED", entityType: "AcademicSpace", entityId: id, previousData: { name: laboratory.name, code: laboratory.code } } }),
      this.prisma.academicSpace.delete({ where: { id } })
    ]);
  }

  private findLaboratory(id: string) {
    return this.prisma.academicSpace.findFirst({ where: { id } }).then((value) => {
      if (!value) throw new NotFoundException("Espacio académico no encontrado");
      return value;
    });
  }

  private async assertCareers(careerIds: string[]) {
    const count = await this.prisma.career.count({ where: { id: { in: careerIds }, status: RecordStatus.ACTIVE } });
    if (count !== careerIds.length) throw new ConflictException("Una o más carreras no están disponibles");
  }

  private normalize(input: CreateLaboratoryDto) {
    return {
      name: input.name.trim(), code: input.code?.trim().toUpperCase() || this.internalCode(input.name, input.type), location: input.location?.trim() || null,
      peopleCapacity: input.peopleCapacity ?? null, studentComputers: input.studentComputers ?? 0,
      teacherComputers: input.teacherComputers ?? 0, hasProjector: input.hasProjector ?? false,
      hasDigitalBoard: input.hasDigitalBoard ?? false
    };
  }

  private internalCode(name: string, type: SpaceType) {
    const normalizedName = name
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/[^a-zA-Z0-9]+/g, "-")
      .replace(/^-|-$/g, "")
      .toUpperCase();
    return `${type === SpaceType.CLASSROOM ? "AULA" : "LAB"}-${normalizedName}`.slice(0, 30);
  }

  private normalizePartial(input: UpdateLaboratoryDto) {
    return {
      type: input.type,
      name: input.name?.trim(), code: input.code?.trim().toUpperCase(),
      location: input.location === undefined ? undefined : input.location.trim() || null,
      peopleCapacity: input.peopleCapacity, studentComputers: input.studentComputers,
      teacherComputers: input.teacherComputers, hasProjector: input.hasProjector, hasDigitalBoard: input.hasDigitalBoard
    };
  }

  private handleUniqueConflict(error: unknown): never | void {
    if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === "P2002") {
      throw new ConflictException("Ya existe un espacio académico con ese nombre");
    }
  }

  private toResponse(laboratory: Prisma.AcademicSpaceGetPayload<{ select: typeof laboratorySelect }>) {
    return { ...laboratory, careers: laboratory.careers.map(({ career }) => career) };
  }
}
