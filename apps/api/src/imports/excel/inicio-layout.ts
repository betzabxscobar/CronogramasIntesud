/**
 * Lector de la hoja "Inicio" del libro "Horarios DS 2026".
 *
 * El layout se descubre en lugar de fijarse a coordenadas, porque la hoja
 * tiene nueve catálogos de asignaturas colocados en paralelo y cada uno ocupa
 * seis columnas:
 *
 *   col+0  Asignatura          (fila 1 = carrera, fila 2 = "Asignaturas XX:")
 *   col+1  (separador)
 *   col+2  Créditos
 *   col+3  Código
 *   col+4  Nivel
 *   col+5  (separador)
 *
 * A la izquierda, en columnas contiguas, están las listas maestras: los
 * docentes en C/D, las aulas en F y los niveles en H.
 */
import type { Worksheet } from "exceljs";
import {
  asText,
  classifyCell,
  comparable,
  extractTitle,
  levelFromSubjectCode,
  levelLabelToOrder,
  spaceCodeFromName,
  spaceTypeFromName,
  splitPersonName,
  tidy
} from "./normalize";

// Columnas de las listas de la izquierda, en base 1.
const COL_CAREER = 1; // A: etiqueta "CARRERA" y el valor de la carrera activa
const ROW_CAREER_VALUE = 2;
const COL_PROF_NAME = 4; // D
const COL_ROOM = 6; // F
const COL_LEVEL = 8; // H

const ROW_LIST_START = 2;
const ROW_PROF_START = 3;
const CATALOG_HEADER_ROW = 2;
const CATALOG_FIRST_DATA_ROW = 4;

// Techo de seguridad: evita que una fila inesperada se convierta en un bucle
// infinito. Ninguna lista real del libro pasa de 60 filas.
const MAX_LIST_ROWS = 60;

export interface DraftLevel {
  order: number;
  name: string;
  sourceRow: number;
}

export interface DraftSubject {
  name: string;
  code: string | null;
  levelOrder: number | null;
  credits: number | null;
  sourceRow: number;
  /** Columna del bloque de catálogo donde se leyó, para poder filtrar. */
  blockColumn: number;
  notes: string[];
}

export interface DraftProfessor {
  firstNames: string;
  lastNames: string;
  professionalTitle: string | null;
  sourceRow: number;
  notes: string[];
}

export interface DraftSpace {
  name: string;
  code: string;
  type: "CLASSROOM" | "LABORATORY";
  sourceRow: number;
  notes: string[];
}

export interface CatalogBlock {
  column: number;
  career: string;
  subjects: number;
}

export interface SkippedCell {
  cell: string;
  value: string;
  reason: string;
}

export interface InicioParse {
  sheetName: string;
  activeCareer: string | null;
  levels: DraftLevel[];
  subjects: DraftSubject[];
  professors: DraftProfessor[];
  spaces: DraftSpace[];
  catalogBlocks: CatalogBlock[];
  skipped: SkippedCell[];
  notes: string[];
}

/** Lee el texto de una celda sea cual sea su tipo interno. */
function cellText(cell: { value: unknown } | null | undefined): string {
  const value = cell?.value;
  if (value === null || value === undefined) return "";
  if (typeof value === "string") return tidy(value);
  if (typeof value === "number") return String(value);
  if (value instanceof Date) return value.toISOString();
  if (typeof value === "object") {
    const record = value as Record<string, unknown>;
    if (Array.isArray(record.richText)) {
      return tidy(
        record.richText
          .map((part) => {
            if (typeof part !== "object" || part === null || !("text" in part)) return "";
            return asText((part as { text: unknown }).text);
          })
          .join("")
      );
    }
    if ("text" in record) return asText(record.text);
    if ("result" in record) return cellText({ value: record.result });
    if ("hyperlink" in record) return asText(record.hyperlink);
  }
  return asText(value);
}

function cellNumber(cell: { value: unknown } | null | undefined): number | null {
  const value = cell?.value;
  if (typeof value === "number" && Number.isFinite(value)) return value;
  const text = cellText(cell).replace(",", ".");
  if (!text) return null;
  const parsed = Number(text);
  return Number.isFinite(parsed) ? parsed : null;
}

/** Devuelve la letra de columna para poder citar la celda en los mensajes. */
function columnLetter(column: number) {
  let letters = "";
  let n = column;
  while (n > 0) {
    const rest = (n - 1) % 26;
    letters = String.fromCharCode(65 + rest) + letters;
    n = (n - rest - 1) / 26;
  }
  return letters;
}

function at(sheet: Worksheet, row: number, column: number) {
  return cellText(sheet.getCell(row, column));
}

/**
 * Recorre una lista vertical. Los rellenos ("- Sin Profesor -", "Aula XX")
 * aparecen *dentro* de las listas, no solo al final, así que se omiten y se
 * sigue leyendo. La lista termina en la primera celda realmente vacía, que es
 * lo que acota además la segunda región de datos de la hoja (filas 103+).
 */
function readRun(
  sheet: Worksheet,
  column: number,
  startRow: number,
  onValue: (row: number, value: string) => void,
  skipped: SkippedCell[]
) {
  const limit = Math.min(sheet.rowCount, startRow + MAX_LIST_ROWS);
  for (let row = startRow; row <= limit; row++) {
    const raw = at(sheet, row, column);
    if (!raw) return; // celda vacía: fin de la lista
    if (classifyCell(raw) === "relleno") {
      skipped.push({
        cell: columnLetter(column) + row,
        value: raw,
        reason: "Celda de relleno del libro, no es un dato"
      });
      continue;
    }
    onValue(row, raw);
  }
}

export function parseInicioSheet(sheet: Worksheet, careerColumn?: number): InicioParse {
  const result: InicioParse = {
    sheetName: sheet.name,
    activeCareer: null,
    levels: [],
    subjects: [],
    professors: [],
    spaces: [],
    catalogBlocks: [],
    skipped: [],
    notes: []
  };

  // --- carrera activa ---
  const activeCareer = at(sheet, ROW_CAREER_VALUE, COL_CAREER);
  if (classifyCell(activeCareer) === "dato") result.activeCareer = activeCareer;
  else result.notes.push(`No se encontró la carrera activa en ${columnLetter(COL_CAREER)}${ROW_CAREER_VALUE}.`);

  // --- niveles (columna H) ---
  const levelOrderByName = new Map<string, number>();
  readRun(
    sheet,
    COL_LEVEL,
    ROW_LIST_START,
    (row, value) => {
      const order = levelLabelToOrder(value);
      if (!order) {
        result.skipped.push({
          cell: columnLetter(COL_LEVEL) + row,
          value,
          reason: "No se pudo deducir el número de nivel"
        });
        return;
      }
      result.levels.push({ order, name: value, sourceRow: row });
      levelOrderByName.set(comparable(value), order);
    },
    result.skipped
  );
  if (!result.levels.length) result.notes.push("La lista de niveles está vacía.");

  // --- docentes (columna D, índice en C) ---
  readRun(
    sheet,
    COL_PROF_NAME,
    ROW_PROF_START,
    (row, value) => {
      const notes: string[] = [];
      const { title, rest } = extractTitle(value);
      const person = splitPersonName(rest);
      if (person.ambiguous) notes.push(person.reason ?? "Nombre ambiguo");
      if (!person.firstNames || !person.lastNames) {
        // No se puede registrar tal cual: se deja a la vista para que la
        // persona disponible decida, en vez de descartarlo en silencio.
        result.skipped.push({
          cell: columnLetter(COL_PROF_NAME) + row,
          value,
          reason: person.reason ?? "No se pudo separar nombre y apellido"
        });
        return;
      }
      result.professors.push({
        firstNames: person.firstNames,
        lastNames: person.lastNames,
        professionalTitle: title,
        sourceRow: row,
        notes
      });
    },
    result.skipped
  );
  if (!result.professors.length) result.notes.push("No se encontró ninguna lista de docentes.");

  // --- aulas (columna F) ---
  const takenCodes = new Set<string>();
  readRun(
    sheet,
    COL_ROOM,
    ROW_LIST_START,
    (row, value) => {
      result.spaces.push({
        name: value,
        code: spaceCodeFromName(value, takenCodes),
        type: spaceTypeFromName(value),
        sourceRow: row,
        notes: []
      });
    },
    result.skipped
  );
  if (!result.spaces.length) result.notes.push("No se encontró ninguna lista de aulas.");

  // --- catálogos de asignaturas ---
  // Se localizan buscando la etiqueta "Asignaturas ..." en la fila 2, en vez de
  // asumir posiciones fijas.
  const width = Math.max(sheet.columnCount, 1);
  const blocks: number[] = [];
  for (let column = 1; column <= width; column++) {
    if (/^asignaturas/i.test(at(sheet, CATALOG_HEADER_ROW, column))) blocks.push(column);
  }

  for (const base of blocks) {
    const career = tidy(at(sheet, 1, base));
    let count = 0;
    for (let row = CATALOG_FIRST_DATA_ROW; row <= Math.min(sheet.rowCount, CATALOG_FIRST_DATA_ROW + 400); row++) {
      const name = at(sheet, row, base);
      if (!name) break; // celda vacía: fin del bloque
      if (classifyCell(name) === "relleno") {
        // Separadores entre niveles y al final del bloque: se omiten, no cortan.
        result.skipped.push({
          cell: columnLetter(base) + row,
          value: name,
          reason: "Separador del catálogo"
        });
        continue;
      }
      const notes: string[] = [];
      const code = tidy(at(sheet, row, base + 3)) || null;
      const credits = cellNumber(sheet.getCell(row, base + 2));
      const levelOrder =
        levelLabelToOrder(at(sheet, row, base + 4)) ?? levelFromSubjectCode(code);
      if (!levelOrder) {
        notes.push("El libro no indica el nivel de esta materia.");
      }
      if (!code) notes.push("La materia no tiene código en el libro.");
      result.subjects.push({
        name,
        code,
        levelOrder,
        credits,
        sourceRow: row,
        blockColumn: base,
        notes
      });
      count++;
    }
    result.catalogBlocks.push({ column: base, career, subjects: count });
  }

  if (!result.catalogBlocks.length) {
    result.notes.push('No se encontró ningún bloque "Asignaturas" en la hoja Inicio.');
  }

  // Si no se indicó bloque, se usa el de la carrera que el propio libro tiene
  // seleccionada. Sin esto se importarían los nueve catálogos (unas 300
  // materias de todas las carreras) contra una sola, que es un error fácil de
  // cometer y difícil de detectar después.
  const columnaPedida = typeof careerColumn === "number" ? careerColumn : null;
  const bloquePorNombre = result.activeCareer
    ? result.catalogBlocks.find((b) => comparable(b.career) === comparable(result.activeCareer))
    : undefined;
  const bloquePorColumna =
    columnaPedida === null
      ? undefined
      : result.catalogBlocks.find((b) => b.column === columnaPedida);

  if (bloquePorColumna && bloquePorNombre && bloquePorColumna !== bloquePorNombre) {
    result.subjects = result.subjects.filter((s) => s.blockColumn === bloquePorColumna.column);
    result.notes.push(
      `Pediste el catálogo «${bloquePorColumna.career}», pero el libro tiene seleccionada «${result.activeCareer}». Se importó el que pediste.`
    );
  } else if (bloquePorColumna || bloquePorNombre) {
    const chosen = (bloquePorColumna ?? bloquePorNombre)!;
    const descartados = result.catalogBlocks.length - 1;
    result.subjects = result.subjects.filter((s) => s.blockColumn === chosen.column);
    result.notes.push(
      columnaPedida === null
        ? `El libro tiene seleccionada la carrera «${chosen.career}»: se importó solo su catálogo (${chosen.subjects} materias) y se descartaron ${descartados} bloques de otras carreras.`
        : `Se conservó el catálogo «${chosen.career}» (${chosen.subjects} materias) y se descartaron ${descartados} bloques de otras carreras.`
    );
  } else if (result.activeCareer) {
    // Ni la columna pedida ni el nombre de la carrera activa están en la hoja.
    // Aun así no se leen los nueve catálogos a ciegas: se avisa y se siguen
    // leyendo todos, pero quien lo lea tiene que saber que están mezclados.
    result.notes.push(
      `No se encontró el catálogo de «${result.activeCareer}» ni en la columna ${columnaPedida}. Se leyeron los ${result.catalogBlocks.length} bloques del libro y las materias de todas las carreras quedaron mezcladas: revísalas antes de confirmar.`
    );
  } else if (columnaPedida !== null) {
    result.notes.push(
      `La columna ${columnaPedida} no corresponde a un bloque de asignaturas y el libro no indica la carrera activa: se leyeron todos los bloques.`
    );
  }

  return result;
}
