import {
  ConflictException,
  Injectable,
  NotFoundException
} from "@nestjs/common";
import {
  Prisma,
  RecordStatus,
  UserRole
} from "@prisma/client";
import { PrismaService } from "../prisma/prisma.service";
import type { CreateCoordinatorDto } from "./dto/create-coordinator.dto";
import type { ListCoordinatorsDto } from "./dto/list-coordinators.dto";
import type { UpdateCoordinatorDto } from "./dto/update-coordinator.dto";

const coordinatorSelect = {
  id: true,
  nationalId: true,
  firstNames: true,
  lastNames: true,
  email: true,
  status: true,
  lastLoginAt: true,
  createdAt: true,
  updatedAt: true,
  careers: {
    select: {
      career: { select: { id: true, name: true, status: true } }
    },
    orderBy: { career: { name: "asc" as const } }
  }
} satisfies Prisma.UserSelect;

@Injectable()
export class CoordinatorsService {
  constructor(private readonly prisma: PrismaService) {}

  async list(query: ListCoordinatorsDto) {
    const search = query.search?.trim();
    const where: Prisma.UserWhereInput = {
      role: UserRole.COORDINATOR,
      status: query.status,
      ...(search
        ? {
            OR: [
              { nationalId: { contains: search, mode: "insensitive" } },
              { firstNames: { contains: search, mode: "insensitive" } },
              { lastNames: { contains: search, mode: "insensitive" } },
              { email: { contains: search, mode: "insensitive" } }
            ]
          }
        : {})
    };
    const skip = (query.page - 1) * query.pageSize;
    const [items, total] = await this.prisma.$transaction([
      this.prisma.user.findMany({
        where,
        select: coordinatorSelect,
        orderBy: [{ status: "asc" }, { lastNames: "asc" }, { firstNames: "asc" }],
        skip,
        take: query.pageSize
      }),
      this.prisma.user.count({ where })
    ]);

    return {
      items: items.map((coordinator) => this.toResponse(coordinator)),
      pagination: {
        page: query.page,
        pageSize: query.pageSize,
        total,
        totalPages: Math.max(1, Math.ceil(total / query.pageSize))
      }
    };
  }

  async listCareers() {
    return this.prisma.career.findMany({
      where: { status: RecordStatus.ACTIVE },
      select: { id: true, name: true },
      orderBy: { name: "asc" }
    });
  }

  async create(input: CreateCoordinatorDto, actorId: string) {
    await this.assertCareers(input.careerIds);
    const normalized = this.normalize(input);
    try {
      const coordinator = await this.prisma.$transaction(async (tx) => {
        const created = await tx.user.create({
          data: {
            ...normalized,
            role: UserRole.COORDINATOR,
            careers: {
              create: input.careerIds.map((careerId) => ({ careerId }))
            }
          },
          select: coordinatorSelect
        });
        await tx.auditLog.create({
          data: {
            userId: actorId,
            action: "COORDINATOR_CREATED",
            entityType: "User",
            entityId: created.id,
            newData: {
              nationalId: created.nationalId,
              email: created.email,
              careerIds: input.careerIds
            }
          }
        });
        return created;
      });
      return this.toResponse(coordinator);
    } catch (error) {
      this.handleUniqueConflict(error);
      throw error;
    }
  }

  async update(id: string, input: UpdateCoordinatorDto, actorId: string) {
    const current = await this.findCoordinator(id);
    if (input.careerIds) await this.assertCareers(input.careerIds);
    const data = this.normalizePartial(input);

    try {
      const coordinator = await this.prisma.$transaction(async (tx) => {
        if (input.careerIds) {
          await tx.coordinatorCareer.deleteMany({ where: { userId: id } });
        }
        const updated = await tx.user.update({
          where: { id },
          data: {
            ...data,
            careers: input.careerIds
              ? {
                  create: input.careerIds.map((careerId) => ({ careerId }))
                }
              : undefined
          },
          select: coordinatorSelect
        });
        await tx.auditLog.create({
          data: {
            userId: actorId,
            action: "COORDINATOR_UPDATED",
            entityType: "User",
            entityId: id,
            previousData: {
              nationalId: current.nationalId,
              email: current.email,
              careerIds: current.careers.map(({ careerId }) => careerId)
            },
            newData: {
              nationalId: updated.nationalId,
              email: updated.email,
              careerIds: updated.careers.map(({ career }) => career.id)
            }
          }
        });
        return updated;
      });
      return this.toResponse(coordinator);
    } catch (error) {
      this.handleUniqueConflict(error);
      throw error;
    }
  }

  async updateStatus(id: string, status: RecordStatus, actorId: string) {
    const current = await this.findCoordinator(id);
    if (current.status === status) return this.findResponse(id);

    const updated = await this.prisma.$transaction(async (tx) => {
      const coordinator = await tx.user.update({
        where: { id },
        data: { status },
        select: coordinatorSelect
      });
      if (status === RecordStatus.INACTIVE) {
        await tx.userSession.updateMany({
          where: { userId: id, revokedAt: null },
          data: { revokedAt: new Date() }
        });
        await tx.loginCode.updateMany({
          where: { userId: id, consumedAt: null },
          data: { consumedAt: new Date() }
        });
      }
      await tx.auditLog.create({
        data: {
          userId: actorId,
          action:
            status === RecordStatus.ACTIVE
              ? "COORDINATOR_ACTIVATED"
              : "COORDINATOR_DEACTIVATED",
          entityType: "User",
          entityId: id,
          previousData: { status: current.status },
          newData: { status }
        }
      });
      return coordinator;
    });
    return this.toResponse(updated);
  }

  async remove(id: string, actorId: string) {
    const coordinator = await this.prisma.user.findFirst({
      where: { id, role: UserRole.COORDINATOR },
      select: {
        id: true,
        nationalId: true,
        firstNames: true,
        lastNames: true,
        email: true,
        _count: {
          select: {
            createdVersions: true,
            modifiedVersions: true,
            publishedVersions: true,
            reservations: true,
            imports: true
          }
        }
      }
    });
    if (!coordinator) throw new NotFoundException("Coordinador no encontrado");

    const academicActivity = Object.values(coordinator._count).reduce(
      (total, count) => total + count,
      0
    );
    if (academicActivity > 0) {
      throw new ConflictException(
        "No se puede eliminar porque el coordinador ya tiene actividad académica. Puedes desactivarlo."
      );
    }

    await this.prisma.$transaction(async (tx) => {
      await tx.coordinatorCareer.deleteMany({ where: { userId: id } });
      await tx.auditLog.create({
        data: {
          userId: actorId,
          action: "COORDINATOR_DELETED",
          entityType: "User",
          entityId: id,
          previousData: {
            nationalId: coordinator.nationalId,
            firstNames: coordinator.firstNames,
            lastNames: coordinator.lastNames,
            email: coordinator.email
          }
        }
      });
      await tx.user.delete({ where: { id } });
    });
  }

  private findResponse(id: string) {
    return this.prisma.user
      .findUniqueOrThrow({ where: { id }, select: coordinatorSelect })
      .then((coordinator) => this.toResponse(coordinator));
  }

  private async findCoordinator(id: string) {
    const coordinator = await this.prisma.user.findFirst({
      where: { id, role: UserRole.COORDINATOR },
      include: { careers: { select: { careerId: true } } }
    });
    if (!coordinator) throw new NotFoundException("Coordinador no encontrado");
    return coordinator;
  }

  private async assertCareers(careerIds: string[]) {
    if (!careerIds.length) return;
    const count = await this.prisma.career.count({
      where: { id: { in: careerIds }, status: RecordStatus.ACTIVE }
    });
    if (count !== careerIds.length) {
      throw new ConflictException("Una o más carreras no están disponibles");
    }
  }

  private normalize(input: CreateCoordinatorDto) {
    return {
      nationalId: input.nationalId.trim(),
      firstNames: input.firstNames.trim(),
      lastNames: input.lastNames.trim(),
      email: input.email.trim().toLowerCase()
    };
  }

  private normalizePartial(input: UpdateCoordinatorDto) {
    return {
      nationalId: input.nationalId?.trim(),
      firstNames: input.firstNames?.trim(),
      lastNames: input.lastNames?.trim(),
      email: input.email?.trim().toLowerCase()
    };
  }

  private handleUniqueConflict(error: unknown): never | void {
    if (
      error instanceof Prisma.PrismaClientKnownRequestError &&
      error.code === "P2002"
    ) {
      const rawTarget = error.meta?.target;
      const target = Array.isArray(rawTarget)
        ? rawTarget.filter((value): value is string => typeof value === "string").join(",")
        : typeof rawTarget === "string"
          ? rawTarget
          : "";
      throw new ConflictException(
        target.includes("email")
          ? "El correo ya está registrado"
          : "La cédula ya está registrada"
      );
    }
  }

  private toResponse(coordinator: Prisma.UserGetPayload<{
    select: typeof coordinatorSelect;
  }>) {
    return {
      ...coordinator,
      careers: coordinator.careers.map(({ career }) => career)
    };
  }
}
