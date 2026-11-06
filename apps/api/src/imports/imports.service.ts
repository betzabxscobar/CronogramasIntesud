import {
  BadRequestException,
  Injectable,
  Logger,
  NotFoundException
} from "@nestjs/common";
import {
  DayOfWeek,
  ImportRowStatus,
  ImportStatus,
  Prisma,
  RecordStatus,
  ScheduleStatus,
  Shift,
  SpaceStatus,
  SpaceType
} from "@prisma/client";
import * as ExcelJS from "exceljs";
import type { AuthenticatedUser } from "../auth/auth.types";
import { PrismaService } from "../prisma/prisma.service";
import {
  detectConflicts,
  resolveProfessor,
  resolveSpace,
  resolveSubject,
  type Catalog,
  type MasterRow
} from "./excel/block-matcher";
import { parseDaySheet, type DraftBlock } from "./excel/day-schedule";
import { parseInicioSheet, type InicioParse } from "./excel/inicio-layout";
import { parseNightSheet } from "./excel/night-schedule";
import { asText, comparable } from "./excel/normalize";

const SHEET_INICIO = "Inicio";
const SHEET_DIURNO = "HorariosClasesDiurno";
const SHEET_NOCTURNO = "HorariosClasesNocturno";
const MAX_FILE_BYTES = 15 * 1024 * 1024;

type EntityKind = "level" | "subject" | "professor" | "space" | "block";

/** Marca los registros que esta misma importación va a crear. */
const PENDING_ID = "__pendiente__";

/** Clave con la que se compara un docente entre el libro y la base. */
function teacherKey(person: { firstNames: string; lastNames: string }) {
  return comparable(person.lastNames + " " + person.firstNames);
}

/** Clave con la que se compara un aula entre el libro y la base. */
function roomKey(room: { name: string }) {
  return comparable(room.name);
}

/** Hora de un campo Time de Postgres, en "HH:MM:SS". */
function clockOf(value: Date | string): string {
  if (typeof value === "string") return value;
  return value.toISOString().slice(11, 19);
}

/** "HH:MM:SS" -> Date para un campo @db.Time, igual que hace schedules.service. */
function toTimeOfDay(value: string): Date {
  return new Date(`1970-01-01T${value.replace(/:\d{2}$/, "")}:00.000Z`);
}

interface DraftRow {
  entity: EntityKind;
  sheetName: string;
  rowNumber: number;
  payload: Record<string, unknown>;
  messages: string[];
  status: ImportRowStatus;
}

export interface ImportPreview {
  job: {
    id: string;
    fileName: string;
    status: ImportStatus;
    totalRows: number;
    validRows: number;
    warningRows: number;
    errorRows: number;
  };
  sheets: string[];
  activeCareerInBook: string | null;
  catalogBlocks: Array<{ column: number; career: string; subjects: number }>;
  counts: {
    subjects: number;
    professors: number;
    spaces: number;
    levels: number;
    blocks: number;
  };
  /** Resumen de los bloques, presente solo si se pidieron. */
  blocks?: BlockPreview;
  /** Filas que la persona debe revisar antes de confirmar. */
  needsReview: Array<{
    sheetName: string;
    rowNumber: number;
    entity: EntityKind;
    status: ImportRowStatus;
    label: string;
    messages: string[];
  }>;
  skipped: Array<{ cell: string; value: string; reason: string }>;
  notes: string[];
  canCommit: boolean;
}

/** Cómo se va a guardar cada bloque de horario. */
export interface BlockPreview {
  periodName: string;
  /** Turnos que se van a leer. */
  shifts: Array<{
    shift: "DAY" | "NIGHT";
    sheetName: string;
    blocks: number;
    byLevel: Record<string, number>;
    ignoredProfessorBlocks: number;
    /** Celdas del libro con la plantilla sin diligenciar. */
    placeholders: number;
    notes: string[];
    /** Módulos sin fechas en el horario nocturno ya existente. */
    missingModuleDates: number;
  }>;
  conflicts: number;
}

@Injectable()
export class ImportsService {
  private readonly logger = new Logger(ImportsService.name);

  constructor(private readonly prisma: PrismaService) {}

  /**
   * Lee el libro, valida y deja el resultado en una ImportJob. No escribe nada
   * en las tablas de dominio: la vista previa es siempre de solo lectura.
   */
  async preview(
    file: { buffer: Buffer; originalname: string },
    actor: AuthenticatedUser,
    careerId: string,
    options: {
      catalogColumn?: number;
      includeSchedule?: boolean;
      periodId?: string;
      shift?: "ALL" | "DAY" | "NIGHT";
    } = {}
  ): Promise<ImportPreview> {
    if (!file?.buffer?.length) throw new BadRequestException("No se recibió ningún archivo.");
    if (file.buffer.length > MAX_FILE_BYTES) {
      throw new BadRequestException(
        `El archivo supera el máximo permitido (${Math.round(MAX_FILE_BYTES / 1024 / 1024)} MB).`
      );
    }

    const career = await this.prisma.career.findUnique({
      where: { id: careerId },
      select: { id: true, name: true }
    });
    if (!career) throw new NotFoundException("La carrera indicada no existe.");

    let period: { id: string; name: string } | null = null;
    if (options.includeSchedule) {
      if (!options.periodId) {
        throw new BadRequestException(
          "Para importar bloques de horario hay que elegir el periodo académico al que pertenecen."
        );
      }
      period = await this.prisma.academicPeriod.findUnique({
        where: { id: options.periodId },
        select: { id: true, name: true }
      });
      if (!period) throw new NotFoundException("El periodo académico indicado no existe.");
    }

    let parse: InicioParse;
    let sheets: string[] = [];
    let dayParse: ReturnType<typeof parseDaySheet> | null = null;
    let nightParse: ReturnType<typeof parseNightSheet> | null = null;

    try {
      const workbook = new ExcelJS.Workbook();
      await workbook.xlsx.load(file.buffer as unknown as ArrayBuffer);
      sheets = workbook.worksheets.map((sheet) => sheet.name);
      const sheet = workbook.getWorksheet(SHEET_INICIO);
      if (!sheet) {
        throw new BadRequestException(
          `El libro no contiene la hoja "${SHEET_INICIO}". Hojas encontradas: ${sheets.join(", ") || "ninguna"}.`
        );
      }
      parse = parseInicioSheet(sheet, options.catalogColumn);

      if (options.includeSchedule) {
        const wanted = options.shift ?? "ALL";
        if (wanted === "ALL" || wanted === "DAY") {
          const daySheet = workbook.getWorksheet(SHEET_DIURNO);
          if (!daySheet) {
            throw new BadRequestException(
              `El libro no contiene la hoja "${SHEET_DIURNO}", que hace falta para los bloques de horario.`
            );
          }
          dayParse = parseDaySheet(daySheet);
        }
        if (wanted === "ALL" || wanted === "NIGHT") {
          const nightSheet = workbook.getWorksheet(SHEET_NOCTURNO);
          if (!nightSheet) {
            if (wanted === "NIGHT") {
              throw new BadRequestException(
                `El libro no contiene la hoja "${SHEET_NOCTURNO}" del horario nocturno.`
              );
            }
          } else {
            nightParse = parseNightSheet(nightSheet);
          }
        }
      }
    } catch (error: unknown) {
      if (error instanceof BadRequestException) throw error;
      this.logger.error("No se pudo leer el archivo", error instanceof Error ? error.stack : undefined);
      throw new BadRequestException(
        "No se pudo leer el archivo. Verifica que sea un .xlsx válido y no esté protegido con contraseña."
      );
    }

    const drafts = await this.buildDrafts(parse, careerId);
    let blockSummary: BlockPreview | undefined;

    if (options.includeSchedule && (dayParse || nightParse)) {
      const blocks = this.collectBlocks(dayParse, nightParse);
      const built = await this.buildBlockDrafts(
        blocks,
        careerId,
        drafts,
        dayParse,
        nightParse,
        period?.name ?? ""
      );
      drafts.push(...built.drafts);
      blockSummary = built.summary;
    }

    const job = await this.persistJob(file.originalname, actor.id, careerId, drafts, parse, sheets);

    const validRows = drafts.filter((row) => row.status === ImportRowStatus.VALID).length;
    const warningRows = drafts.filter((row) => row.status === ImportRowStatus.WARNING).length;
    const errorRows = drafts.filter((row) => row.status === ImportRowStatus.ERROR).length;
    const status =
      errorRows > 0 || validRows + warningRows === 0
        ? ImportStatus.INVALID
        : ImportStatus.READY;

    const updated = await this.prisma.importJob.update({
      where: { id: job.id },
      data: {
        status,
        totalRows: drafts.length,
        validRows,
        warningRows,
        errorRows,
        ...(options.periodId ? { periodId: options.periodId } : {})
      }
    });

    const skipped = [
      ...parse.skipped,
      ...(dayParse?.skipped ?? []),
      ...(nightParse?.skipped ?? [])
    ];

    return {
      job: {
        id: updated.id,
        fileName: updated.fileName,
        status: updated.status,
        totalRows: updated.totalRows,
        validRows: updated.validRows,
        warningRows: updated.warningRows,
        errorRows: updated.errorRows
      },
      sheets,
      activeCareerInBook: parse.activeCareer,
      catalogBlocks: parse.catalogBlocks,
      counts: {
        subjects: parse.subjects.length,
        professors: parse.professors.length,
        spaces: parse.spaces.length,
        levels: parse.levels.length,
        blocks: blockSummary?.shifts.reduce((sum, s) => sum + s.blocks, 0) ?? 0
      },
      blocks: blockSummary,
      needsReview: drafts
        .filter((row) => row.status !== ImportRowStatus.VALID)
        .slice(0, 200)
        .map((row) => ({
          sheetName: row.sheetName,
          rowNumber: row.rowNumber,
          entity: row.entity,
          status: row.status,
          label: asText(row.payload.name) || asText(row.payload.label),
          messages: row.messages
        })),
      skipped: skipped.slice(0, 200),
      notes: [
        ...parse.notes,
        ...(dayParse?.notes ?? []),
        ...(nightParse?.notes ?? [])
      ],
      canCommit: status === ImportStatus.READY
    };
  }

  /** Aplica las filas válidas y con advertencias de la ImportJob indicada. */
  // ------------------------------------------------------------------
// Bloques de horario
// ------------------------------------------------------------------

/** Inicio del periodo, base para calcular las fechas de los módulos. */
  private async periodStart(periodId: string): Promise<Date> {
    const period = await this.prisma.academicPeriod.findUnique({
      where: { id: periodId },
      select: { startDate: true }
    });
    if (!period) throw new NotFoundException("El periodo académico indicado no existe.");
    return period.startDate;
  }

  /**
   * Crea los seis módulos del horario nocturno si no existen, con la misma
   * distribución de fechas que usa la aplicación al crear un horario a mano.
   */
  private async ensureNightModules(scheduleId: string, periodStart: Date) {
    for (let moduleNumber = 1; moduleNumber <= 6; moduleNumber++) {
      const startDate = new Date(periodStart);
      startDate.setUTCDate(startDate.getUTCDate() + (moduleNumber - 1) * 21);
      const endDate = new Date(startDate);
      endDate.setUTCDate(endDate.getUTCDate() + 18);
      await this.prisma.scheduleModule.upsert({
        where: { scheduleId_moduleNumber: { scheduleId, moduleNumber } },
        update: {},
        create: { scheduleId, moduleNumber, startDate, endDate }
      });
    }
  }

  /**
 * Crea los bloques de horario.
 *
 * Los bloques cuelgan de una versión de horario, que a su vez pertenece a un
 * curso (periodo + nivel) y a un turno. Si no existen se crean; si ya hay una
 * versión en borrador se reutiliza, para que subir el mismo archivo dos veces
 * no genere horarios paralelos.
 *
 * No se inserta un bloque si ya existe otro igual en esa versión, así repetir la
 * importación no duplica nada.
 */
private async applyBlocks(
  job: { id: string; careerId: string; periodId: string | null },
  rows: Array<{ rawData: unknown }>,
  actor: AuthenticatedUser
): Promise<number> {
  const periodId = job.periodId;
  if (!periodId) {
    throw new BadRequestException("La importación no tiene periodo académico asociado.");
  }

  const [subjects, professors, spaces, levels] = await Promise.all([
    this.prisma.subject.findMany({
      where: { careerId: job.careerId },
      select: { id: true, name: true }
    }),
    this.prisma.professor.findMany({
      select: { id: true, firstNames: true, lastNames: true }
    }),
    this.prisma.academicSpace.findMany({
      select: { id: true, name: true, code: true }
    }),
    this.prisma.level.findMany({
      where: { careerId: job.careerId },
      select: { id: true, order: true, name: true }
    })
  ]);

  const catalog: Catalog = {
    subjects: subjects.map((s) => ({ key: comparable(s.name), value: s })),
    professors: professors.map((p) => ({
      key: comparable(p.lastNames + " " + p.firstNames),
      alternateKey: comparable(p.firstNames + " " + p.lastNames),
      value: p
    })),
    spaces: spaces.map((s) => ({ key: comparable(s.name), value: s }))
  };
  const levelByOrder = new Map(levels.map((l) => [l.order, l]));

  const DAY_ENUM: Record<number, DayOfWeek> = {
    1: DayOfWeek.MONDAY,
    2: DayOfWeek.TUESDAY,
    3: DayOfWeek.WEDNESDAY,
    4: DayOfWeek.THURSDAY,
    5: DayOfWeek.FRIDAY,
    6: DayOfWeek.SATURDAY
  };

  // Se agrupa por turno y nivel porque cada grupo es un horario distinto.
  const groups = new Map<string, typeof rows>();
  for (const row of rows) {
    const data = row.rawData as { entity?: string } & Record<string, unknown>;
    const shift = asText(data.shift) === "NIGHT" ? "NIGHT" : "DAY";
    const order = Number(data.levelOrder);
    const key = `${shift}:${order}`;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(row);
  }

  let inserted = 0;

  for (const [key, group] of groups) {
    const [shiftRaw = "", orderRaw = ""] = key.split(":");
    const shift: Shift = shiftRaw === "NIGHT" ? Shift.NIGHT : Shift.DAY;
    const order = Number(orderRaw);
    const level = levelByOrder.get(order);
    if (!level) {
      this.logger.warn(
        `No se encontraron bloques del nivel ${order}: la carrera no tiene ese nivel.`
      );
      continue;
    }

    const course = await this.prisma.academicCourse.upsert({
      where: { periodId_levelId: { periodId, levelId: level.id } },
      update: {},
      create: { periodId, levelId: level.id, name: `${level.name} ${new Date().getFullYear()}` }
    });

    const schedule = await this.prisma.schedule.upsert({
      where: { courseId_shift: { courseId: course.id, shift } },
      update: {},
      create: { courseId: course.id, shift }
    });

    // El horario nocturno se lee por módulos y la aplicación los necesita con
    // fecha: sin ellos los bloques importados no tendrían vigencia.
    if (shift === Shift.NIGHT) {
      await this.ensureNightModules(schedule.id, (await this.periodStart(periodId)));
    }
    const modules = shift === Shift.NIGHT
      ? await this.prisma.scheduleModule.findMany({
          where: { scheduleId: schedule.id },
          select: { moduleNumber: true, startDate: true, endDate: true }
        })
      : [];
    const moduleByNumber = new Map(modules.map((m) => [m.moduleNumber, m]));

    // Se reutiliza la última versión en borrador; si no hay, se abre una nueva.
    let version = await this.prisma.scheduleVersion.findFirst({
      where: { scheduleId: schedule.id, status: ScheduleStatus.DRAFT },
      orderBy: { version: "desc" }
    });
    if (!version) {
      const last = await this.prisma.scheduleVersion.findFirst({
        where: { scheduleId: schedule.id },
        orderBy: { version: "desc" },
        select: { version: true }
      });
      version = await this.prisma.scheduleVersion.create({
        data: {
          scheduleId: schedule.id,
          version: (last?.version ?? 0) + 1,
          status: ScheduleStatus.DRAFT,
          reason: "Importado desde el libro de horarios",
          createdById: actor.id,
          modifiedById: actor.id
        }
      });
    }

    const existentes = await this.prisma.scheduleBlock.findMany({
      where: { versionId: version.id },
      select: {
        subjectId: true,
        professorId: true,
        spaceId: true,
        dayOfWeek: true,
        startTime: true,
        endTime: true,
        moduleNumber: true
      }
    });
    const yaEsta = new Set(
      existentes.map(
        (b) =>
          `${b.subjectId}|${b.professorId}|${b.spaceId ?? ""}|${b.dayOfWeek}|${clockOf(b.startTime)}|${clockOf(b.endTime)}|${b.moduleNumber ?? 0}`
      )
    );

    const nuevos = [];
    for (const row of group) {
      const data = row.rawData as Record<string, unknown>;
      const subjectName = asText(data.subjectName);
      if (!subjectName) continue;
      const subject = resolveSubject(subjectName, catalog);
      const professor = resolveProfessor(asText(data.professorName), catalog);
      if (!professor.item || professor.item.id === PENDING_ID) continue;

      let subjectId: string;
      if (subject.item) {
        subjectId = subject.item.id;
      } else {
        // El horario usa una materia que el catálogo no lista. La vista previa
        // ya lo avisó; aquí se crea para que el horario no quede incompleto.
        const created = await this.prisma.subject.create({
          data: { careerId: job.careerId, name: subjectName, status: RecordStatus.ACTIVE },
          select: { id: true }
        });
        subjectId = created.id;
        catalog.subjects.push({ key: comparable(subjectName), value: { id: subjectId, name: subjectName } });
      }


      const spaceRaw = asText(data.spaceName);
      const space = spaceRaw ? resolveSpace(spaceRaw, catalog) : null;
      const spaceId = space?.item && space.item.id !== PENDING_ID ? space.item.id : null;

      const dayOfWeek = DAY_ENUM[Number(data.dayOfWeek)];
      if (!dayOfWeek) continue;

      const startTime = asText(data.startTime);
      const endTime = asText(data.endTime);
      const moduleNumber = data.moduleNumber ? Number(data.moduleNumber) : null;
      const fingerprint = `${subjectId}|${professor.item.id}|${spaceId ?? ""}|${dayOfWeek}|${startTime}|${endTime}|${moduleNumber ?? 0}`;
      if (yaEsta.has(fingerprint)) continue;
      yaEsta.add(fingerprint);

      nuevos.push({
        versionId: version.id,
        subjectId,
        professorId: professor.item.id,
        spaceId,
        dayOfWeek,
        startTime: toTimeOfDay(startTime),
        endTime: toTimeOfDay(endTime),
        moduleNumber,
        validFrom: moduleNumber ? (moduleByNumber.get(moduleNumber)?.startDate ?? null) : null,
        validUntil: moduleNumber ? (moduleByNumber.get(moduleNumber)?.endDate ?? null) : null,
        notes: null
      });
    }

    if (nuevos.length) {
      await this.prisma.scheduleBlock.createMany({ data: nuevos });
      inserted += nuevos.length;
    }
  }

  return inserted;
}

// ------------------------------------------------------------------
// Bloques de horario
// ------------------------------------------------------------------

/** Junta los bloques de las dos hojas y marca de qué turno viene cada uno. */
private collectBlocks(
  dayParse: ReturnType<typeof parseDaySheet> | null,
  nightParse: ReturnType<typeof parseNightSheet> | null
): Array<{ block: DraftBlock; shift: "DAY" | "NIGHT" }> {
  return [
    ...(dayParse?.blocks ?? []).map((block) => ({ block, shift: "DAY" as const })),
    ...(nightParse?.blocks ?? []).map((block) => ({ block, shift: "NIGHT" as const }))
  ];
}

/**
 * Arma el catálogo contra el que se resuelven las referencias de los bloques.
 *
 * Incluye lo que ya está en la base y también lo que esta misma importación va a
 * crear. Sin eso, un docente que aparece en la hoja Inicio pero todavía no está
 * registrado se reportaría como inexistente y el horario no se podría migrar en
 * una sola pasada.
 */
private async buildCatalog(careerId: string, masterDrafts: DraftRow[]): Promise<Catalog> {
  const [subjects, professors, spaces] = await Promise.all([
    this.prisma.subject.findMany({
      where: { careerId },
      select: { id: true, name: true }
    }),
    this.prisma.professor.findMany({
      select: { id: true, firstNames: true, lastNames: true }
    }),
    this.prisma.academicSpace.findMany({
      select: { id: true, name: true, code: true }
    })
  ]);

  const pendingSubjects: Array<MasterRow<{ id: string; name: string }>> = [];
  const pendingProfessors: Array<MasterRow<{ id: string; firstNames: string; lastNames: string }>> = [];
  const pendingSpaces: Array<MasterRow<{ id: string; name: string; code: string }>> = [];

  for (const row of masterDrafts) {
    const data = row.payload as { entity?: EntityKind } & Record<string, unknown>;
    if (row.entity === "subject") {
      const name = asText(data.name);
      if (!name) continue;
      pendingSubjects.push({
        key: comparable(name),
        value: { id: PENDING_ID, name }
      });
    } else if (row.entity === "professor") {
      const firstNames = asText(data.firstNames);
      const lastNames = asText(data.lastNames);
      if (!firstNames || !lastNames) continue;
      pendingProfessors.push({
        key: comparable(lastNames + " " + firstNames),
        alternateKey: comparable(firstNames + " " + lastNames),
        value: { id: PENDING_ID, firstNames, lastNames }
      });
    } else if (row.entity === "space") {
      const name = asText(data.name);
      if (!name) continue;
      // Si el aula ya existe en la base con un nombre más corto, no se agrega
      // como pendiente: así la resolución por prefijo se aplica igual que en la
      // aplicación y la vista previa cuenta la misma historia.
      const yaExiste = spaces.some((item) => {
        const key = comparable(item.name);
        const keyLibro = comparable(name);
        return key === keyLibro || (key.length > 0 && keyLibro.startsWith(key));
      });
      if (yaExiste) continue;
      pendingSpaces.push({
        key: comparable(name),
        value: { id: PENDING_ID, name, code: asText(data.code) }
      });
    }
  }

  return {
    // Primero lo que ya existe, para que una coincidencia exacta gana siempre.
    subjects: [
      ...subjects.map((s) => ({ key: comparable(s.name), value: s })),
      ...pendingSubjects
    ],
    professors: [
      ...professors.map((p) => ({
        key: comparable(p.lastNames + " " + p.firstNames),
        alternateKey: comparable(p.firstNames + " " + p.lastNames),
        value: p
      })),
      ...pendingProfessors
    ],
    spaces: [
      ...spaces.map((s) => ({ key: comparable(s.name), value: s })),
      ...pendingSpaces
    ]
  };
}

/** Valida los bloques y los convierte en filas de la importación. */
private async buildBlockDrafts(
  entries: Array<{ block: DraftBlock; shift: "DAY" | "NIGHT" }>,
  careerId: string,
  masterDrafts: DraftRow[],
  dayParse: ReturnType<typeof parseDaySheet> | null,
  nightParse: ReturnType<typeof parseNightSheet> | null,
  periodName: string
): Promise<{ drafts: DraftRow[]; conflicts: number; summary: BlockPreview }> {
  const catalog = await this.buildCatalog(careerId, masterDrafts);

  // El horario puede usar una materia que el catálogo no lista. Antes de
  // validar se registran como pendientes: si no, esos bloques darían error y
  // bloquearían toda la importación por un detalle del catálogo.
  const pendientesDeHorario: Array<MasterRow<{ id: string; name: string }>> = [];
  for (const entry of entries) {
    const name = entry.block.subjectName;
    const key = comparable(name);
    if (!key) continue;
    if (catalog.subjects.some((row) => row.key === key)) continue;
    if (pendientesDeHorario.some((row) => row.key === key)) continue;
    pendientesDeHorario.push({ key, value: { id: PENDING_ID, name } });
  }
  catalog.subjects.push(...pendientesDeHorario);

  const drafts: DraftRow[] = [];

  // Se detectan choques por turno porque las reglas del horario nocturno son
  // distintas: allí lo que separa dos clases es el módulo, no el día.
  type ConflictList = ReturnType<typeof detectConflicts> extends Map<number, infer T> ? T : never;
  const conflictsByShift = new Map<"DAY" | "NIGHT", Map<number, ConflictList>>();
  const resolvedCache = new Map<string, { professorKey: string | null; spaceKey: string | null }>();

  const resolveFor = (block: DraftBlock) => {
    const key = `${block.professorName}|${block.spaceName}`;
    const cached = resolvedCache.get(key);
    if (cached) return cached;
    const professor = resolveProfessor(block.professorName, catalog);
    const space = resolveSpace(block.spaceName, catalog);
    const value = {
      professorKey: professor.item ? teacherKey(professor.item) : null,
      spaceKey: space.item ? roomKey(space.item) : null
    };
    resolvedCache.set(key, value);
    return value;
  };

  for (const shift of ["DAY", "NIGHT"] as const) {
    const group = entries.filter((entry) => entry.shift === shift);
    if (!group.length) continue;
    conflictsByShift.set(
      shift,
      detectConflicts(
        group.map((entry) => entry.block),
        shift,
        resolveFor
      )
    );
  }

  entries.forEach((entry, index) => {
    const { block, shift } = entry;
    const messages = [...block.notes];
    let status: ImportRowStatus = ImportRowStatus.VALID;

    const subject = resolveSubject(block.subjectName, catalog);
    if (!subject.item) {
      messages.push(subject.detail);
      status = ImportRowStatus.ERROR;
    } else if (subject.item.id === PENDING_ID) {
      // El libro usa una materia que el catálogo no lista. Se crea sola, sin
      // nivel ni código, para no dejar el horario incompleto.
      messages.push(
        `La materia «${block.subjectName}» se usa en el horario pero no está en el catálogo de la carrera: se creará sin nivel para que puedas asignárselo después.`
      );
      status = ImportRowStatus.WARNING;
    }

    const professor = resolveProfessor(block.professorName, catalog);
    if (!professor.item) {
      messages.push(professor.detail);
      status = ImportRowStatus.ERROR;
    } else if (professor.detail) {
      messages.push(professor.detail);
      status = ImportRowStatus.WARNING;
    }

    if (block.spaceName) {
      const space = resolveSpace(block.spaceName, catalog);
      if (!space.item) {
        messages.push(space.detail);
        status = ImportRowStatus.ERROR;
      } else if (space.detail) {
        messages.push(space.detail);
        if (status !== ImportRowStatus.ERROR) status = ImportRowStatus.WARNING;
      }
    }

    if (!block.levelOrder) {
      messages.push("El libro no indica a qué nivel pertenece esta clase.");
      status = ImportRowStatus.ERROR;
    }

    const conflicts = conflictsByShift.get(shift)?.get(index) ?? [];
    for (const conflict of conflicts) {
      messages.push(conflict.message);
      status = ImportRowStatus.ERROR;
    }

    if (messages.length && status === ImportRowStatus.VALID) status = ImportRowStatus.WARNING;

    drafts.push({
      entity: "block",
      sheetName: shift === "DAY" ? SHEET_DIURNO : SHEET_NOCTURNO,
      rowNumber: block.sourceRow,
      payload: {
        entity: "block",
        shift,
        levelOrder: block.levelOrder,
        dayOfWeek: block.dayOfWeek,
        startTime: block.startTime,
        endTime: block.endTime,
        moduleNumber: block.moduleNumber,
        subjectName: block.subjectName,
        professorName: block.professorName,
        spaceName: block.spaceName,
        label: `${block.subjectName} · ${block.startTime.slice(0, 5)}-${block.endTime.slice(0, 5)}`
      },
      messages,
      status
    });
  });

  const conflictCount = [...conflictsByShift.values()].reduce(
    (sum, map) => sum + [...map.values()].reduce((n, list) => n + list.length, 0),
    0
  );

  const shifts: BlockPreview["shifts"] = [];
  for (const shift of ["DAY", "NIGHT"] as const) {
    const group = entries.filter((entry) => entry.shift === shift);
    if (!group.length) continue;
    const byLevel: Record<string, number> = {};
    for (const entry of group) {
      const key = String(entry.block.levelOrder ?? "?");
      byLevel[key] = (byLevel[key] ?? 0) + 1;
    }
    shifts.push({
      shift,
      sheetName: shift === "DAY" ? SHEET_DIURNO : SHEET_NOCTURNO,
      blocks: group.length,
      byLevel,
      ignoredProfessorBlocks: shift === "DAY" ? (dayParse?.professorBlocksIgnored ?? 0) : (nightParse?.professorBlocksIgnored ?? 0),
      placeholders: shift === "NIGHT" ? (nightParse?.placeholderCount ?? 0) : 0,
      notes:
        shift === "DAY"
          ? []
          : [
              "El horario nocturno del libro no tiene días: las clases se aplicaron a lunes-viernes y se repetirán en cada módulo."
            ],
      missingModuleDates: 0
    });
  }

  return {
    drafts,
    conflicts: conflictCount,
    summary: { periodName, shifts, conflicts: conflictCount }
  };
}

async commit(jobId: string, actor: AuthenticatedUser) {
    const job = await this.prisma.importJob.findUnique({
      where: { id: jobId },
      include: { rows: { orderBy: { rowNumber: "asc" } } }
    });
    if (!job) throw new NotFoundException("La importación no existe.");
    if (job.status === ImportStatus.CANCELLED) {
      throw new BadRequestException("Esta importación fue cancelada.");
    }
    if (job.status === ImportStatus.IMPORTED) {
      throw new BadRequestException("Esta importación ya fue aplicada.");
    }
    if (job.status === ImportStatus.INVALID) {
      throw new BadRequestException(
        "La importación tiene errores. Corrígelos en el archivo y vuelve a subirlo."
      );
    }

    const career = await this.prisma.career.findUnique({
      where: { id: job.careerId },
      select: { id: true, name: true }
    });
    if (!career) throw new NotFoundException("La carrera de la importación ya no existe.");

    // Solo se aplican filas válidas o con advertencias: un ERROR nunca se escribe.
    const applicable = job.rows.filter(
      (row) => row.status === ImportRowStatus.VALID || row.status === ImportRowStatus.WARNING
    );

    const applied = { levels: 0, subjects: 0, professors: 0, spaces: 0, blocks: 0 };
    const levelNameByOrder = new Map<number, string>();

    for (const row of applicable) {
      const data = row.rawData as unknown as { entity?: EntityKind } & Record<string, unknown>;
      const entity = data.entity;

      // Los bloques se aplican al final, cuando ya existen las materias, los
      // docentes y las aulas a los que apuntan.
      if (entity === "block") continue;

      if (entity === "level") {
        const order = Number(data.order);
        const levelName = asText(data.name);
        if (!Number.isInteger(order) || !levelName) continue;
        await this.prisma.level.upsert({
          where: { careerId_order: { careerId: job.careerId, order } },
          update: { name: levelName },
          create: { careerId: job.careerId, order, name: levelName }
        });
        levelNameByOrder.set(order, levelName);
        applied.levels++;
        continue;
      }

      if (entity === "subject") {
        const name = asText(data.name);
        if (!name) continue;
        const order = Number(data.levelOrder);
        let levelId: string | null = null;
        if (Number.isInteger(order)) {
          const level = await this.prisma.level.findUnique({
            where: { careerId_order: { careerId: job.careerId, order } },
            select: { id: true }
          });
          levelId = level?.id ?? null;
        }
        const code = asText(data.code) || null;
        const existing = code
          ? await this.prisma.subject.findFirst({
              where: {
                careerId: job.careerId,
                code: { equals: code, mode: "insensitive" }
              },
              select: { id: true }
            })
          : await this.prisma.subject.findFirst({
              where: {
                careerId: job.careerId,
                name: { equals: name, mode: "insensitive" }
              },
              select: { id: true }
            });

        if (existing) {
          await this.prisma.subject.update({
            where: { id: existing.id },
            data: {
              name,
              levelId,
              code,
              status: RecordStatus.ACTIVE,
              // null significa que el libro no traía la columna, no que valga 0.
              ...(data.weeklyHours === null || data.weeklyHours === undefined
                ? {}
                : { weeklyHours: Number(data.weeklyHours) })
            }
          });
        } else {
          await this.prisma.subject.create({
            data: {
              careerId: job.careerId,
              name,
              code,
              levelId,
              status: RecordStatus.ACTIVE,
              ...(data.weeklyHours === null || data.weeklyHours === undefined
                ? {}
                : { weeklyHours: Number(data.weeklyHours) })
            }
          });
        }
        applied.subjects++;
        continue;
      }

      if (entity === "professor") {
        const firstNames = asText(data.firstNames);
        const lastNames = asText(data.lastNames);
        if (!firstNames || !lastNames) continue;
        const professionalTitle = asText(data.professionalTitle) || null;
        const existing = await this.prisma.professor.findFirst({
          where: {
            firstNames: { equals: firstNames, mode: "insensitive" },
            lastNames: { equals: lastNames, mode: "insensitive" }
          },
          select: { id: true }
        });
        const professor = existing
          ? await this.prisma.professor.update({
              where: { id: existing.id },
              data: { ...(professionalTitle ? { professionalTitle } : {}), status: RecordStatus.ACTIVE },
              select: { id: true }
            })
          : await this.prisma.professor.create({
              data: {
                firstNames,
                lastNames,
                professionalTitle,
                status: RecordStatus.ACTIVE,
                publicProfileEnabled: true
              },
              select: { id: true }
            });
        await this.prisma.professorCareer.upsert({
          where: { professorId_careerId: { professorId: professor.id, careerId: job.careerId } },
          update: { status: RecordStatus.ACTIVE },
          create: { professorId: professor.id, careerId: job.careerId }
        });
        applied.professors++;
        continue;
      }

      if (entity === "space") {
        const name = asText(data.name);
        const code = asText(data.code);
        if (!name || !code) continue;
        const type = data.type === SpaceType.LABORATORY ? SpaceType.LABORATORY : SpaceType.CLASSROOM;

        // Si la vista previa detectó que el aula ya existe con otro nombre, se
        // enlaza esa: crear una copia dejaría dos aulas para el mismo lugar.
        const reuseName = asText(data.reuseName);
        let space: { id: string } | null = null;
        if (reuseName) {
          space = await this.prisma.academicSpace.findFirst({
            where: { name: { equals: reuseName, mode: "insensitive" } },
            select: { id: true }
          });
          if (space) {
            await this.prisma.academicSpace.update({
              where: { id: space.id },
              data: { type, status: SpaceStatus.AVAILABLE },
              select: { id: true }
            });
          }
        }

        if (!space) {
          const existing = await this.prisma.academicSpace.findUnique({
            where: { code },
            select: { id: true }
          });
          space = existing
            ? await this.prisma.academicSpace.update({
                where: { id: existing.id },
                data: { name, type, status: SpaceStatus.AVAILABLE },
                select: { id: true }
              })
            : await this.prisma.academicSpace.create({
                data: { name, code, type, status: SpaceStatus.AVAILABLE },
                select: { id: true }
              });
        }

        await this.prisma.spaceCareer.upsert({
          where: { spaceId_careerId: { spaceId: space.id, careerId: job.careerId } },
          update: {},
          create: { spaceId: space.id, careerId: job.careerId }
        });
        applied.spaces++;
      }
    }

    const blockRows = applicable.filter(
      (row) => (row.rawData as unknown as { entity?: string }).entity === "block"
    );
    if (blockRows.length) {
      if (!job.periodId) {
        throw new BadRequestException(
          "Esta importación trae bloques de horario pero no tiene periodo académico asociado. Vuelve a analizarla indicando el periodo."
        );
      }
      applied.blocks = await this.applyBlocks(job, blockRows, actor);
    }

    await this.prisma.importJob.update({
      where: { id: job.id },
      data: { status: ImportStatus.IMPORTED }
    });

    await this.prisma.auditLog.create({
      data: {
        userId: actor.id,
        action: "IMPORT_APPLIED",
        entityType: "ImportJob",
        entityId: job.id,
        newData: { careerId: job.careerId, career: career.name, fileName: job.fileName, applied }
      }
    });

    this.logger.log(
      `Importación ${job.id} aplicada en ${career.name}: ${JSON.stringify(applied)}`
    );

    return { jobId: job.id, career: career.name, applied };
  }

  async findJob(jobId: string) {
    const job = await this.prisma.importJob.findUnique({
      where: { id: jobId },
      include: { rows: { orderBy: { rowNumber: "asc" }, take: 500 } }
    });
    if (!job) throw new NotFoundException("La importación no existe.");
    return job;
  }

  async cancel(jobId: string, actor: AuthenticatedUser) {
    const job = await this.prisma.importJob.findUnique({ where: { id: jobId } });
    if (!job) throw new NotFoundException("La importación no existe.");
    if (job.status === ImportStatus.IMPORTED) {
      throw new BadRequestException("No se puede cancelar una importación ya aplicada.");
    }
    await this.prisma.importJob.update({
      where: { id: jobId },
      data: { status: ImportStatus.CANCELLED }
    });
    await this.prisma.auditLog.create({
      data: {
        userId: actor.id,
        action: "IMPORT_CANCELLED",
        entityType: "ImportJob",
        entityId: jobId
      }
    });
    return { id: jobId, status: ImportStatus.CANCELLED };
  }

  async list(actor: AuthenticatedUser) {
    return this.prisma.importJob.findMany({
      where: actor.role === "ADMIN" ? {} : { userId: actor.id },
      orderBy: { createdAt: "desc" },
      take: 20,
      select: {
        id: true,
        fileName: true,
        status: true,
        totalRows: true,
        validRows: true,
        warningRows: true,
        errorRows: true,
        createdAt: true,
        career: { select: { id: true, name: true } }
      }
    });
  }

  // ------------------------------------------------------------------

  /** Convierte el resultado del parser en filas con estado y mensajes. */
  private async buildDrafts(parse: InicioParse, careerId: string): Promise<DraftRow[]> {
    const [existingSubjects, existingProfessors, existingSpaces] = await Promise.all([
      this.prisma.subject.findMany({
        where: { careerId },
        select: { code: true, name: true }
      }),
      this.prisma.professor.findMany({ select: { firstNames: true, lastNames: true } }),
      this.prisma.academicSpace.findMany({ select: { code: true, name: true } })
    ]);

    const subjectCodes = new Set(
      existingSubjects.map((s) => comparable(s.code)).filter(Boolean)
    );
    const subjectNames = new Set(existingSubjects.map((s) => comparable(s.name)));
    const professorNames = new Set(
      existingProfessors.map((p) => comparable(`${p.lastNames} ${p.firstNames}`))
    );
    const spaceCodes = new Set(existingSpaces.map((s) => comparable(s.code)));

    const drafts: DraftRow[] = [];

    for (const level of parse.levels) {
      drafts.push({
        entity: "level",
        sheetName: parse.sheetName,
        rowNumber: level.sourceRow,
        payload: { entity: "level", order: level.order, name: level.name },
        messages: [],
        status: ImportRowStatus.VALID
      });
    }

    for (const subject of parse.subjects) {
      const messages = [...subject.notes];
      let status: ImportRowStatus = ImportRowStatus.VALID;

      if (!subject.name) {
        messages.push("La materia no tiene nombre.");
        status = ImportRowStatus.ERROR;
      }
      if (!subject.levelOrder && subject.name) {
        // El nivel es opcional en el modelo, así que no bloquea la importacion:
        // la materia entra sin nivel y se asigna despues desde Estructura.
        messages.push(
          "El libro no indica el nivel de esta materia: se importará sin nivel y podrás asignarlo después."
        );
        status = status === ImportRowStatus.ERROR ? status : ImportRowStatus.WARNING;
      }
      if (subject.code) {
        const key = comparable(subject.code);
        if (subjectCodes.has(key)) messages.push("Ya existe una materia con este código: se actualizará.");
        subjectCodes.add(key);
      } else if (subjectNames.has(comparable(subject.name))) {
        messages.push("Ya existe una materia con este nombre: se actualizará.");
        status = ImportRowStatus.WARNING;
      }
      subjectNames.add(comparable(subject.name));

      // El libro guarda los créditos con decimales (3,3333) porque son la
      // equivalencia en horas. El sistema solo admite horas enteras, así que se
      // redondea y se avisa del redondeo en vez de callarlo.
      let weeklyHours: number | null = null;
      if (typeof subject.credits === "number" && Number.isFinite(subject.credits)) {
        weeklyHours = Math.round(subject.credits);
        if (Math.abs(weeklyHours - subject.credits) > 0.001) {
          messages.push(
            `El libro indica ${subject.credits.toFixed(2).replace(".", ",")} créditos y se guardó como ${weeklyHours} ${weeklyHours === 1 ? "hora" : "horas"}.`
          );
          if (status !== ImportRowStatus.ERROR) status = ImportRowStatus.WARNING;
        }
      }

      if (messages.length && status === ImportRowStatus.VALID) status = ImportRowStatus.WARNING;

      drafts.push({
        entity: "subject",
        sheetName: parse.sheetName,
        rowNumber: subject.sourceRow,
        payload: {
          entity: "subject",
          name: subject.name,
          code: subject.code,
          levelOrder: subject.levelOrder,
          credits: subject.credits,
          weeklyHours
        },
        messages,
        status
      });
    }

    for (const professor of parse.professors) {
      const messages = [...professor.notes];
      const status: ImportRowStatus = messages.length
        ? ImportRowStatus.WARNING
        : ImportRowStatus.VALID;
      const key = comparable(`${professor.lastNames} ${professor.firstNames}`);
      if (professorNames.has(key)) messages.push("Ya existe un docente con ese nombre: se actualizará.");
      professorNames.add(key);

      drafts.push({
        entity: "professor",
        sheetName: parse.sheetName,
        rowNumber: professor.sourceRow,
        payload: {
          entity: "professor",
          firstNames: professor.firstNames,
          lastNames: professor.lastNames,
          professionalTitle: professor.professionalTitle,
          label: `${professor.professionalTitle ? professor.professionalTitle + " " : ""}${professor.lastNames} ${professor.firstNames}`
        },
        messages,
        status
      });
    }

    for (const space of parse.spaces) {
      const messages = [...space.notes];
      let status: ImportRowStatus = ImportRowStatus.VALID;

      // El libro nombra las aulas con un sufijo que la base no tiene
      // ("Aula 3-A Laboratorio 01" frente a "Aula 3-A"). Si ya existe un aula
      // registrada con un nombre más corto, es la misma aula: se reutiliza en
      // lugar de crear una copia con otro nombre y otro código.
      const yaRegistrada = existingSpaces.find((item) => {
        const key = comparable(item.name);
        const keyLibro = comparable(space.name);
        return key === keyLibro || (key.length > 0 && keyLibro.startsWith(key));
      });

      if (yaRegistrada && comparable(yaRegistrada.name) !== comparable(space.name)) {
        messages.push(
          `El libro escribe «${space.name}» y esa aula ya está registrada como «${yaRegistrada.name}» (${yaRegistrada.code}). Se usará la existente y no se creará otra.`
        );
        status = ImportRowStatus.WARNING;
      } else if (spaceCodes.has(comparable(space.code))) {
        messages.push("Ya existe un espacio con ese código: se actualizará.");
        status = ImportRowStatus.WARNING;
      }
      spaceCodes.add(comparable(space.code));

      drafts.push({
        entity: "space",
        sheetName: parse.sheetName,
        rowNumber: space.sourceRow,
        payload: {
          entity: "space",
          name: space.name,
          code: space.code,
          type: space.type,
          // Si el aula ya existe con otro nombre, se enlaza esa en vez de crear.
          reuseName: yaRegistrada ? yaRegistrada.name : null
        },
        messages,
        status
      });
    }

    return drafts;
  }

  private async persistJob(
    fileName: string,
    userId: string,
    careerId: string,
    drafts: DraftRow[],
    parse: InicioParse,
    sheets: string[]
  ) {
    return this.prisma.importJob.create({
      data: {
        userId,
        careerId,
        fileName,
        status: ImportStatus.VALIDATING,
        totalRows: drafts.length,
        rows: {
          create: drafts.map((row) => ({
            sheetName: row.sheetName,
            rowNumber: row.rowNumber,
            status: row.status,
            rawData: row.payload as Prisma.InputJsonValue,
            messages: {
              entity: row.entity,
              messages: row.messages,
              skipped: parse.skipped.length,
              sheets: sheets.join(" | ")
            }
          }))
        }
      },
      select: { id: true, fileName: true, status: true, totalRows: true, validRows: true, warningRows: true, errorRows: true }
    });
  }
}
