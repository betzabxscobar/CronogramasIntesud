/**
 * Lector de la hoja "HorariosClasesNocturno" del libro "Horarios DS 2026".
 *
 * La hoja no tiene días: el eje horizontal son las semanas del periodo y las
 * franjas se repiten de lunes a viernes. Por eso los bloques se guardan con un
 * único día (lunes), que es la convención que ya usa la aplicación.
 *
 * Cada bloque mide 23 columnas:
 *
 *   col+0  etiqueta del bloque (nivel, "SEMANAS:", "MÓDULOS:" y las horas)
 *   col+1..   una columna por semana, agrupadas de tres en tres por módulo,
 *             con una columna en blanco entre grupos
 *
 * Las franjas son de dos filas (materia y docente) porque el horario nocturno
 * no asigna aula.
 */
import type { Worksheet } from "exceljs";
import { cellRef, reader } from "./cells";
import { classifyCell, levelLabelToOrder } from "./normalize";
import { minutesToClock, parseHourRange, type DraftBlock } from "./day-schedule";

/** Ancho del bloque, deducido de la separación entre etiquetas "SEMANAS:". */
const BLOCK_STRIDE = 23;

/** Días que se repiten al no existir el eje de días. */
const NIGHT_DAY = 1;

const PLACEHOLDER = /^(asignatura|docente|aula|lista de sus profesores|-+)$/i;

export interface NightParse {
  sheetName: string;
  blocks: DraftBlock[];
  professorBlocksIgnored: number;
  notes: string[];
  skipped: Array<{ cell: string; value: string; reason: string }>;
  /** Celdas que tenían el texto "Asignatura" en vez de una materia real. */
  placeholderCount: number;
}

export function parseNightSheet(sheet: Worksheet): NightParse {
  const read = reader(sheet);
  const result: NightParse = {
    sheetName: sheet.name,
    blocks: [],
    professorBlocksIgnored: 0,
    notes: [],
    skipped: [],
    placeholderCount: 0
  };

  // Los bloques se localizan por la etiqueta "SEMANAS:" de la fila 4.
  const blockColumns: number[] = [];
  for (let column = 1; column <= read.columns; column++) {
    if (/^semanas/i.test(read.text(4, column))) blockColumns.push(column);
  }
  if (!blockColumns.length) {
    result.notes.push('No se encontró ningún bloque "SEMANAS:" en la hoja nocturna.');
    return result;
  }

  const NIVELES = /^(PRIMERO|SEGUNDO|TERCERO|CUARTO|QUINTO)$/i;
  const seenModules = new Map<string, number>();

  for (const blockColumn of blockColumns) {
    const blockName = read.text(3, blockColumn);
    if (!NIVELES.test(blockName.trim())) {
      // Bloque por docente: misma informacion vista desde otro lado.
      result.professorBlocksIgnored++;
      continue;
    }

    const levelOrder = levelLabelToOrder(blockName);
    const limit = Math.min(read.columns, blockColumn + BLOCK_STRIDE);

    // Columna -> número de semana (fila 5).
    const weeks = new Map<number, number>();
    for (let column = blockColumn; column <= limit; column++) {
      const value = read.text(5, column);
      if (/^\d{1,2}$/.test(value)) weeks.set(column, Number(value));
    }
    if (!weeks.size) {
      result.notes.push(`El bloque "${blockName}" no tiene semanas numeradas.`);
      continue;
    }

    // Columna -> módulo. La fila 6 solo trae el número en la primera columna de
    // cada módulo porque está combinada, así que se arrastra hacia adelante.
    const modules = new Map<number, number>();
    let current: number | null = null;
    for (let column = blockColumn; column <= limit; column++) {
      const value = read.text(6, column);
      if (/^\d{1,2}$/.test(value)) current = Number(value);
      if (current !== null) modules.set(column, current);
    }

    // Franjas: la etiqueta de hora también está combinada en vertical, así que
    // la misma franja aparece en la fila de materia y en la de docente.
    const rawSlots: Array<{ row: number; label: string }> = [];
    for (let row = 5; row <= read.rows; row++) {
      const label = read.text(row, blockColumn);
      if (/^\d{1,2}:\d{2}\s*-\s*\d{1,2}:\d{2}/.test(label)) rawSlots.push({ row, label });
    }
    const slots: Array<{ row: number; start: number; end: number }> = [];
    for (const item of rawSlots) {
      const range = parseHourRange(item.label);
      if (!range) continue;
      const previous = slots[slots.length - 1];
      if (previous && previous.start === range.start && previous.end === range.end) continue;
      slots.push({ row: item.row, start: range.start, end: range.end });
    }
    if (!slots.length) {
      result.notes.push(`El bloque "${blockName}" no tiene franjas horarias.`);
      continue;
    }

    for (const slot of slots) {
      // Se agrupa por módulo: cada módulo tiene su propia materia.
      const byModule = new Map<number, { subject: string; professor: string; column: number; weeks: number[] }>();
      for (const [column, week] of weeks) {
        const subject = read.text(slot.row, column);
        if (!subject) continue;
        const professor = read.text(slot.row + 1, column);
        const module = modules.get(column) ?? null;

        if (PLACEHOLDER.test(subject) || classifyCell(subject) === "relleno") {
          result.placeholderCount++;
          const ref = cellRef(slot.row, column);
          if (!seenModules.has(ref)) {
            seenModules.set(ref, 1);
            result.skipped.push({
              cell: ref,
              value: subject,
              reason: "El libro todavía no tiene la materia de esta semana: la plantilla sigue vacía"
            });
          }
          continue;
        }

        const key = module ?? 0;
        const existing = byModule.get(key);
        if (existing) {
          existing.weeks.push(week);
          continue;
        }
        byModule.set(key, { subject, professor, column, weeks: [week] });
      }

      for (const [module, data] of byModule) {
        const notes: string[] = [];
        if (!data.professor) {
          notes.push("La franja no indica docente: se importará sin docente asignado.");
        } else if (PLACEHOLDER.test(data.professor)) {
          notes.push("El libro todavía no tiene el docente de esta franja.");
          continue;
        }
        if (data.professor.includes("/")) {
          notes.push("La celda de docente contiene más de una persona separada por «/».");
        }
        notes.push(
          `El horario nocturno no distingue días: se aplicó a lunes-viernes y se repetirá en cada módulo.`
        );

        result.blocks.push({
          levelOrder,
          dayOfWeek: NIGHT_DAY,
          startTime: minutesToClock(slot.start),
          endTime: minutesToClock(slot.end),
          moduleNumber: module || null,
          subjectName: data.subject,
          professorName: data.professor,
          spaceName: "",
          sourceRow: slot.row,
          sourceColumn: data.column,
          notes
        });
      }
    }
  }

  if (result.professorBlocksIgnored) {
    result.notes.push(
      `Se ignoraron ${result.professorBlocksIgnored} bloques por docente: repiten la misma información que los bloques por nivel.`
    );
  }
  if (result.placeholderCount) {
    result.notes.push(
      `La hoja nocturna tiene ${result.placeholderCount} celdas con la plantilla sin diligenciar (la palabra "Asignatura" donde debería ir la materia). No se importaron.`
    );
  }
  if (!result.blocks.length) {
    result.notes.push("La hoja nocturna no tiene ninguna clase con materia y docente reales.");
  }

  return result;
}