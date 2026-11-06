/**
 * Normalización de los valores que llegan del libro Excel de INTESUD.
 *
 * El libro no es una fuente limpia: mezcla títulos abreviados, ordinales con
 * punto, celdas de relleno ("---", "- Sin Profesor -", "Aula XX") y dos
 * docentes en una misma celda. Aquí se decide qué es un dato, qué es relleno y
 * qué es ambiguo y necesita revisión humana.
 */

/** Quita acentos y normaliza para poder comparar sin falsos negativos. */
export function comparable(value: string | null | undefined) {
  if (!value) return "";
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[\u00a0\u2007\u202f]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();
}

/** Limpieza de presentación: conserva acentos, quita espacios raros. */
export function tidy(value: string | null | undefined) {
  if (!value) return "";
  return String(value)
    .replace(/[\u00a0\u2007\u202f]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

/**
 * Patrones de relleno del libro. No son datos: se descartan y quedan
 * registrados como omisiones para que la vista previa los muestre.
 */
const FILLER_PATTERNS = [
  /^-+$/, // --- o --
  /^-\s*sin\s+profesor\s*-?$/i, // - Sin Profesor -
  /^-\s*sin\s+asignar\s*-?$/i,
  /^profesor\s+n\.?n\.?$/i, // Profesor N.N.
  /^profesor\s+xx$/i,
  /^aula\s+xx$/i, // Aula XX
  /^lista\s+de\s+sus\s+profesores$/i, // etiqueta usada como valor
  /^escoja\b/i, // "ESCOJA PRIMERO LA ESCUELA"
  /^elige\b/i,
  /^seleccione\b/i,
  /^\d+(\.\d+)?$/ // 0, 1, 3.3333 -> creditos/indices, nunca un nombre
];

/**
 * Separadores decorados que usa el libro entre bloques: " --- v --- ",
 * " --- ^ --- ", " --- | --- ", " --- - --- ". Son guiones con adornos, asi que
 * no los cubre /^-+$/, pero tampoco pueden confundirse con un nombre.
 */
function isDecoratedSeparator(text: string) {
  if (!/[-–—]/.test(text)) return false;
  return /^[\s\-–—|^~vVx×.·]+$/.test(text);
}

/**
 * Coerción segura a texto. Los payloads guardados en `rawData` son Json, así que
 * sus campos llegan como `unknown`: usar String() directamente produciría
 * "[object Object]" en lugar del valor.
 */
export function asText(value: unknown): string {
  if (typeof value === "string") return tidy(value);
  if (typeof value === "number" && Number.isFinite(value)) return String(value);
  if (typeof value === "boolean") return String(value);
  return "";
}

export type CellKind = "dato" | "relleno";

export function classifyCell(value: string | null | undefined): CellKind {
  const text = comparable(value);
  if (!text) return "relleno";
  if (isDecoratedSeparator(text)) return "relleno";
  return FILLER_PATTERNS.some((pattern) => pattern.test(text)) ? "relleno" : "dato";
}

/**
 * Títulos que usa el libro, traducidos al vocabulario que ya entiende la
 * aplicación (ver professors/professional-title.ts). "Tnlgo(e)." aparece en el
 * Excel pero no existía en esa tabla, así que se resuelve aquí.
 */
const EXCEL_TITLE_MAP: Array<[RegExp, string]> = [
  [/^tnlga\.?\s*s\.?u\.?$/i, "Tnlga. S.U."],
  [/^tnlgo\.?\s*s\.?u\.?$/i, "Tnlgo. S.U."],
  [/^tnlga\.?\(e\)\.?$/i, "Tnlga."],
  [/^tnlgo\.?\(e\)\.?$/i, "Tnlgo."],
  [/^tnlga\.?$/i, "Tnlga."],
  [/^tnlgo\.?$/i, "Tnlgo."],
  [/^msc\.?$/i, "MSc."],
  [/^phd\.?$/i, "PhD."],
  [/^mtro?\.?$/i, "Mtr."],
  [/^mtra\.?$/i, "Mtr."],
  [/^ing\.?$/i, "Ing."],
  [/^lcdo\.?$/i, "Lcdo."],
  [/^lcda\.?$/i, "Lcda."],
  [/^arq\.?$/i, "Arq."],
  [/^dr\.?$/i, "Dr."],
  [/^dra\.?$/i, "Dra."],
  [/^prof\.?$/i, "Prof."],
  [/^profa\.?$/i, "Profa."]
];

export interface ExtractedTitle {
  /** Título canónico, o null si la celda no trae título. */
  title: string | null;
  /** El resto del texto, ya sin el título. */
  rest: string;
}

/** Separa el título abreviado del resto del nombre. */
export function extractTitle(raw: string): ExtractedTitle {
  const text = tidy(raw);
  if (!text) return { title: null, rest: "" };

  // El título siempre es el primer token terminado en punto.
  const match = text.match(/^([A-Za-zÁÉÍÓÚÑáéíóúñ().\s]{1,24}?\.)\s+(.+)$/);
  if (!match) return { title: null, rest: text };

  const candidate = (match[1] ?? "").trim();
  const remainder = match[2] ?? "";
  const found = EXCEL_TITLE_MAP.find(([pattern]) => pattern.test(candidate));
  if (!found) return { title: null, rest: text };

  return { title: found[1], rest: tidy(remainder) };
}

export interface SplitPerson {
  firstNames: string;
  lastNames: string;
  /** Hay que revisarlo a mano: dos docentes, o nombre de 3+ palabras. */
  ambiguous: boolean;
  reason?: string;
}

/**
 * El libro escribe "Apellido Nombre" (convención ecuatoriana), pero con
 * excepciones: "Mtr. Villasis / Tnlgo. Rivera" son dos personas en una celda y
 * "MSc. Diego San Andrés" invierte el orden. Cuando no hay certeza se marca
 * para que la vista previa lo muestre en vez de adivinar en silencio.
 */
export function splitPersonName(rest: string): SplitPerson {
  const text = tidy(rest);
  if (!text) {
    return { firstNames: "", lastNames: "", ambiguous: true, reason: "Nombre vacío" };
  }

  if (text.includes("/")) {
    return {
      firstNames: "",
      lastNames: "",
      ambiguous: true,
      reason: "La celda contiene más de un docente separado por «/»: se debe registrar por separado"
    };
  }

  const words = text.split(" ").filter(Boolean);
  if (words.length === 0) {
    return { firstNames: "", lastNames: "", ambiguous: true, reason: "Nombre vacío" };
  }
  if (words.length === 1) {
    return { firstNames: words[0] ?? "", lastNames: "", ambiguous: true, reason: "Solo una palabra: no se puede separar nombre y apellido" };
  }
  if (words.length === 2) {
    // "Morales Gabriela" -> apellido Morales, nombre Gabriela
    return { firstNames: words[1] ?? "", lastNames: words[0] ?? "", ambiguous: false };
  }

  // Tres o más: "Flores Juan José" (apellido + nombre doble) es lo normal, pero
  // "Diego San Andrés" invierte el orden. Se asume la convención y se avisa.
  return {
    firstNames: words.slice(1).join(" "),
    lastNames: words[0] ?? "",
    ambiguous: true,
    reason: "El nombre tiene 3 o más palabras: se asumió que la primera es el apellido. Confirma si es correcto."
  };
}

const ORDINAL_WORDS: Record<string, number> = {
  primero: 1,
  segundo: 2,
  tercero: 3,
  cuarto: 4,
  quinto: 5
};

/**
 * El libro usa dos formas de nivel: "1ro." en los catálogos y "PRIMERO" en la
 * lista de niveles. Además el código de la materia lo repite
 * (DS-B-01-001 -> nivel 1), lo que sirve de respaldo.
 */
export function levelLabelToOrder(label: string | null | undefined): number | null {
  const text = comparable(label);
  if (!text) return null;

  const ordinal = text.match(/^(\d{1,2})\s*(?:ro|do|to|er|era|º|°)?/);
  if (ordinal) {
    const value = Number(ordinal[1]);
    if (Number.isInteger(value) && value >= 1 && value <= 20) return value;
  }

  const stripped = text.replace(/[.\s°º]/g, "");
  const word = ORDINAL_WORDS[stripped];
  return typeof word === "number" ? word : null;
}

/** Respaldo: extrae el nivel del código, p. ej. "DS-B-01-001" -> 1. */
export function levelFromSubjectCode(code: string | null | undefined): number | null {
  const text = comparable(code);
  if (!text) return null;
  const parts = text.split("-");
  if (parts.length < 3) return null;
  const candidate = Number(parts[1]);
  return Number.isInteger(candidate) && candidate >= 1 && candidate <= 20 ? candidate : null;
}

/** Deriva un código de aula estable a partir de su nombre. */
export function spaceCodeFromName(name: string, taken: Set<string>) {
  const text = comparable(name);
  const base = text
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toUpperCase()
    .slice(0, 24) || "ESPACIO";
  if (!taken.has(base)) {
    taken.add(base);
    return base;
  }
  let n = 2;
  while (taken.has(`${base}-${n}`)) n++;
  taken.add(`${base}-${n}`);
  return `${base}-${n}`;
}

/** "Aula 3-A Laboratorio 01" es laboratorio; "Aula 1-D" es aula. */
export function spaceTypeFromName(name: string): "CLASSROOM" | "LABORATORY" {
  return /laboratorio|lab\s*\d|taller/i.test(name) ? "LABORATORY" : "CLASSROOM";
}
