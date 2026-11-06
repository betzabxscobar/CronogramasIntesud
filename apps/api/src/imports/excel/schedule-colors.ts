/**
 * Colores del libro de horarios.
 *
 * El color de una clase lo decide el docente, no el archivo: cada docente tiene
 * un `scheduleColor` que se edita en el panel de Profesores y es el mismo que
 * se ve en el horario de la plataforma. El libro exportado usa ese color, para
 * que lo que se reparte en el papel se vea igual que lo que se ve en pantalla.
 * No hay una segunda fuente de color: si aqui se inventara uno, el papel y la
 * plataforma dirian cosas distintas.
 */

/**
 * Las franjas sin clase no llevan fondo. Podría ponerse un gris, pero algún
 * docente puede tener ese mismo gris y entonces su clase se vería como una
 * franja vacía. Sin fondo, lo que distingue una clase de un hueco es que tiene
 * texto, y eso no se confunde con ningún color.
 */

/**
 * Los seis tonos del encabezado de módulos del horario nocturno. El archivo
 * antigo los usaba para distinguir un módulo del siguiente; la plataforma no
 * colorea los módulos, así que estos tonos son solo del libro.
 */
export const TONO_MODULO = [
  "#385724",
  "#548235",
  "#BFBFBF",
  "#806000",
  "#BF9000",
  "#773F19"
] as const;

/** Normaliza un color a "#RRGGBB" en mayúsculas, o null si no es utilizable. */
export function normalizaColor(color: string | null | undefined): string | null {
  if (!color) return null;
  const limpio = color.trim().replace(/^#/, "").toUpperCase();
  return /^[0-9A-F]{6}$/.test(limpio) ? `#${limpio}` : null;
}

/**
 * El color con el que va el texto sobre un fondo dado. Es la misma regla que
 * usa la plataforma al pintar las clases en pantalla: un docente puede tener un
 * color muy claro o muy oscuro, y el texto tiene que poder leerse en los dos
 * casos.
 */
export function tintaLegible(fondo: string): "#102D41" | "#FFFFFF" {
  const hex = fondo.replace("#", "");
  const rojo = Number.parseInt(hex.slice(0, 2), 16);
  const verde = Number.parseInt(hex.slice(2, 4), 16);
  const azul = Number.parseInt(hex.slice(4, 6), 16);
  return rojo * 0.299 + verde * 0.587 + azul * 0.114 > 155 ? "#102D41" : "#FFFFFF";
}