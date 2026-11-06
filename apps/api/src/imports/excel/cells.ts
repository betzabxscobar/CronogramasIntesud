/**
 * Utilidades para leer celdas de exceljs sin conocer su tipo interno.
 *
 * Un .xlsx guarda cada celda de una forma distinta según quién lo escribió:
 * texto plano, número, fórmula con resultado, texto enriquecido, hipervínculo o
 * celda combinada cuya esclava devuelve el valor de la maestra. Centralizar esto
 * evita que cada lector invente su propia forma de Romper un libro real.
 */
import type { Worksheet } from "exceljs";
import { asText, tidy } from "./normalize";

/** Devuelve el texto de una celda sea cual sea su tipo interno. */
export function cellText(cell: { value: unknown } | null | undefined): string {
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

/** Igual que cellText pero devuelve el número cuando la celda lo es. */
export function cellNumber(cell: { value: unknown } | null | undefined): number | null {
  const value = cell?.value;
  if (typeof value === "number" && Number.isFinite(value)) return value;
  const text = cellText(cell).replace(",", ".");
  if (!text) return null;
  const parsed = Number(text);
  return Number.isFinite(parsed) ? parsed : null;
}

/** Letra de columna (1 -> A, 27 -> AA) para citar celdas en los mensajes. */
export function columnLetter(column: number): string {
  let letters = "";
  let n = column;
  while (n > 0) {
    const rest = (n - 1) % 26;
    letters = String.fromCharCode(65 + rest) + letters;
    n = (n - rest - 1) / 26;
  }
  return letters;
}

/** Referencia legible de una celda, p. ej. "AD42". */
export function cellRef(row: number, column: number): string {
  return columnLetter(column) + row;
}

/**
 * Paleta de Excel de los libros que genera Office. Un color guardado como
 * "tema 6 con tinte 0.8" no significa nada si no se sabe qué color es el tema 6
 * de ese libro, asi que se resuelve aqui contra la paleta estandar.
 */
const TEMA_EXCEL = [
  "FFFFFF", "44546A", "E7E6E6", "44546A",
  "5B9BD5", "ED7D31", "A5A5A5", "FFC000", "4472C4", "70AD47", "0563C1", "954F72"
];

/**
 * Color de fondo de una celda en hexadecimal "#RRGGBB", o null si no tiene
 * relleno. Excel aclara hacia el blanco con tinte positivo y oscurece con
 * negativo; el gris automatico (indice 64) se resuelve como negro.
 */
export function cellFill(cell: { fill?: unknown } | null | undefined): string | null {
  const fill = cell?.fill as { type?: string; pattern?: string; fgColor?: unknown } | undefined;
  if (!fill || fill.type !== "pattern" || fill.pattern !== "solid") return null;
  const color = fill.fgColor as { argb?: string; theme?: number; tint?: number; indexed?: number } | undefined;
  if (!color) return null;

  let base: string | null = null;
  if (typeof color.argb === "string") base = color.argb.length === 8 ? color.argb.slice(2) : color.argb;
  else if (typeof color.theme === "number") base = TEMA_EXCEL[color.theme] ?? null;
  else if (color.indexed === 64) base = "000000";
  if (!base || !/^[0-9A-Fa-f]{6}$/.test(base)) return null;

  const tint = color.tint ?? 0;
  const canales = [0, 1, 2].map((i) => parseInt(base.slice(i * 2, i * 2 + 2), 16));
  const tinteados =
    tint < 0
      ? canales.map((v) => Math.round(v * (1 + tint)))
      : tint > 0
        ? canales.map((v) => Math.round(v + (255 - v) * tint))
        : canales;
  return "#" + tinteados.map((v) => Math.max(0, Math.min(255, v)).toString(16).padStart(2, "0")).join("").toUpperCase();
}

/** Lector de celdas de una hoja concreta. */
export function reader(sheet: Worksheet) {
  return {
    text: (row: number, column: number) => cellText(sheet.getCell(row, column)),
    number: (row: number, column: number) => cellNumber(sheet.getCell(row, column)),
    fill: (row: number, column: number) => cellFill(sheet.getCell(row, column)),
    rows: sheet.rowCount,
    columns: sheet.columnCount,
    name: sheet.name
  };
}