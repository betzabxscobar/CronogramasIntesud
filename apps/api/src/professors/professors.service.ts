import {
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException
} from "@nestjs/common";
import {
  Prisma,
  RecordStatus,
  UserRole
} from "@prisma/client";
import type { AuthenticatedUser } from "../auth/auth.types";
import { PrismaService } from "../prisma/prisma.service";
import type { CreateProfessorDto } from "./dto/create-professor.dto";
import type { ListProfessorsDto } from "./dto/list-professors.dto";
import type { UpdateProfessorDto } from "./dto/update-professor.dto";
import {
  expandProfessionalTitle,
  professionalTitlePrefix
} from "./professional-title";

const professorSelect = {
  id: true,
  nationalId: true,
  firstNames: true,
  lastNames: true,
  professionalTitle: true,
  scheduleColor: true,
  publicProfileEnabled: true,
  status: true,
  createdAt: true,
  updatedAt: true,
  careers: {
    where: { status: RecordStatus.ACTIVE },
    select: { career: { select: { id: true, name: true, status: true } } },
    orderBy: { career: { name: "asc" as const } }
  }
} satisfies Prisma.ProfessorSelect;

@Injectable()
export class ProfessorsService {
  constructor(private readonly prisma: PrismaService) {}

  async list(query: ListProfessorsDto, actor: AuthenticatedUser) {
    const allowedCareerIds =
      actor.role === UserRole.ADMIN ? undefined : actor.careerIds;
    if (
      query.careerId &&
      allowedCareerIds &&
      !allowedCareerIds.includes(query.careerId)
    ) {
      throw new ForbiddenException("No tiene acceso a esta carrera");
    }

    const search = query.search?.trim();
    const where: Prisma.ProfessorWhereInput = {
      status: query.status,
      careers: {
        some: {
          status: RecordStatus.ACTIVE,
          careerId: query.careerId
            ? query.careerId
            : allowedCareerIds
              ? { in: allowedCareerIds }
              : undefined
        }
      },
      ...(search
        ? {
            OR: [
              { nationalId: { contains: search, mode: "insensitive" } },
              { firstNames: { contains: search, mode: "insensitive" } },
              { lastNames: { contains: search, mode: "insensitive" } },
              {
                professionalTitle: {
                  contains: search,
                  mode: "insensitive"
                }
              }
            ]
          }
        : {})
    };
    const skip = (query.page - 1) * query.pageSize;
    const [items, total] = await this.prisma.$transaction([
      this.prisma.professor.findMany({
        where,
        select: professorSelect,
        orderBy: [{ status: "asc" }, { lastNames: "asc" }, { firstNames: "asc" }],
        skip,
        take: query.pageSize
      }),
      this.prisma.professor.count({ where })
    ]);
    return {
      items: items.map((professor) => this.toResponse(professor)),
      pagination: {
        page: query.page,
        pageSize: query.pageSize,
        total,
        totalPages: Math.max(1, Math.ceil(total / query.pageSize))
      }
    };
  }

  listCareers(actor: AuthenticatedUser) {
    return this.prisma.career.findMany({
      where: {
        status: RecordStatus.ACTIVE,
        id:
          actor.role === UserRole.ADMIN
            ? undefined
            : { in: actor.careerIds }
      },
      select: { id: true, name: true },
      orderBy: { name: "asc" }
    });
  }

  async create(input: CreateProfessorDto, actor: AuthenticatedUser) {
    await this.assertCareerScope(input.careerIds, actor);
    const normalized = this.normalize(input);
    try {
      const professor = await this.prisma.$transaction(async (tx) => {
        const created = await tx.professor.create({
          data: {
            ...normalized,
            careers: {
              create: input.careerIds.map((careerId) => ({ careerId }))
            }
          },
          select: professorSelect
        });
        await tx.auditLog.create({
          data: {
            userId: actor.id,
            action: "PROFESSOR_CREATED",
            entityType: "Professor",
            entityId: created.id,
            newData: {
              nationalId: created.nationalId,
              firstNames: created.firstNames,
              lastNames: created.lastNames,
              careerIds: input.careerIds
            }
          }
        });
        return created;
      });
      return this.toResponse(professor);
    } catch (error) {
      this.handleUniqueConflict(error);
      throw error;
    }
  }

  async update(
    id: string,
    input: UpdateProfessorDto,
    actor: AuthenticatedUser
  ) {
    const current = await this.findScoped(id, actor);
    if (input.careerIds) await this.assertCareerScope(input.careerIds, actor);
    const normalized = this.normalizePartial(input);

    try {
      const professor = await this.prisma.$transaction(async (tx) => {
        if (input.careerIds) {
          if (actor.role === UserRole.ADMIN) {
            await tx.professorCareer.deleteMany({ where: { professorId: id } });
          } else {
            await tx.professorCareer.deleteMany({
              where: { professorId: id, careerId: { in: actor.careerIds } }
            });
          }
        }
        const updated = await tx.professor.update({
          where: { id },
          data: {
            ...normalized,
            careers: input.careerIds
              ? {
                  create: input.careerIds.map((careerId) => ({ careerId }))
                }
              : undefined
          },
          select: professorSelect
        });
        await tx.auditLog.create({
          data: {
            userId: actor.id,
            action: "PROFESSOR_UPDATED",
            entityType: "Professor",
            entityId: id,
            previousData: {
              nationalId: current.nationalId,
              firstNames: current.firstNames,
              lastNames: current.lastNames
            },
            newData: {
              nationalId: updated.nationalId,
              firstNames: updated.firstNames,
              lastNames: updated.lastNames,
              careerIds: updated.careers.map(({ career }) => career.id)
            }
          }
        });
        return updated;
      });
      return this.toResponse(professor);
    } catch (error) {
      this.handleUniqueConflict(error);
      throw error;
    }
  }

  async updateStatus(
    id: string,
    status: RecordStatus,
    actor: AuthenticatedUser
  ) {
    const current = await this.findScoped(id, actor);
    if (current.status === status) return this.findResponse(id);
    const updated = await this.prisma.$transaction(async (tx) => {
      const professor = await tx.professor.update({
        where: { id },
        data: { status },
        select: professorSelect
      });
      await tx.auditLog.create({
        data: {
          userId: actor.id,
          action:
            status === RecordStatus.ACTIVE
              ? "PROFESSOR_ACTIVATED"
              : "PROFESSOR_DEACTIVATED",
          entityType: "Professor",
          entityId: id,
          previousData: { status: current.status },
          newData: { status }
        }
      });
      return professor;
    });
    return this.toResponse(updated);
  }

  async remove(id: string, actorId: string) {
    const professor = await this.prisma.professor.findUnique({
      where: { id },
      select: {
        id: true,
        nationalId: true,
        firstNames: true,
        lastNames: true,
        professionalTitle: true,
        photoAssetId: true,
        _count: { select: { scheduleBlocks: true } }
      }
    });
    if (!professor) throw new NotFoundException("Profesor no encontrado");

    if (professor._count.scheduleBlocks > 0) {
      throw new ConflictException(
        "No se puede eliminar porque el profesor ya tiene horarios asignados. Puedes desactivarlo."
      );
    }

    await this.prisma.$transaction(async (tx) => {
      await tx.professorCareer.deleteMany({ where: { professorId: id } });
      await tx.auditLog.create({
        data: {
          userId: actorId,
          action: "PROFESSOR_DELETED",
          entityType: "Professor",
          entityId: id,
          previousData: {
            nationalId: professor.nationalId,
            firstNames: professor.firstNames,
            lastNames: professor.lastNames,
            professionalTitle: professor.professionalTitle
          }
        }
      });
      await tx.professor.delete({ where: { id } });
      if (professor.photoAssetId) {
        await tx.fileAsset.delete({ where: { id: professor.photoAssetId } });
      }
    });
  }

  async listPublic(search?: string, careerId?: string) {
    const term = search?.trim();
    const professors = await this.prisma.professor.findMany({
      where: {
        status: RecordStatus.ACTIVE,
        publicProfileEnabled: true,
        careers: {
          some: {
            status: RecordStatus.ACTIVE,
            career: { status: RecordStatus.ACTIVE },
            careerId
          }
        },
        ...(term
          ? {
              OR: [
                { firstNames: { contains: term, mode: "insensitive" } },
                { lastNames: { contains: term, mode: "insensitive" } },
                {
                  professionalTitle: {
                    contains: term,
                    mode: "insensitive"
                  }
                }
              ]
            }
          : {})
      },
      select: {
        id: true,
        firstNames: true,
        lastNames: true,
        professionalTitle: true,
        careers: {
          where: { status: RecordStatus.ACTIVE },
          select: { career: { select: { id: true, name: true } } }
        }
      },
      orderBy: [{ lastNames: "asc" }, { firstNames: "asc" }]
    });
    return professors.map((professor) => this.toPublicResponse(professor));
  }

  async publicDetail(id: string) {
    const professor = await this.prisma.professor.findFirst({
      where: {
        id,
        status: RecordStatus.ACTIVE,
        publicProfileEnabled: true
      },
      select: {
        id: true,
        firstNames: true,
        lastNames: true,
        professionalTitle: true,
        careers: {
          where: {
            status: RecordStatus.ACTIVE,
            career: { status: RecordStatus.ACTIVE }
          },
          select: { career: { select: { id: true, name: true } } }
        }
      }
    });
    if (!professor) throw new NotFoundException("Profesor no encontrado");
    return this.toPublicResponse(professor);
  }

  private async findScoped(id: string, actor: AuthenticatedUser) {
    const professor = await this.prisma.professor.findFirst({
      where: {
        id,
        careers:
          actor.role === UserRole.ADMIN
            ? undefined
            : {
                some: {
                  status: RecordStatus.ACTIVE,
                  careerId: { in: actor.careerIds }
                }
              }
      },
      include: {
        careers: { select: { careerId: true } }
      }
    });
    if (!professor) throw new NotFoundException("Profesor no encontrado");
    return professor;
  }

  private findResponse(id: string) {
    return this.prisma.professor
      .findUniqueOrThrow({ where: { id }, select: professorSelect })
      .then((professor) => this.toResponse(professor));
  }

  private async assertCareerScope(
    careerIds: string[],
    actor: AuthenticatedUser
  ) {
    if (
      actor.role === UserRole.COORDINATOR &&
      careerIds.some((careerId) => !actor.careerIds.includes(careerId))
    ) {
      throw new ForbiddenException(
        "No puede asignar profesores a carreras no autorizadas"
      );
    }
    const count = await this.prisma.career.count({
      where: {
        id: { in: careerIds },
        status: RecordStatus.ACTIVE
      }
    });
    if (count !== careerIds.length) {
      throw new ConflictException("Una o más carreras no están disponibles");
    }
  }

  private normalize(input: CreateProfessorDto) {
    return {
      nationalId: input.nationalId?.trim() || null,
      firstNames: input.firstNames.trim(),
      lastNames: input.lastNames.trim(),
      professionalTitle: expandProfessionalTitle(input.professionalTitle),
      scheduleColor: input.scheduleColor?.toUpperCase() ?? "#A9CBE4",
      publicProfileEnabled: input.publicProfileEnabled ?? true
    };
  }

  private normalizePartial(input: UpdateProfessorDto) {
    return {
      nationalId:
        input.nationalId === undefined ? undefined : input.nationalId.trim() || null,
      firstNames: input.firstNames?.trim(),
      lastNames: input.lastNames?.trim(),
      professionalTitle:
        input.professionalTitle === undefined
          ? undefined
          : expandProfessionalTitle(input.professionalTitle),
      scheduleColor: input.scheduleColor?.toUpperCase(),
      publicProfileEnabled: input.publicProfileEnabled
    };
  }

  private handleUniqueConflict(error: unknown): never | void {
    if (
      error instanceof Prisma.PrismaClientKnownRequestError &&
      error.code === "P2002"
    ) {
      throw new ConflictException("La cédula del profesor ya está registrada");
    }
  }

  private toResponse(professor: Prisma.ProfessorGetPayload<{
    select: typeof professorSelect;
  }>) {
    return {
      ...professor,
      professionalTitle: expandProfessionalTitle(
        professor.professionalTitle
      ),
      professionalTitleAbbreviation: professionalTitlePrefix(
        professor.professionalTitle
      ),
      careers: professor.careers.map(({ career }) => career)
    };
  }

  private toPublicResponse(professor: {
    id: string;
    firstNames: string;
    lastNames: string;
    professionalTitle: string | null;
    careers: Array<{ career: { id: string; name: string } }>;
  }) {
    return {
      id: professor.id,
      firstNames: professor.firstNames,
      lastNames: professor.lastNames,
      professionalTitle: expandProfessionalTitle(
        professor.professionalTitle
      ),
      professionalTitleAbbreviation: professionalTitlePrefix(
        professor.professionalTitle
      ),
      careers: professor.careers.map(({ career }) => career)
    };
  }
}
