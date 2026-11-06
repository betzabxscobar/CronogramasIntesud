import { ConflictException, ForbiddenException, Injectable, NotFoundException } from "@nestjs/common";
import { AcademicPeriodStatus, Prisma, RecordStatus, UserRole } from "@prisma/client";
import type { AuthenticatedUser } from "../auth/auth.types";
import { PrismaService } from "../prisma/prisma.service";
import type { CreatePeriodDto } from "./dto/create-period.dto";
import type { UpdateCourseDto } from "./dto/update-course.dto";
import type { UpdatePeriodDto } from "./dto/update-period.dto";

@Injectable()
export class AcademicPeriodsService {
  constructor(private readonly prisma: PrismaService) {}

  list(actor: AuthenticatedUser) {
    return this.prisma.academicPeriod.findMany({
      select: {
        id: true, name: true, startDate: true, endDate: true, status: true, createdAt: true,
        _count: { select: { courses: { where: this.courseAccess(actor) } } }
      },
      orderBy: [{ status: "asc" }, { startDate: "desc" }]
    });
  }

  async detail(id: string, actor: AuthenticatedUser) {
    const period = await this.prisma.academicPeriod.findUnique({
      where: { id },
      select: {
        id: true, name: true, startDate: true, endDate: true, status: true,
        courses: {
          where: this.courseAccess(actor),
          orderBy: [{ level: { career: { name: "asc" } } }, { level: { order: "asc" } }],
          select: {
            id: true, name: true, studentCount: true, status: true,
            level: { select: { id: true, name: true, order: true, career: { select: { id: true, name: true } } } },
            _count: { select: { schedules: true } }
          }
        }
      }
    });
    if (!period) throw new NotFoundException("Periodo académico no encontrado");
    return { ...period, shifts: ["DAY", "NIGHT"] };
  }

  async create(input: CreatePeriodDto, actorId: string) {
    const dates = this.validateDates(input.startDate, input.endDate);
    try {
      return await this.prisma.$transaction(async (tx) => {
        const period = await tx.academicPeriod.create({ data: { name: this.clean(input.name), ...dates } });
        await this.createMissingCourses(tx, period.id);
        await tx.auditLog.create({ data: { userId: actorId, action: "ACADEMIC_PERIOD_CREATED", entityType: "AcademicPeriod", entityId: period.id, newData: { name: period.name, startDate: input.startDate, endDate: input.endDate } } });
        return period;
      });
    } catch (error) { this.handleConflict(error); throw error; }
  }

  async update(id: string, input: UpdatePeriodDto, actorId: string) {
    const current = await this.findPeriod(id);
    const startDate = input.startDate ?? current.startDate.toISOString().slice(0, 10);
    const endDate = input.endDate ?? current.endDate.toISOString().slice(0, 10);
    const dates = this.validateDates(startDate, endDate);
    try {
      return await this.prisma.$transaction(async (tx) => {
        const period = await tx.academicPeriod.update({ where: { id }, data: { name: input.name ? this.clean(input.name) : undefined, ...dates } });
        await tx.auditLog.create({ data: { userId: actorId, action: "ACADEMIC_PERIOD_UPDATED", entityType: "AcademicPeriod", entityId: id, previousData: { name: current.name, startDate: current.startDate, endDate: current.endDate }, newData: { name: period.name, startDate: period.startDate, endDate: period.endDate } } });
        return period;
      });
    } catch (error) { this.handleConflict(error); throw error; }
  }

  async updateStatus(id: string, status: AcademicPeriodStatus, actorId: string) {
    const current = await this.findPeriod(id);
    return this.prisma.$transaction(async (tx) => {
      if (status === AcademicPeriodStatus.ACTIVE) {
        await tx.academicPeriod.updateMany({ where: { status: AcademicPeriodStatus.ACTIVE, id: { not: id } }, data: { status: AcademicPeriodStatus.CLOSED } });
        await this.createMissingCourses(tx, id);
      }
      const period = await tx.academicPeriod.update({ where: { id }, data: { status } });
      await tx.auditLog.create({ data: { userId: actorId, action: "ACADEMIC_PERIOD_STATUS_CHANGED", entityType: "AcademicPeriod", entityId: id, previousData: { status: current.status }, newData: { status } } });
      return period;
    });
  }

  async syncCourses(id: string, actorId: string) {
    await this.findPeriod(id);
    const created = await this.prisma.$transaction(async (tx) => {
      const count = await this.createMissingCourses(tx, id);
      await tx.auditLog.create({ data: { userId: actorId, action: "ACADEMIC_COURSES_SYNCHRONIZED", entityType: "AcademicPeriod", entityId: id, newData: { created: count } } });
      return count;
    });
    return { created };
  }

  async updateCourse(id: string, input: UpdateCourseDto, actor: AuthenticatedUser) {
    const current = await this.prisma.academicCourse.findUnique({ where: { id }, select: { id: true, studentCount: true, level: { select: { careerId: true } } } });
    if (!current) throw new NotFoundException("Curso no encontrado");
    if (actor.role === UserRole.COORDINATOR && !actor.careerIds.includes(current.level.careerId)) throw new ForbiddenException("No puedes administrar este curso");
    return this.prisma.$transaction(async (tx) => {
      const course = await tx.academicCourse.update({ where: { id }, data: { studentCount: input.studentCount } });
      await tx.auditLog.create({ data: { userId: actor.id, action: "ACADEMIC_COURSE_UPDATED", entityType: "AcademicCourse", entityId: id, previousData: { studentCount: current.studentCount }, newData: { studentCount: course.studentCount } } });
      return course;
    });
  }

  async removeCourse(id: string, actorId: string) {
    const course = await this.prisma.academicCourse.findUnique({
      where: { id },
      select: {
        id: true, name: true, studentCount: true,
        period: { select: { name: true } },
        level: { select: { name: true, career: { select: { name: true } } } },
        _count: { select: { schedules: true } }
      }
    });
    if (!course) throw new NotFoundException("Curso no encontrado");
    if (course._count.schedules > 0) throw new ConflictException("No se puede eliminar porque el curso ya tiene horarios vinculados");
    await this.prisma.$transaction([
      this.prisma.auditLog.create({ data: { userId: actorId, action: "ACADEMIC_COURSE_DELETED", entityType: "AcademicCourse", entityId: id, previousData: { name: course.name, studentCount: course.studentCount, period: course.period.name, level: course.level.name, career: course.level.career.name } } }),
      this.prisma.academicCourse.delete({ where: { id } })
    ]);
  }

  private courseAccess(actor: AuthenticatedUser): Prisma.AcademicCourseWhereInput | undefined {
    return actor.role === UserRole.ADMIN ? undefined : { level: { careerId: { in: actor.careerIds } } };
  }

  private async createMissingCourses(tx: Prisma.TransactionClient, periodId: string) {
    const levels = await tx.level.findMany({ where: { status: RecordStatus.ACTIVE }, select: { id: true } });
    let created = 0;
    for (const level of levels) {
      const exists = await tx.academicCourse.count({ where: { periodId, levelId: level.id } });
      if (!exists) {
        await tx.academicCourse.create({ data: { periodId, levelId: level.id, name: "Curso único" } });
        created++;
      }
    }
    return created;
  }

  private async findPeriod(id: string) {
    const value = await this.prisma.academicPeriod.findUnique({ where: { id } });
    if (!value) throw new NotFoundException("Periodo académico no encontrado");
    return value;
  }

  private validateDates(start: string, end: string) {
    const startDate = new Date(`${start}T00:00:00.000Z`);
    const endDate = new Date(`${end}T00:00:00.000Z`);
    if (endDate <= startDate) throw new ConflictException("La fecha de finalización debe ser posterior a la fecha de inicio");
    return { startDate, endDate };
  }

  private clean(value: string) { return value.trim().replace(/\s+/g, " "); }
  private handleConflict(error: unknown): never | void {
    if (error instanceof Prisma.PrismaClientKnownRequestError && error.code === "P2002") throw new ConflictException("Ya existe un periodo académico con ese nombre");
  }
}
