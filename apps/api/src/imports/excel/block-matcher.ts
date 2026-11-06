/**
 * Resolución de las referencias que traen los bloques de horario.
 *
 * Un bloque dice "Fundamentos de Programación / Tnlgo(e). Rivera Jeremy / Aula
 * 3-D Laboratorio 04" con texto suelto. Para poder guardarlo hay que encontrar
 * la materia, el docente y el aula reales, y aquí está la trampa: el libro y la
 * base no siempre escriben igual.
 *
 *   - El libro alterna "Apellido Nombre" y "Nombre Apellido".
 *   - El libro anota el aula con un sufijo que la base no tiene
 *     ("Aula 3-D Laboratorio 04" en el libro, "Aula 3-D" en la base).
 *
 * Por eso se prueban varias reglas, de la más estricta a la más floja, y se
 * recuerda cuál se usó: una coincidencia por prefijo se reporta como advertencia
 * para que la persona la confirme, no como un acierto silencioso.
 *
 * Además, los bloques se resuelven contra la base **y** contra lo que la misma
 * importación va a crear. Si no, un docente que aparece en la hoja Inicio pero
 * todavía no está en la base se reportaría como inexistente.
 */
import { comparable, extractTitle, splitPersonName } from "./normalize";
import type { DraftBlock } from "./day-schedule";

export type MatchQuality = "exacta" | "inversa" | "prefijo" | "sinDato";

export interface Resolved<T> {
  item: T | null;
  quality: MatchQuality;
  /** Explicación lista para mostrar en la vista previa. */
  detail: string;
}

export interface MasterRow<T> {
  /** Clave con la que se empareja la fila. */
  key: string;
  /** Clave alternativa, para el caso en que el libro invierte el orden. */
  alternateKey?: string;
  value: T;
}

export interface Catalog {
  subjects: Array<MasterRow<{ id: string; name: string }>>;
  professors: Array<MasterRow<{ id: string; firstNames: string; lastNames: string }>>;
  spaces: Array<MasterRow<{ id: string; name: string; code: string }>>;
}

function miss<T>(detail: string): Resolved<T> {
  return { item: null, quality: "sinDato", detail };
}

/** Busca la materia cuyo nombre coincide exactamente, ignorando acentos. */
export function resolveSubject(name: string, catalog: Catalog): Resolved<{ id: string; name: string }> {
  const key = comparable(name);
  if (!key) return miss("La celda de materia está vacía.");
  const hit = catalog.subjects.find((row) => row.key === key);
  if (hit) return { item: hit.value, quality: "exacta", detail: "" };
  return miss(`La materia «${name}» no existe en esta carrera. Impórtala desde el catálogo o créala en Estructura académica.`);
}

/** Busca el docente probando las dos ordenaciones de nombre. */
export function resolveProfessor(
  cell: string,
  catalog: Catalog
): Resolved<{ id: string; firstNames: string; lastNames: string }> {
  const raw = comparable(cell);
  if (!raw) return miss("La celda de docente está vacía.");

  const { rest } = extractTitle(cell);
  const person = splitPersonName(rest);
  if (!person.firstNames || !person.lastNames) {
    return miss(`No se pudo separar nombre y apellido de «${cell}».`);
  }

  const forward = comparable(person.lastNames + " " + person.firstNames);
  const backward = comparable(person.firstNames + " " + person.lastNames);

  const direct = catalog.professors.find(
    (row) => row.key === forward || row.alternateKey === forward
  );
  if (direct) return { item: direct.value, quality: "exacta", detail: "" };

  const inverse = catalog.professors.find(
    (row) => row.key === backward || row.alternateKey === backward
  );
  if (inverse) {
    return {
      item: inverse.value,
      quality: "inversa",
      detail: `El libro escribe «${cell}» en orden nombre-apellido y el registro dice «${inverse.value.lastNames} ${inverse.value.firstNames}». Se associaron por el nombre invertido.`
    };
  }

  // Último recurso: todas las palabras presentes, en cualquier orden.
  const words = forward.split(" ").filter(Boolean);
  const byParts = catalog.professors.find((row) => words.every((word) => row.key.includes(word)));
  if (byParts) {
    return {
      item: byParts.value,
      quality: "inversa",
      detail: `No se encontró «${cell}» de forma exacta; se associó a «${byParts.value.lastNames} ${byParts.value.firstNames}» comparando solo las palabras. Revísalo.`
    };
  }

  return miss(`El docente «${cell}» no existe todavía.`);
}

/**
 * Busca el aula. El libro le añade el número de laboratorio ("Aula 3-D
 * Laboratorio 04") mientras la base guarda solo "Aula 3-D", así que si no
 * coincide exacto se acepta el prefijo y se avisa.
 */
export function resolveSpace(cell: string, catalog: Catalog): Resolved<{ id: string; name: string; code: string }> {
  const key = comparable(cell);
  if (!key) return { item: null, quality: "sinDato", detail: "La franja no indica aula." };

  const exact = catalog.spaces.find((row) => row.key === key);
  if (exact) return { item: exact.value, quality: "exacta", detail: "" };

  const byPrefix = catalog.spaces.find((row) => row.key && key.startsWith(row.key));
  if (byPrefix) {
    return {
      item: byPrefix.value,
      quality: "prefijo",
      detail: `El libro escribe «${cell}» y el aula registrada es «${byPrefix.value.name}» (${byPrefix.value.code}). Se asociaron por coincidencia parcial.`
    };
  }

  return miss(`El aula «${cell}» no existe todavía.`);
}

/** Minutos desde medianoche a partir de un "HH:MM:SS". */
export function toMinutes(clock: string): number {
  return Number(clock.slice(0, 2)) * 60 + Number(clock.slice(3, 5));
}

/** Clave de agrupacion para detectar choques. */
function overlapKey(block: DraftBlock, shift: "DAY" | "NIGHT"): string {
  // El horario nocturno no tiene días: lo que separa dos clases es el módulo.
  if (shift === "NIGHT") return `mod:${block.moduleNumber ?? 0}`;
  return `dia:${block.dayOfWeek ?? 0}`;
}

export interface BlockConflict {
  kind: "profesor" | "aula" | "solape";
  withIndex: number;
  message: string;
}

/**
 * Detecta choques entre los bloques que se van a importar.
 *
 * Cada nivel es un horario independiente, así que el mismo docente puede dar
 * clase a PRIMERO y a SEGUNDO el lunes a la misma hora sin que sea un problema.
 * Solo se comparan bloques del mismo nivel.
 */
export function detectConflicts(
  blocks: DraftBlock[],
  shift: "DAY" | "NIGHT",
  resolve: (block: DraftBlock) => { professorKey: string | null; spaceKey: string | null }
): Map<number, BlockConflict[]> {
  const byLevel = new Map<number, number[]>();
  blocks.forEach((block, index) => {
    const level = block.levelOrder ?? 0;
    if (!byLevel.has(level)) byLevel.set(level, []);
    byLevel.get(level)!.push(index);
  });

  const found = new Map<number, BlockConflict[]>();
  const add = (index: number, conflict: BlockConflict) => {
    const list = found.get(index) ?? [];
    if (!list.some((c) => c.kind === conflict.kind && c.withIndex === conflict.withIndex)) {
      list.push(conflict);
      found.set(index, list);
    }
  };

  for (const [, indices] of byLevel) {
    for (let i = 0; i < indices.length; i++) {
      const a = blocks[indices[i]!];
      if (!a) continue;
      const ra = resolve(a);
      for (let j = i + 1; j < indices.length; j++) {
        const b = blocks[indices[j]!];
        if (!b) continue;
        if (overlapKey(a, shift) !== overlapKey(b, shift)) continue;
        const overlaps =
          toMinutes(a.startTime) < toMinutes(b.endTime) &&
          toMinutes(b.startTime) < toMinutes(a.endTime);
        if (!overlaps) continue;

        const rb = resolve(b);
        const cuando = `${a.dayOfWeek ? dayLabel(a.dayOfWeek) + " " : ""}${a.startTime.slice(0, 5)}-${a.endTime.slice(0, 5)}`;
        if (ra.professorKey && rb.professorKey && ra.professorKey === rb.professorKey) {
          add(indices[j]!, {
            kind: "profesor",
            withIndex: indices[i]!,
            message: `El docente ya está asignado a «${a.subjectName}» a esa misma hora (${cuando}).`
          });
        }
        if (ra.spaceKey && rb.spaceKey && ra.spaceKey === rb.spaceKey) {
          add(indices[j]!, {
            kind: "aula",
            withIndex: indices[i]!,
            message: `El aula ya está ocupada por «${a.subjectName}» a esa misma hora (${cuando}).`
          });
        }
        if ((ra.professorKey && ra.professorKey === rb.professorKey) || (ra.spaceKey && ra.spaceKey === rb.spaceKey)) {
          continue;
        }
        add(indices[j]!, {
          kind: "solape",
          withIndex: indices[i]!,
          message: `Se pisa con «${a.subjectName}» (${cuando}).`
        });
      }
    }
  }

  return found;
}

function dayLabel(day: number): string {
  return ["", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"][day] ?? "";
}