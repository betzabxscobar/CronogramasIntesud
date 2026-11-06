/**
 * Generador del libro "Horarios" a partir de los datos del sistema.
 *
 * El archivo usa la misma disposición que la aplicación sabe importar, para que
 * el ciclo exportar → importar no pierda información, y reproduce el aspecto del
 * libro original para que el archivo generado se pueda repartir tal cual:
 *
 *   - Times New Roman para rótulos y encabezados, Calibri para el contenido.
 *   - Cabeceras sobre azul (#467B79) con texto blanco.
 *   - Clases sobre un verde muy claro; el horario nocturno alterna el tono de
 *     cada módulo para que se distingan de un vistazo.
 *   - Filas altas (60 puntos la materia en el diurno, 47 el nocturno) porque si
 *     no el texto de tres líneas no cabe.
 *   - En el nocturno las tres semanas de un módulo van combinadas en una sola
 *     celda, que es como se lee de verdad: el módulo dura tres semanas.
 *
 *   Inicio
 *     A1 "CARRERA" y A2 la carrera; en D los docentes, en F las aulas, en H los
 *     niveles; a partir de la columna 30, un bloque de seis columnas por carrera.
 *
 *   HorariosClasesDiurno
 *     Bloques de siete columnas: la primera es "Horas" y las cinco siguientes los
 *     días. Cada franja son tres filas (asignatura, docente, aula).
 *
 *   HorariosClasesNocturno
 *     Bloques de veintitrés columnas: la etiqueta del bloque y las dieciocho
 *     semanas agrupadas de tres en tres por módulo. Cada franja son dos filas.
 */
import type { Worksheet } from "exceljs";
import { TONO_MODULO, normalizaColor, tintaLegible } from "../imports/excel/schedule-colors";

// ------------------------------------------------------------------ tipos

export interface ExportLevel {
  order: number;
  name: string;
}

export interface ExportSubject {
  name: string;
  code: string | null;
  levelOrder: number | null;
  weeklyHours: number | null;
}

export interface ExportCatalog {
  careerName: string;
  subjects: ExportSubject[];
}

export interface ExportBlock {
  levelOrder: number | null;
  dayOfWeek: number | null;
  startTime: string;
  endTime: string;
  moduleNumber: number | null;
  subject: string;
  /**
   * Color con el que se pinta la clase. Es el del docente, el mismo que se ve
   * en la plataforma; si no viniera, la clase sale en gris.
   */
  color: string | null;
  professor: string;
  space: string | null;
}

export interface ExportModule {
  moduleNumber: number;
  startDate: Date;
  endDate: Date;
}

export interface ExportSignature {
  label: string;
  name: string | null;
}

export interface ExportModel {
  careerName: string;
  periodName: string;
  coordinator: string | null;
  signatures: ExportSignature[];
  levels: ExportLevel[];
  subjects: ExportSubject[];
  catalogs: ExportCatalog[];
  professors: string[];
  spaces: string[];
  dayBlocks: ExportBlock[];
  nightBlocks: ExportBlock[];
  nightModules: ExportModule[];
}

// ------------------------------------------------------------------ estilo

/** Rótulo y encabezado: azul del libro original sobre letra blanca. */
const ROTULO = { name: "Times New Roman", size: 12, bold: true };
const TITULO = { name: "Times New Roman", size: 18 };
const ROTULO_CHICO = { name: "Times New Roman", size: 11, bold: true };
/** Contenido de las celdas. */
const CONTENIDO = { name: "Calibri", size: 11 };
const CONTENIDO_PEQUENO = { name: "Calibri", size: 10 };
const CONTENIDO_MINI = { name: "Calibri", size: 9 };
const MINI_NEGRITA = { name: "Calibri", size: 9, bold: true };

const AZUL_ROTULO: Tinta = { argb: "FF467B79" };
const BLANCO = { theme: 0 };
const NEGRO = { theme: 1 };

/** Fondo de las cabeceras del horario nocturno. */
const NOCHE_CABECERA = { theme: 0, tint: -0.25 };

/**
 * El lateral izquierdo de las dos hojas de horario lleva el titulo "Horarios de
 * clases por niveles" escrito de punta, sobre una franja de color. Es lo primero
 * que se ve al abrir el archivo y sin el queda una rejilla sin título.
 */
const PARED_DIURNO: Tinta = { argb: "FFDAE3F3" };
const PARED_NOCTURNO: Tinta = { argb: "FFB4BBC3" };

/** Colores admitidos: por tema de Excel o por valor fijo. */
type Tinta = { argb?: string; theme?: number; tint?: number };
type Bordes = NonNullable<ReturnType<Worksheet["getCell"]>["border"]>;

/** Borde fino, como el libro original (Excel dibuja el color automático). */
const BORDE = (): Bordes => ({
  top: { style: "thin" },
  left: { style: "thin" },
  bottom: { style: "thin" },
  right: { style: "thin" }
});

type Cell = ReturnType<Worksheet["getCell"]>;

/** "#AABBCC" -> "FFAABBCC": Excel guarda el color con su canal alfa. */
function conAlfa(color: string): string {
  return color.length === 7 ? `FF${color.slice(1)}` : color;
}

function relleno(cell: Cell, color: Tinta) {
  cell.fill = {
    type: "pattern",
    pattern: "solid",
    fgColor: {
      ...(color.argb !== undefined ? { argb: color.argb } : {}),
      ...(color.theme !== undefined ? { theme: color.theme } : {}),
      ...(color.tint !== undefined ? { tint: color.tint } : {})
    }
  };
}

function rotulo(cell: Cell, opciones: { chico?: boolean; titulo?: boolean } = {}) {
  const base = opciones.titulo ? TITULO : opciones.chico ? ROTULO_CHICO : ROTULO;
  cell.font = { ...base, color: { argb: "FFFFFFFF" } };
  relleno(cell, AZUL_ROTULO);
  cell.alignment = { vertical: "middle", horizontal: "left" };
  cell.border = BORDE();
}

function rotuloPlano(cell: Cell, opciones: { chico?: boolean } = {}) {
  // En el horario diurno los rótulos son texto negro sobre blanco: el azul solo
  // se usa en la hoja Inicio. Ponerlos de color cambia la hoja de raíz.
  const base = opciones.chico ? ROTULO_CHICO : ROTULO;
  cell.font = { ...base, color: NEGRO };
  cell.alignment = { vertical: "middle", horizontal: "left" };
  cell.border = BORDE();
}

function contenido(
  cell: Cell,
  opciones: {
    fondo?: Tinta;
    fuente?: typeof CONTENIDO;
    color?: Tinta;
    centrar?: boolean;
    envolver?: boolean;
  } = {}
) {
  const tinta = opciones.color ?? NEGRO;
  cell.font = { ...(opciones.fuente ?? CONTENIDO), color: { ...tinta } };
  if (opciones.fondo !== undefined) relleno(cell, opciones.fondo);
  cell.alignment = {
    vertical: "middle",
    horizontal: opciones.centrar ? "center" : "left",
    wrapText: opciones.envolver ?? false
  };
  cell.border = BORDE();
}

// ------------------------------------------------------------------ medidas

const DAYS = [
  { label: "Lunes", day: 1 },
  { label: "Martes", day: 2 },
  { label: "Miércoles", day: 3 },
  { label: "Jueves", day: 4 },
  { label: "Viernes", day: 5 }
];

/** Anchos y altos del libro original, para que el archivo se vea igual. */
const ANCHO = {
  inicioRotulo: 39.1,
  inicioIndice: 9.7,
  inicioDocente: 39.1,
  inicioLista: 29.4,
  inicioNivel: 11.4,
  diaRotulo: 12.7,
  diaHora: 18.7,
  diaDia: 27.7,
  nocheRotulo: 8.9,
  nocheEtiqueta: 14.7,
  nocheSemana: 6.7,
  nocheSeparador: 1
};
const ALTO = {
  inicioFila: 15.8,
  diaCabecera: 21,
  diaTitulos: 18,
  diaMateria: 60,
  diaFila: 21,
  nocheFecha: 27,
  nocheSemana: 30,
  nocheModulo: 24,
  nocheMateria: 47.1,
  nocheDocente: 27
};

export function minutes(value: string): number {
  const [h = "0", m = "0"] = value.split(":");
  return Number(h) * 60 + Number(m);
}

export function clock(value: number): string {
  const h = Math.floor(value / 60);
  const m = value % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

function dateRange(from: Date, to: Date): string {
  const month = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];
  const f = `${String(from.getUTCDate()).padStart(2, "0")} ${month[from.getUTCMonth()]}.`;
  const t = `${String(to.getUTCDate()).padStart(2, "0")} ${month[to.getUTCMonth()]}.`;
  return `${f} - ${t}`;
}

function catalogTag(name: string): string {
  const words = name.split(/\s+/).filter(Boolean).map((w) => w.replace(/[^A-Za-zÁÉÍÓÚÑáéíóúñ]/g, ""));
  const initials = words.length > 1 ? words.slice(1).map((w) => w[0]!.toUpperCase()).join("") : "";
  const tag = `${words[0]?.[0]?.toUpperCase() ?? "X"}${initials}`.slice(0, 3);
  return `Asignaturas ${tag}:`;
}

function comparable(value: string | null | undefined): string {
  if (!value) return "";
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim();
}

// ------------------------------------------------------------------ Inicio

/** Columnas del marco gris: separan las listas de contenido, sin tocarlas. */
const MARCO_INICIO = [1, 2, 5, 7, 9];
const GRIS_MARCO: Tinta = { argb: "FF808080" };

export function writeInicioSheet(sheet: Worksheet, model: ExportModel) {
  sheet.getColumn(1).width = ANCHO.inicioRotulo;
  sheet.getColumn(2).width = ANCHO.inicioIndice;
  sheet.getColumn(3).width = ANCHO.inicioIndice;
  sheet.getColumn(4).width = ANCHO.inicioDocente;
  sheet.getColumn(5).width = ANCHO.inicioIndice;
  sheet.getColumn(6).width = ANCHO.inicioLista;
  sheet.getColumn(7).width = ANCHO.inicioIndice;
  sheet.getColumn(8).width = ANCHO.inicioNivel;
  sheet.getColumn(9).width = ANCHO.inicioNivel;
  for (let r = 7; r <= 12; r++) sheet.getRow(r).height = ALTO.inicioFila;

  // Marco gris de fondo: las listas de contenido quedan como huecos claros
  // sobre el gris. Se pinta primero para que las etiquetas de encima se vean.
  const alcance = Math.max(12 + model.signatures.length * 3, model.professors.length + 3, model.spaces.length + 2);
  for (const columna of MARCO_INICIO) {
    for (let fila = 1; fila <= alcance; fila++) relleno(sheet.getCell(fila, columna), GRIS_MARCO);
  }

  sheet.getCell("A1").value = "CARRERA";
  rotulo(sheet.getCell("A1"), { chico: true });

  // El valor de la carrera se deja marcado en rojo como en el original: es el
  // campo que quien llena el libro cambia.
  sheet.getCell("A2").value = model.careerName.toUpperCase();
  sheet.getCell("A2").font = { ...CONTENIDO, color: BLANCO };
  relleno(sheet.getCell("A2"), { argb: "FFFF0000" });

  const firmas: Array<{ fila: number; rotulo: string; nombre: string | null }> = [
    { fila: 12, rotulo: "Nombre del Coordinador", nombre: model.coordinator },
    ...model.signatures.map((firma, index) => ({
      fila: 15 + index * 3,
      rotulo: firma.label,
      nombre: firma.name
    }))
  ];
  for (const firma of firmas) {
    sheet.getCell(firma.fila, 1).value = firma.rotulo;
    rotulo(sheet.getCell(firma.fila, 1), { chico: true });
    sheet.getCell(firma.fila + 1, 1).value = firma.nombre ?? "";
    contenido(sheet.getCell(firma.fila + 1, 1), {
      color: firma.nombre ? { argb: "FF0070C0" } : { argb: "FF9C9C9C" }
    });
  }

  sheet.getCell("D2").value = "LISTA DE SUS PROFESORES";
  model.professors.forEach((nombre, index) => {
    const indice = sheet.getCell(index + 3, 3);
    indice.value = index + 1;
    relleno(indice, AZUL_ROTULO);
    indice.font = { ...ROTULO_CHICO, color: BLANCO };
    indice.alignment = { horizontal: "center", vertical: "middle" };
    indice.border = BORDE();
    sheet.getCell(index + 3, 4).value = nombre;
    contenido(sheet.getCell(index + 3, 4), { color: { argb: "FF0070C0" } });
  });

  sheet.getCell("F1").value = "AULAS";
  rotulo(sheet.getCell("F1"), { chico: true });
  model.spaces.forEach((nombre, index) => {
    sheet.getCell(index + 2, 6).value = nombre;
    contenido(sheet.getCell(index + 2, 6), { color: NEGRO });
  });

  sheet.getCell("H1").value = "NIVEL";
  rotulo(sheet.getCell("H1"), { chico: true });
  model.levels.forEach((level, index) => {
    sheet.getCell(index + 2, 8).value = level.name;
    contenido(sheet.getCell(index + 2, 8), { color: NEGRO });
  });

  // Catálogos: uno por carrera, seis columnas cada uno, con el nombre de la
  // carrera combinado sobre sus cuatro columnas.
  const PRIMERA = 30;
  const PASO = 6;
  model.catalogs.forEach((catalog, index) => {
    const base = PRIMERA + index * PASO;
    sheet.getColumn(base).width = 34;
    sheet.getColumn(base + 1).width = 3;
    sheet.getColumn(base + 2).width = 9;
    sheet.getColumn(base + 3).width = 15;
    sheet.getColumn(base + 4).width = 8;
    sheet.getColumn(base + 5).width = 3;

    // El catálogo va en texto plano: en el libro original el nombre de la
    // carrera y los rótulos de sus columnas no llevan color ni fondo.
    sheet.getCell(1, base).value = catalog.careerName.toUpperCase();
    contenido(sheet.getCell(1, base), { fuente: CONTENIDO });
    sheet.mergeCells(1, base, 1, base + 3);

    sheet.getCell(2, base).value = catalogTag(catalog.careerName);
    contenido(sheet.getCell(2, base), { fuente: CONTENIDO });
    sheet.getCell(2, base + 2).value = "Créditos";
    contenido(sheet.getCell(2, base + 2), { fuente: CONTENIDO });
    sheet.getCell(2, base + 3).value = "Código";
    contenido(sheet.getCell(2, base + 3), { fuente: CONTENIDO });
    sheet.getCell(2, base + 4).value = "Nivel";
    contenido(sheet.getCell(2, base + 4), { fuente: CONTENIDO });

    const subjects = [...catalog.subjects].sort(
      (a, b) => (a.levelOrder ?? 99) - (b.levelOrder ?? 99) || a.name.localeCompare(b.name, "es")
    );
    subjects.forEach((subject, row) => {
      const r = row + 4;
      sheet.getCell(r, base).value = subject.name;
      contenido(sheet.getCell(r, base), { color: { argb: "FF0070C0" } });
      sheet.getCell(r, base + 2).value = subject.weeklyHours ?? null;
      contenido(sheet.getCell(r, base + 2), { centrar: true, color: { argb: "FF0070C0" } });
      sheet.getCell(r, base + 3).value = subject.code ?? "";
      contenido(sheet.getCell(r, base + 3), { color: { argb: "FF0070C0" } });
      sheet.getCell(r, base + 4).value = subject.levelOrder ? `${subject.levelOrder}.` : "";
      contenido(sheet.getCell(r, base + 4), { centrar: true, color: { argb: "FF0070C0" } });
    });
  });

  sheet.views = [{ state: "normal" }];
}

// ------------------------------------------------------------------ diurno

function buildDayGrid(blocks: ExportBlock[]) {
  const edges = new Set<number>();
  for (const block of blocks) {
    edges.add(minutes(block.startTime));
    edges.add(minutes(block.endTime));
  }
  const marks = [...edges].sort((a, b) => a - b);
  const slots: Array<{ from: number; to: number }> = [];
  for (let i = 0; i < marks.length - 1; i++) {
    const from = marks[i]!;
    const to = marks[i + 1]!;
    if (to - from !== 60) continue;
    slots.push({ from, to });
  }

  const byLevel = new Map<string, ExportBlock>();
  const byProfessor = new Map<string, ExportBlock>();
  for (const block of blocks) {
    if (block.dayOfWeek === null) continue;
    const desde = minutes(block.startTime);
    const hasta = minutes(block.endTime);
    // Una clase larga ocupa varias franjas de la rejilla. Si solo se
    // registrara en la hora de inicio, al releer saldría más corta de lo que es.
    for (let marca = desde; marca < hasta; marca += 60) {
      if (!slots.some((s) => s.from === marca)) continue;
      byLevel.set(`${block.levelOrder}|${block.dayOfWeek}|${marca}`, block);
      byProfessor.set(`${comparable(block.professor)}|${block.dayOfWeek}|${marca}`, block);
    }
  }
  return { slots, byLevel, byProfessor };
}

export function writeDaySheet(sheet: Worksheet, model: ExportModel) {
  const { slots, byLevel, byProfessor } = buildDayGrid(model.dayBlocks);
  const PASO = 7;
  const INICIO = 5;

  sheet.getColumn(1).width = ANCHO.diaRotulo;
  sheet.getColumn(2).width = ANCHO.diaHora;

  sheet.getCell(2, 2).value = "Carrera:";
  rotuloPlano(sheet.getCell(2, 2));
  sheet.getCell(2, 3).value = model.careerName.toUpperCase();
  contenido(sheet.getCell(2, 3), { fuente: CONTENIDO });
  sheet.getCell(2, 7).value = "Sección Diurna";
  rotuloPlano(sheet.getCell(2, 7));

  const descriptores = [
    ...model.levels.map((level, index) => ({
      col: 2 + index * PASO,
      titulo: "Nivel:",
      nombre: level.name,
      buscar: (dia: number, desde: number) => byLevel.get(`${level.order}|${dia}|${desde}`),
      tieneClases: [...byLevel.keys()].some((k) => k.startsWith(`${level.order}|`))
    })),
    ...model.professors.map((nombre, index) => ({
      col: 2 + (model.levels.length + index) * PASO,
      titulo: "Profesor:",
      nombre,
      buscar: (dia: number, desde: number) => byProfessor.get(`${comparable(nombre)}|${dia}|${desde}`),
      tieneClases: [...byProfessor.keys()].some((k) => k.startsWith(`${comparable(nombre)}|`))
    }))
  ];

  for (const bloque of descriptores) {
    // Un nivel sin clases conserva su bloque con encabezados y rejilla vacía,
    // igual que el libro: si se saltara, la hoja empezaría corrida de columna
    // respecto a los demás bloques. Los bloques por docente sí se omiten.
    if (!bloque.tieneClases && bloque.titulo === "Profesor:") continue;

    sheet.getColumn(bloque.col).width = ANCHO.diaHora;
    for (let d = 1; d <= 5; d++) sheet.getColumn(bloque.col + d).width = ANCHO.diaDia;

    sheet.getCell(3, bloque.col).value = bloque.titulo;
    rotuloPlano(sheet.getCell(3, bloque.col));
    sheet.getCell(3, bloque.col + 1).value = bloque.nombre;
    rotuloPlano(sheet.getCell(3, bloque.col + 1));

    sheet.getCell(4, bloque.col).value = "Horas";
    rotuloPlano(sheet.getCell(4, bloque.col));
    DAYS.forEach((dia, index) => {
      sheet.getCell(4, bloque.col + 1 + index).value = dia.label;
      rotuloPlano(sheet.getCell(4, bloque.col + 1 + index));
    });

    slots.forEach((slot, index) => {
      const fila = INICIO + index * 3;
      sheet.getRow(fila).height = ALTO.diaMateria;
      sheet.getRow(fila + 1).height = ALTO.diaFila;
      sheet.getRow(fila + 2).height = ALTO.diaFila;

      const rango = sheet.getCell(fila, bloque.col);
      rango.value = `${clock(slot.from)}h-${clock(slot.to)}h`;
      // La hora va sobre fondo claro con texto negro, como en el libro.
      rango.font = { ...ROTULO, color: NEGRO };
      rango.alignment = { vertical: "middle", horizontal: "left" };
      rango.border = BORDE();
      relleno(rango, { theme: 0 });
      sheet.mergeCells(fila, bloque.col, fila + 2, bloque.col);

      for (const dia of DAYS) {
        const clase = bloque.buscar(dia.day, slot.from);
        // El fondo es el color del docente, el mismo que se ve en pantalla, y la
        // franja entera (materia, docente y aula) lo comparte. El texto se pone
        // claro u oscuro segun el fondo para que siempre se lea. Las franjas
        // libres se dejan sin fondo: si llevaran un gris, un docente con ese
        // mismo gris tendría sus clases invisibles.
        const hex = normalizaColor(clase?.color ?? null);
        const colorFondo: Tinta | undefined = hex ? { argb: conAlfa(hex) } : undefined;
        const tinta: Tinta = hex ? { argb: conAlfa(tintaLegible(hex)) } : { argb: "FF8A8A8A" };
        const materia = sheet.getCell(fila, bloque.col + dia.day);
        materia.value = clase?.subject ?? "";
        contenido(materia, { fondo: colorFondo, color: tinta, fuente: CONTENIDO, envolver: true });
        const docente = sheet.getCell(fila + 1, bloque.col + dia.day);
        docente.value = clase?.professor ?? "";
        contenido(docente, { fondo: colorFondo, color: tinta, fuente: CONTENIDO_PEQUENO, envolver: true });
        const aula = sheet.getCell(fila + 2, bloque.col + dia.day);
        aula.value = clase?.space ?? "";
        contenido(aula, { fondo: colorFondo, color: tinta, fuente: CONTENIDO_PEQUENO, envolver: true });
      }
    });
  }

  const firmas = INICIO + slots.length * 3 + 2;
  for (const [columna, texto] of [[2, "Elaborado por:"], [4, "Revisado por:"], [6, "Aprobado por:"]] as const) {
    sheet.getCell(firmas, columna).value = texto;
    rotuloPlano(sheet.getCell(firmas, columna), { chico: true });
  }

  // Lateral izquierdo: el título del libro, escrito de punta. Las firmas van a
  // partir de la segunda columna para no caer encima del título.
  const ultimaFila = firmas + 2;
  for (let fila = 1; fila <= ultimaFila; fila++) {
    relleno(sheet.getCell(fila, 1), PARED_DIURNO);
    sheet.getCell(fila, 1).border = BORDE();
  }
  const titulo = sheet.getCell(2, 1);
  titulo.value = "Horarios   de   clases   por   niveles";
  titulo.font = { name: "Times New Roman", size: 24, color: NEGRO };
  titulo.alignment = { horizontal: "center", vertical: "middle", textRotation: 90 };
  sheet.mergeCells(2, 1, 19, 1);

  sheet.getRow(1).height = ALTO.diaCabecera;
  sheet.getRow(2).height = ALTO.diaCabecera;
  sheet.getRow(3).height = ALTO.diaCabecera;
  sheet.getRow(4).height = ALTO.diaTitulos;
  sheet.views = [{ state: "normal" }];
}

// ------------------------------------------------------------------ nocturno

export const NIGHT_WEEK_OFFSETS = [1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 12, 13, 15, 16, 17, 18, 19, 20];
export const NIGHT_WIDTH = 23;

function buildNightSlots(blocks: ExportBlock[]): string[] {
  const seen = new Map<number, string>();
  for (const block of blocks) {
    seen.set(minutes(block.startTime), `${block.startTime.slice(0, 5)} - ${block.endTime.slice(0, 5)}`);
  }
  return [...seen.entries()].sort((a, b) => a[0] - b[0]).map(([, value]) => value);
}

export function writeNightSheet(sheet: Worksheet, model: ExportModel) {
  const slots = buildNightSlots(model.nightBlocks);
  const INICIO = 7;
  const POR_FRANJA = 2;

  const titulo = sheet.getCell("A2");
  titulo.value = "Horarios  de  clases   por   Nivel";
  titulo.font = { ...TITULO, color: NEGRO };
  relleno(titulo, { theme: 3, tint: 0.6 });
  titulo.border = BORDE();

  const bloques = [
    ...model.levels.map((level, index) => ({
      col: 2 + index * NIGHT_WIDTH,
      nombre: level.name,
      nivel: level.order,
      bloques: model.nightBlocks.filter((b) => b.levelOrder === level.order)
    })),
    ...model.professors.map((nombre, index) => ({
      col: 2 + (model.levels.length + index) * NIGHT_WIDTH,
      nombre,
      nivel: null,
      bloques: model.nightBlocks.filter((b) => comparable(b.professor) === comparable(nombre))
    }))
  ];

  bloques.forEach((bloque) => {
    // Igual que en el diurno: un nivel sin clases mantiene su bloque para que la
    // columna caiga donde corresponde; los bloques por docente vacíos se omiten.
    if (!bloque.bloques.length && bloque.nivel === null) return;
    const col = bloque.col;
    sheet.getColumn(col).width = ANCHO.nocheEtiqueta;
    for (const offset of NIGHT_WEEK_OFFSETS) sheet.getColumn(col + offset).width = ANCHO.nocheSemana;
    for (const hueco of [7, 14, 21]) sheet.getColumn(col + hueco).width = ANCHO.nocheSeparador;

    const cabecera = sheet.getCell(2, col + 1);
    cabecera.value = model.careerName.toUpperCase();
    cabecera.font = { ...CONTENIDO, size: 12, color: NEGRO };
    relleno(cabecera, NOCHE_CABECERA);
    cabecera.border = BORDE();
    const seccion = sheet.getCell(2, col + 19);
    seccion.value = "Sección Nocturna";
    seccion.font = { ...CONTENIDO, size: 12, color: NEGRO };
    relleno(seccion, NOCHE_CABECERA);
    seccion.border = BORDE();

    const nivel = sheet.getCell(3, col);
    nivel.value = bloque.nombre;
    nivel.font = { ...ROTULO, color: BLANCO };
    relleno(nivel, { theme: 0, tint: -0.25 });
    nivel.border = BORDE();

    const semanas = sheet.getCell(4, col);
    semanas.value = "SEMANAS:";
    semanas.font = { ...MINI_NEGRITA, color: BLANCO };
    relleno(semanas, NOCHE_CABECERA);
    semanas.border = BORDE();

    model.nightModules.forEach((module, index) => {
      const inicio = NIGHT_WEEK_OFFSETS[index * 3]!;
      const celda = sheet.getCell(4, col + inicio);
      celda.value = dateRange(module.startDate, module.endDate);
      celda.font = { ...MINI_NEGRITA, color: BLANCO };
      relleno(celda, { argb: conAlfa(TONO_MODULO[index % TONO_MODULO.length]!) });
      celda.border = BORDE();
      celda.alignment = { horizontal: "center", vertical: "middle" };
      // El rango de fechas cubre las tres semanas del módulo.
      sheet.mergeCells(4, col + inicio, 4, col + inicio + 2);
    });

    NIGHT_WEEK_OFFSETS.forEach((offset, indice) => {
      const celda = sheet.getCell(5, col + offset);
      celda.value = indice + 1;
      celda.font = { ...MINI_NEGRITA, color: BLANCO };
      relleno(celda, NOCHE_CABECERA);
      celda.alignment = { horizontal: "center", vertical: "middle" };
      celda.border = BORDE();
    });
    const guion = sheet.getCell(5, col + 21);
    guion.value = " --- ";
    guion.font = { ...MINI_NEGRITA, color: BLANCO };
    relleno(guion, NOCHE_CABECERA);
    guion.border = BORDE();

    const modulos = sheet.getCell(6, col);
    modulos.value = "MÓDULOS:";
    modulos.font = { ...MINI_NEGRITA, color: BLANCO };
    relleno(modulos, NOCHE_CABECERA);
    modulos.border = BORDE();

    model.nightModules.forEach((module, index) => {
      const celda = sheet.getCell(6, col + NIGHT_WEEK_OFFSETS[index * 3]!);
      celda.value = module.moduleNumber;
      // El tono por módulo solo va en la fila de fechas; el resto de la rejilla
      // es gris uniforme, como en el libro original.
      celda.font = { ...CONTENIDO_MINI, bold: true, color: BLANCO };
      relleno(celda, NOCHE_CABECERA);
      celda.alignment = { horizontal: "center", vertical: "middle" };
      celda.border = BORDE();
      sheet.mergeCells(6, col + NIGHT_WEEK_OFFSETS[index * 3]!, 6, col + NIGHT_WEEK_OFFSETS[index * 3]! + 2);
    });

    const porClave = new Map<string, ExportBlock>();
    for (const block of bloque.bloques) {
      const semana = ((block.moduleNumber ?? 1) - 1) * 3 + 1;
      porClave.set(`${minutes(block.startTime)}|${semana}`, block);
    }

    slots.forEach((slot, indice) => {
      const fila = INICIO + indice * POR_FRANJA;
      sheet.getRow(fila).height = ALTO.nocheMateria;
      sheet.getRow(fila + 1).height = ALTO.nocheDocente;

      const rango = sheet.getCell(fila, col);
      rango.value = slot;
      rango.font = { ...MINI_NEGRITA, color: BLANCO };
      relleno(rango, NOCHE_CABECERA);
      rango.border = BORDE();
      sheet.mergeCells(fila, col, fila + POR_FRANJA - 1, col);

      const desde = minutes(slot.split(" - ")[0]!.trim());
      model.nightModules.forEach((module, m) => {
        const semana = m * 3 + 1;
        const clase = porClave.get(`${desde}|${semana}`);
        const inicio = col + NIGHT_WEEK_OFFSETS[m * 3]!;
        // Mismo criterio que el horario diurno y que la pantalla: el color lo
        // pone el docente. Sin clase, la rejilla va en el gris del encabezado.
        const hex = normalizaColor(clase?.color ?? null);
        const colorFondo: Tinta = hex ? { argb: conAlfa(hex) } : NOCHE_CABECERA;
        const tinta: Tinta = hex ? { argb: conAlfa(tintaLegible(hex)) } : BLANCO;
        const materia = sheet.getCell(fila, inicio);
        materia.value = clase?.subject ?? "";
        contenido(materia, { fondo: colorFondo, color: tinta, fuente: MINI_NEGRITA, centrar: true, envolver: true });
        const docente = sheet.getCell(fila + 1, inicio);
        docente.value = clase?.professor ?? "";
        contenido(docente, { fondo: colorFondo, color: tinta, fuente: CONTENIDO_MINI, centrar: true, envolver: true });
        // Las tres semanas del módulo son una sola celda: así se lee igual que
        // en el libro, donde el módulo dura tres semanas.
        if (clase) sheet.mergeCells(fila, inicio, fila, inicio + 2);
        if (clase) sheet.mergeCells(fila + 1, inicio, fila + 1, inicio + 2);
      });

      const notas = sheet.getCell(fila, col + 21);
      notas.value = "Notas\nSupletarios";
      notas.font = { ...CONTENIDO_MINI, color: { argb: "FFA6A6A6" } };
      relleno(notas, NOCHE_CABECERA);
      notas.border = BORDE();
    });
  });

  sheet.getRow(2).height = ALTO.diaCabecera;
  sheet.getRow(3).height = ALTO.nocheModulo;
  sheet.getRow(4).height = ALTO.nocheFecha;
  sheet.getRow(5).height = ALTO.nocheSemana;
  sheet.getRow(6).height = ALTO.nocheModulo;
  sheet.getColumn(1).width = ANCHO.nocheRotulo;

  // Lateral izquierdo: mismo título que en el horario diurno.
  const firmas = INICIO + slots.length * POR_FRANJA + 2;
  const ultimaFila = firmas + 2;
  for (let fila = 1; fila <= ultimaFila; fila++) {
    relleno(sheet.getCell(fila, 1), PARED_NOCTURNO);
    sheet.getCell(fila, 1).border = BORDE();
  }
  const lateral = sheet.getCell(2, 1);
  lateral.value = "Horarios  de  clases  por  niveles";
  lateral.font = { name: "Times New Roman", size: 18, color: NEGRO };
  lateral.alignment = { horizontal: "center", vertical: "middle", textRotation: 90 };
  sheet.mergeCells(2, 1, 12, 1);

  sheet.views = [{ state: "normal" }];
}