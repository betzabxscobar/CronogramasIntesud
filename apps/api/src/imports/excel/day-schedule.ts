/**
 * Lector de la hoja "HorariosClasesDiurno" del libro "Horarios DS 2026".
 *
 * La hoja repite el mismo horario dos veces: una vez por nivel y otra por
 * docente. Solo se leen los bloques de NIVEL, porque los de docente son la
 * misma informacion vista desde otro lado y leerlos duplicaria los bloques.
 *
 * Cada bloque ocupa siete columnas:
 *
 *   col+0  Horas       (etiqueta combinada sobre las tres filas siguientes)
 *   col+1  Lunes
 *   col+2  Martes
 *   col+3  Miércoles
 *   col+4  Jueves
 *   col+5  Viernes
 *   col+6  (separador)
 *
 * Y cada franja horaria son tres filas: materia, docente y aula. Cuando dos o
 * mas franjas consecutivas del mismo día tienen exactamente la misma materia,
 * docente y aula, son en realidad una sola clase de dos, tres o cuatro horas.
 */
import type { Worksheet } from "exceljs";
import { cellRef, reader } from "./cells";
import { classifyCell, comparable, levelLabelToOrder } from "./normalize";

/** Días de la semana como los nombra el libro. */
const DAY_LABELS: Record<string, number> = {
  LUNES: 1,
  MARTES: 2,
  MIERCOLES: 3,
  JUEVES: 4,
  VIERNES: 5,
  SABADO: 6
};

/** Rango horario "08:00h-09:00h" -> minutos desde medianoche. */
export function parseHourRange(label: string): { start: number; end: number } | null {
  const match = label.match(/(\d{1,2}):(\d{2})\s*h?\s*-\s*(\d{1,2}):(\d{2})\s*h?/i);
  if (!match) return null;
  const start = Number(match[1]) * 60 + Number(match[2]);
  const end = Number(match[3]) * 60 + Number(match[4]);
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) return null;
  return { start, end };
}

/** Minutos -> "HH:MM" para el campo Time de la base. */
export function minutesToClock(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:00`;
}

export interface DraftBlock {
  levelOrder: number | null;
  /** 1 = lunes ... 6 = sabado. El horario nocturno no usa dias. */
  dayOfWeek: number | null;
  startTime: string;
  endTime: string;
  moduleNumber: number | null;
  subjectName: string;
  professorName: string;
  spaceName: string;
  sourceRow: number;
  sourceColumn: number;
  notes: string[];
}

export interface DayParse {
  sheetName: string;
  blocks: DraftBlock[];
  /** Bloques de docente detectados y descartados, para poder informarlo. */
  professorBlocksIgnored: number;
  notes: string[];
  skipped: Array<{ cell: string; value: string; reason: string }>;
}

interface Cell {
  row: number;
  column: number;
  start: number;
  end: number;
  subject: string;
  professor: string;
  space: string;
  notes: string[];
}

/**
 * Una franja son las tres filas (materia, docente, aula) que comparten la misma
 * etiqueta de hora. Como esa etiqueta está combinada, se repite en las tres, y
 * hay que quedarse con el primer grupo.
 */
function readTimeSlots(sheet: Worksheet, labelColumn: number) {
  const read = reader(sheet);
  const raw: Array<{ row: number; label: string }> = [];
  for (let row = 5; row <= read.rows; row++) {
    const label = read.text(row, labelColumn);
    if (/\d{1,2}:\d{2}\s*h?\s*-/.test(label)) raw.push({ row, label });
  }

  const slots: Array<{ row: number; start: number; end: number }> = [];
  for (const item of raw) {
    const range = parseHourRange(item.label);
    if (!range) continue;
    const previous = slots[slots.length - 1];
    if (previous && previous.start === range.start && previous.end === range.end) continue;
    slots.push({ row: item.row, start: range.start, end: range.end });
  }
  return slots;
}

export function parseDaySheet(sheet: Worksheet): DayParse {
  const read = reader(sheet);
  const result: DayParse = {
    sheetName: sheet.name,
    blocks: [],
    professorBlocksIgnored: 0,
    notes: [],
    skipped: []
  };

  const levelColumns: number[] = [];
  let professorColumns = 0;
  for (let column = 1; column <= read.columns; column++) {
    const cell = read.text(3, column);
    if (/^nivel:/i.test(cell)) levelColumns.push(column);
    else if (/^profesor:/i.test(cell)) professorColumns++;
  }
  result.professorBlocksIgnored = professorColumns;
  if (professorColumns) {
    result.notes.push(
      `Se ignoraron ${professorColumns} bloques por docente: repiten la misma información que los bloques por nivel.`
    );
  }
  if (!levelColumns.length) {
    result.notes.push('No se encontró ningún bloque "Nivel:" en la hoja.');
    return result;
  }

  for (const levelColumn of levelColumns) {
    const levelOrder = levelLabelToOrder(read.text(3, levelColumn + 1));
    const levelName = read.text(3, levelColumn + 1);
    if (!levelOrder) {
      result.skipped.push({
        cell: cellRef(3, levelColumn + 1),
        value: levelName,
        reason: "No se pudo deducir el número de nivel"
      });
    }

    // Días del bloque: se descubren por la cabecera, no por posición fija.
    const days: Array<{ column: number; day: number; label: string }> = [];
    for (let offset = 1; offset <= 7; offset++) {
      const column = levelColumn + offset;
      if (column > read.columns) break;
      const label = read.text(4, column);
      // comparable() quita acentos y pasa a minúsculas: "Miércoles" -> "miercoles".
      const key = comparable(label).toUpperCase();
      const day = DAY_LABELS[key] ?? null;
      if (day) days.push({ column, day, label });
    }
    if (!days.length) {
      result.notes.push(`El bloque de nivel "${levelName}" no tiene días reconocibles.`);
      continue;
    }

    const slots = readTimeSlots(sheet, levelColumn);
    if (!slots.length) {
      result.notes.push(`El bloque de nivel "${levelName}" no tiene franjas horarias.`);
      continue;
    }

    // Celdas con contenido, por día.
    const byDay = new Map<number, Cell[]>();
    for (const slot of slots) {
      for (const day of days) {
        const subject = read.text(slot.row, day.column);
        const professor = read.text(slot.row + 1, day.column);
        const space = read.text(slot.row + 2, day.column);
        if (!subject && !professor && !space) continue;
        const notes: string[] = [];
        if (classifyCell(subject) === "relleno" || !subject) {
          result.skipped.push({
            cell: cellRef(slot.row, day.column),
            value: subject,
            reason: "La celda de materia está vacía o es un separador"
          });
          continue;
        }
        if (!professor) {
          notes.push("La franja no indica docente: se importará sin docente asignado.");
        }
        if (professor.includes("/")) {
          notes.push(
            "La celda de docente contiene más de una persona separada por «/»: revísala antes de publicar."
          );
        }
        if (!space) {
          notes.push("La franja no indica aula.");
        }
        if (!byDay.has(day.day)) byDay.set(day.day, []);
        byDay.get(day.day)!.push({
          row: slot.row,
          column: day.column,
          start: slot.start,
          end: slot.end,
          subject,
          professor,
          space,
          notes
        });
      }
    }

    // Fusionar franjas consecutivas idénticas: son una sola clase más larga.
    for (const [day, cells] of byDay) {
      let group: Cell[] = [];
      const flush = () => {
        if (!group.length) return;
        const first = group[0]!;
        const last = group[group.length - 1]!;
        const notes = [...new Set(group.flatMap((c) => c.notes))];
        if (group.length > 1) {
          notes.push(
            `El libro repite la misma clase en ${group.length} franjas seguidas: se importó como una sola de ${
              (last.end - first.start) / 60
            } horas.`
          );
        }
        result.blocks.push({
          levelOrder,
          dayOfWeek: day,
          startTime: minutesToClock(first.start),
          endTime: minutesToClock(last.end),
          moduleNumber: null,
          subjectName: first.subject,
          professorName: first.professor,
          spaceName: first.space,
          sourceRow: first.row,
          sourceColumn: first.column,
          notes
        });
        group = [];
      };

      for (const cell of cells) {
        const previous = group[group.length - 1];
        const same =
          previous &&
          previous.subject === cell.subject &&
          previous.professor === cell.professor &&
          previous.space === cell.space &&
          previous.end === cell.start; // solo si son contiguas
        if (same) {
          group.push(cell);
          continue;
        }
        flush();
        group = [cell];
      }
      flush();
    }
  }

  if (!result.blocks.length) {
    result.notes.push("No se encontró ninguna clase con materia en los bloques por nivel.");
  }

  return result;
}