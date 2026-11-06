import {
  ConflictException,
  Injectable,
  NotFoundException
} from "@nestjs/common";
import { Prisma, RecordStatus } from "@prisma/client";
import { PrismaService } from "../prisma/prisma.service";
import type { CreateCareerDto } from "./dto/create-career.dto";
import type { UpdateCareerDto } from "./dto/update-career.dto";

@Injectable()
export class CareersService {
  constructor(private readonly prisma: PrismaService) {}

  list() {
    return this.prisma.career.findMany({
      select: {
        id: true,
        name: true,
        slug: true,
        status: true,
        _count: {
          select: { coordinators: true, professors: true }
        }
      },
      orderBy: [{ status: "asc" }, { name: "asc" }]
    });
  }

  async create(input: CreateCareerDto, actorId: string) {
    const name = input.name.trim().replace(/\s+/g, " ");
    const slug = this.slugify(name);
    if (!slug) throw new ConflictException("El nombre de la carrera no es válido");

    try {
      return await this.prisma.$transaction(async (tx) => {
        const career = await tx.career.create({
          data: { name, slug, status: RecordStatus.ACTIVE },
          select: { id: true, name: true, slug: true, status: true }
        });
        await tx.auditLog.create({
          data: {
            userId: actorId,
            action: "CAREER_CREATED",
            entityType: "Career",
            entityId: career.id,
            newData: { name: career.name, slug: career.slug }
          }
        });
        return career;
      });
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === "P2002"
      ) {
        throw new ConflictException("La carrera ya está registrada");
      }
      throw error;
    }
  }

  async update(id: string, input: UpdateCareerDto, actorId: string) {
    const current = await this.findCareer(id);
    if (!input.name) return current;
    const name = input.name.trim().replace(/\s+/g, " ");
    const slug = this.slugify(name);
    if (!slug) throw new ConflictException("El nombre de la carrera no es válido");

    try {
      return await this.prisma.$transaction(async (tx) => {
        const career = await tx.career.update({
          where: { id },
          data: { name, slug },
          select: {
            id: true,
            name: true,
            slug: true,
            status: true,
            _count: { select: { coordinators: true, professors: true } }
          }
        });
        await tx.auditLog.create({
          data: {
            userId: actorId,
            action: "CAREER_UPDATED",
            entityType: "Career",
            entityId: id,
            previousData: { name: current.name, slug: current.slug },
            newData: { name: career.name, slug: career.slug }
          }
        });
        return career;
      });
    } catch (error) {
      if (
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === "P2002"
      ) {
        throw new ConflictException("La carrera ya está registrada");
      }
      throw error;
    }
  }

  async updateStatus(id: string, status: RecordStatus, actorId: string) {
    const current = await this.findCareer(id);
    if (current.status === status) return current;

    return this.prisma.$transaction(async (tx) => {
      const career = await tx.career.update({
        where: { id },
        data: { status },
        select: {
          id: true,
          name: true,
          slug: true,
          status: true,
          _count: { select: { coordinators: true, professors: true } }
        }
      });
      await tx.auditLog.create({
        data: {
          userId: actorId,
          action:
            status === RecordStatus.ACTIVE
              ? "CAREER_ACTIVATED"
              : "CAREER_DEACTIVATED",
          entityType: "Career",
          entityId: id,
          previousData: { status: current.status },
          newData: { status }
        }
      });
      return career;
    });
  }

  async remove(id: string, actorId: string) {
    const career = await this.prisma.career.findUnique({
      where: { id },
      select: {
        id: true,
        name: true,
        slug: true,
        _count: {
          select: {
            coordinators: true,
            professors: true,
            levels: true,
            subjects: true,
            allowedSpaces: true,
            reservations: true,
            imports: true
          }
        }
      }
    });
    if (!career) throw new NotFoundException("Carrera no encontrada");

    const relatedRecords = Object.values(career._count).reduce(
      (total, count) => total + count,
      0
    );
    if (relatedRecords > 0) {
      throw new ConflictException(
        "No se puede eliminar porque la carrera ya tiene información vinculada"
      );
    }

    await this.prisma.$transaction([
      this.prisma.auditLog.create({
        data: {
          userId: actorId,
          action: "CAREER_DELETED",
          entityType: "Career",
          entityId: career.id,
          previousData: { name: career.name, slug: career.slug }
        }
      }),
      this.prisma.career.delete({ where: { id } })
    ]);
  }

  private async findCareer(id: string) {
    const career = await this.prisma.career.findUnique({
      where: { id },
      select: {
        id: true,
        name: true,
        slug: true,
        status: true,
        _count: { select: { coordinators: true, professors: true } }
      }
    });
    if (!career) throw new NotFoundException("Carrera no encontrada");
    return career;
  }

  private slugify(value: string) {
    return value
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "-")
      .replace(/^-+|-+$/g, "");
  }
}
