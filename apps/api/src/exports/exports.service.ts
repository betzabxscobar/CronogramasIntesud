import { BadRequestException, Injectable, Logger, NotFoundException } from "@nestjs/common";
import { DayOfWeek, RecordStatus, ScheduleStatus, Shift } from "@prisma/client";
import { Workbook } from "exceljs";
import { professionalTitlePrefix } from "../professors/professional-title";
import { PrismaService } from "../prisma/prisma.service";
import { writeDaySheet, writeInicioSheet, writeNightSheet, type ExportModel } from "./excel-layout";

/**
 * Genera el libro de horarios a partir de los datos del sistema.
 *
 * Solo se exporta lo que está publicado. Un horario en borrador puede tener
 * huecos y cruces, y repartirlo es peor que no repartirlo: por eso el servicio
 * exige que exista una versión publicada.
 */
@Injectable()
export class ExportsService {
  private readonly logger = new Logger(ExportsService.name);

  constructor(private readonly prisma: PrismaService) {}

  async buildWorkbook(periodId: string, careerId: string): Promise<{ buffer: Buffer; fileName: string }> {
    const period = await this.prisma.academicPeriod.findUnique({
      where: { id: periodId },
      select: { id: true, name: true }
    });
    if (!period) throw new NotFoundException("El periodo académico indicado no existe.");

    const career = await this.prisma.career.findUnique({
      where: { id: careerId },
      include: {
        levels: { where: { status: RecordStatus.ACTIVE }, orderBy: { order: "asc" } },
        subjects: { where: { status: RecordStatus.ACTIVE }, include: { level: true } },
        professors: {
          where: { status: RecordStatus.ACTIVE, professor: { status: RecordStatus.ACTIVE } },
          include: { professor: true },
          orderBy: { professor: { lastNames: "asc" } }
        },
        allowedSpaces: { include: { space: true } },
        coordinators: { include: { user: true } }
      }
    });
    if (!career) throw new NotFoundException("La carrera indicada no existe.");

    const schedules = await this.prisma.schedule.findMany({
      where: { course: { periodId, level: { careerId } } },
      include: {
        course: { include: { level: true } },
        modules: { orderBy: { moduleNumber: "asc" } },
        versions: {
          where: { status: ScheduleStatus.PUBLISHED },
          orderBy: { version: "desc" },
          take: 1,
          include: {
            blocks: {
              include: { subject: true, professor: true, space: true },
              orderBy: [{ dayOfWeek: "asc" }, { startTime: "asc" }]
            }
          }
        }
      }
    });

    const publicadas = schedules.filter((s) => s.versions.length > 0);
    if (!publicadas.length) {
      throw new BadRequestException(
        "No hay ningún horario publicado para esta carrera en este periodo. Publica al menos un horario antes de exportar."
      );
    }
    const sinPublicar = schedules.filter((s) => s.versions.length === 0).map((s) => s.course.level.name);
    if (sinPublicar.length) {
      this.logger.warn(
        `La exportación de ${career.name} omitió los horarios sin publicar: ${sinPublicar.join(", ")}.`
      );
    }

    const model = await this.buildModel(period.name, career, publicadas);

    const workbook = new Workbook();
    workbook.creator = "CRONO-INTESUD";
    workbook.created = new Date();
    writeInicioSheet(workbook.addWorksheet("Inicio"), model);
    writeDaySheet(workbook.addWorksheet("HorariosClasesDiurno"), model);
    writeNightSheet(workbook.addWorksheet("HorariosClasesNocturno"), model);

    const buffer = Buffer.from(await workbook.xlsx.writeBuffer());
    const nombre = `Horarios ${shortCareer(career.name)} ${period.name}.xlsx`
      .replace(/\s+/g, " ")
      .trim();
    return { buffer, fileName: nombre };
  }

  private async buildModel(
    periodName: string,
    career: {
      name: string;
      levels: Array<{ id: string; order: number; name: string }>;
      subjects: Array<{ name: string; code: string | null; weeklyHours: number | null; levelId: string | null; level: { order: number } | null }>;
      professors: Array<{ professor: { firstNames: string; lastNames: string; professionalTitle: string | null } }>;
      allowedSpaces: Array<{ space: { name: string } }>;
      coordinators: Array<{ user: { firstNames: string; lastNames: string } }>;
    },
    schedules: Array<{
      shift: Shift;
      course: { level: { id: string; order: number; name: string } };
      modules: Array<{ moduleNumber: number; startDate: Date; endDate: Date }>;
      versions: Array<{
        blocks: Array<{
          dayOfWeek: DayOfWeek;
          startTime: Date;
          endTime: Date;
          moduleNumber: number | null;
          subject: { name: string };
          professor: {
            firstNames: string;
            lastNames: string;
            professionalTitle: string | null;
            scheduleColor: string;
          };
          space: { name: string } | null;
        }>;
      }>;
    }>
  ): Promise<ExportModel> {
    const levelOrderById = new Map(career.levels.map((l) => [l.id, l.order]));
    const nombreDocente = (person: { firstNames: string; lastNames: string; professionalTitle: string | null }) =>
      [professionalTitlePrefix(person.professionalTitle), person.lastNames, person.firstNames]
        .filter(Boolean)
        .join(" ");

    const bloques = (shift: Shift) =>
      schedules
        .filter((s) => s.shift === shift)
        .flatMap((s) =>
          (s.versions[0]?.blocks ?? []).map((block) => ({
            levelOrder: levelOrderById.get(s.course.level.id) ?? null,
            dayOfWeek: shift === Shift.NIGHT ? null : dayNumber(block.dayOfWeek),
            startTime: clockOf(block.startTime),
            endTime: clockOf(block.endTime),
            moduleNumber: block.moduleNumber,
            subject: block.subject.name,
            // El color sale del docente: el mismo que se ve en la plataforma.
            color: block.professor.scheduleColor,
            professor: nombreDocente(block.professor),
            space: block.space?.name ?? null
          }))
        );

    // Catálogos de todas las carreras activas, igual que el libro compartido.
    const carreras = await this.prisma.career.findMany({
      where: { status: RecordStatus.ACTIVE },
      orderBy: { name: "asc" },
      include: {
        levels: { select: { id: true, order: true } },
        subjects: { where: { status: RecordStatus.ACTIVE } }
      }
    });

    const catalogs = carreras.map((otra) => ({
      careerName: otra.name,
      subjects: otra.subjects.map((s) => ({
        name: s.name,
        code: s.code,
        levelOrder: otra.levels.find((l) => l.id === s.levelId)?.order ?? null,
        weeklyHours: s.weeklyHours
      }))
    }));

    const coordinator = career.coordinators[0]
      ? [career.coordinators[0].user.firstNames, career.coordinators[0].user.lastNames].join(" ")
      : null;

    return {
      careerName: career.name,
      periodName,
      coordinator,
      // El sistema no guarda quién revisó o aprobó: se deja el espacio en
      // blanco para que se complete al imprimir.
      signatures: [
        { label: "Revisado por:", name: null },
        { label: "Aprobado por:", name: null },
        { label: "Recursos Humanos:", name: null }
      ],
      levels: career.levels.map((l) => ({ order: l.order, name: l.name })),
      subjects: career.subjects.map((s) => ({
        name: s.name,
        code: s.code,
        levelOrder: s.level?.order ?? null,
        weeklyHours: s.weeklyHours
      })),
      catalogs,
      professors: career.professors.map((p) => nombreDocente(p.professor)),
      spaces: career.allowedSpaces.map((s) => s.space.name).sort((a, b) => a.localeCompare(b, "es")),
      dayBlocks: bloques(Shift.DAY),
      nightBlocks: bloques(Shift.NIGHT),
      nightModules: schedules.find((s) => s.shift === Shift.NIGHT)?.modules ?? []
    };
  }
}

function clockOf(value: Date): string {
  return value.toISOString().slice(11, 16);
}

/** El enum DayOfWeek es de texto y empieza en domingo; el libro va de lunes a viernes. */
function dayNumber(day: DayOfWeek): number {
  const orden: Record<DayOfWeek, number> = {
    [DayOfWeek.SUNDAY]: 7,
    [DayOfWeek.MONDAY]: 1,
    [DayOfWeek.TUESDAY]: 2,
    [DayOfWeek.WEDNESDAY]: 3,
    [DayOfWeek.THURSDAY]: 4,
    [DayOfWeek.FRIDAY]: 5,
    [DayOfWeek.SATURDAY]: 6
  };
  return orden[day] ?? 1;
}

/** "Desarrollo de Software" -> "DS", como en el nombre del archivo original. */
function shortCareer(name: string): string {
  const words = name.split(/\s+/).filter(Boolean);
  if (words.length === 1) return words[0]!.slice(0, 12).toUpperCase();
  return words.map((w) => w[0]!).join("").slice(0, 4).toUpperCase();
}