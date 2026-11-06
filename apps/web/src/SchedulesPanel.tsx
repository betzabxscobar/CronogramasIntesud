import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import type { AuthenticatedUser } from "./App";
import { ExportScheduleButton } from "./ExportScheduleButton";
import { PublishScheduleButton } from "./PublishScheduleButton";

type Shift = "DAY" | "NIGHT";
type Day =
  | "MONDAY"
  | "TUESDAY"
  | "WEDNESDAY"
  | "THURSDAY"
  | "FRIDAY"
  | "SATURDAY"
  | "SUNDAY";
interface Course {
  id: string;
  name: string;
  studentCount: number | null;
  level: {
    id: string;
    name: string;
    order: number;
    career: { id: string; name: string };
  };
}
interface Period {
  id: string;
  name: string;
  status: string;
  courses: Course[];
}
interface ScheduleItem {
  id: string;
  shift: Shift;
  course: Course;
  versions: {
    id: string;
    version: number;
    status: string;
    _count: { blocks: number };
  }[];
}
interface CatalogItem {
  id: string;
  name: string;
  [key: string]: unknown;
}
interface ProfessorItem {
  id: string;
  firstNames: string;
  lastNames: string;
  professionalTitle: string | null;
  professionalTitleAbbreviation?: string | null;
  scheduleColor: string;
}
interface Block {
  id: string;
  dayOfWeek: Day;
  startTime: string;
  endTime: string;
  moduleNumber: number | null;
  subject: CatalogItem;
  professor: ProfessorItem;
  space: (CatalogItem & { code: string; location: string | null; type: string }) | null;
}
interface ScheduleModule {
  id: string;
  moduleNumber: number;
  startDate: string;
  endDate: string;
}
interface Detail {
  id: string;
  shift: Shift;
  course: Course & { period: { id: string; name: string } };
  version: {
    id: string;
    version: number;
    status: string;
    blocks: Block[];
  } | null;
  /** Versión congelada y repartible; es la que sale al exportar. */
  publishedVersion: { version: number; publishedAt: string | null } | null;
  modules: ScheduleModule[];
  catalogs: {
    subjects: CatalogItem[];
    professors: Block["professor"][];
    spaces: (CatalogItem & {
      code: string;
      location: string | null;
      type: string;
      peopleCapacity: number | null;
    })[];
  };
}
const days: { id: Day; label: string }[] = [
  { id: "MONDAY", label: "Lunes" },
  { id: "TUESDAY", label: "Martes" },
  { id: "WEDNESDAY", label: "Miércoles" },
  { id: "THURSDAY", label: "Jueves" },
  { id: "FRIDAY", label: "Viernes" },
  { id: "SATURDAY", label: "Sábado" },
];
const daySlots = [
  ["08:00", "09:00"],
  ["09:00", "10:00"],
  ["10:00", "11:00"],
  ["11:00", "12:00"],
  ["12:00", "13:00"],
  ["13:00", "14:00"],
] as const;
const nightSlots = [
  ["18:00", "19:30"],
  ["19:30", "21:00"],
] as const;
function compactProfessor(professor: ProfessorItem) {
  const firstName = professor.firstNames.trim().split(/\s+/)[0] ?? "";
  const firstSurname = professor.lastNames.trim().split(/\s+/)[0] ?? "";
  return [professor.professionalTitleAbbreviation, firstSurname, firstName]
    .filter(Boolean)
    .join(" ");
}

function readableTextColor(background: string) {
  const hex = background.replace("#", "");
  const red = Number.parseInt(hex.slice(0, 2), 16);
  const green = Number.parseInt(hex.slice(2, 4), 16);
  const blue = Number.parseInt(hex.slice(4, 6), 16);
  return red * 0.299 + green * 0.587 + blue * 0.114 > 155
    ? "#102D41"
    : "#FFFFFF";
}

function scheduleSpaceName(space: {
  name: string;
  location: string | null;
  type: string;
}) {
  if (space.type !== "LABORATORY") return space.name;
  const location = space.location?.trim();
  const roomLetter = location?.match(/Aula\s+3-([A-Z])/i)?.[1]?.toUpperCase();
  const laboratoryNumber = roomLetter
    ? String(roomLetter.charCodeAt(0) - 64).padStart(2, "0")
    : null;
  return location && laboratoryNumber
    ? `${location} Laboratorio ${laboratoryNumber}`
    : space.name;
}
async function apiError(response: Response) {
  try {
    const body = (await response.json()) as { message?: string | string[] };
    return Array.isArray(body.message)
      ? body.message.join(". ")
      : (body.message ?? "No fue posible completar la operación.");
  } catch {
    return "No fue posible conectar con el servidor.";
  }
}

export function SchedulesPanel({
  accessToken,
  user,
}: {
  accessToken: string;
  user: AuthenticatedUser;
}) {
  const [periods, setPeriods] = useState<Period[]>([]);
  const [periodId, setPeriodId] = useState("");
  const [schedules, setSchedules] = useState<ScheduleItem[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [detail, setDetail] = useState<Detail | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [creatorOpen, setCreatorOpen] = useState(false);
  const [moduleDatesOpen, setModuleDatesOpen] = useState(false);
  const [editingBlock, setEditingBlock] = useState<Block | null | undefined>(
    undefined,
  );
  const [scheduleListCollapsed, setScheduleListCollapsed] = useState(false);
  const [copiedBlock, setCopiedBlock] = useState<Block | null>(null);
  const [galleryOpen, setGalleryOpen] = useState(false);
  const headers = useMemo(
    () => ({
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    }),
    [accessToken],
  );

  const loadDetail = useCallback(
    async (id: string) => {
      if (!id) {
        setDetail(null);
        return;
      }
      const response = await fetch(`/api/v1/schedules/${id}`, {
        headers: { Authorization: `Bearer ${accessToken}` },
        cache: "no-store",
      });
      if (!response.ok) throw new Error(await apiError(response));
      setDetail((await response.json()) as Detail);
    },
    [accessToken],
  );
  const loadSchedules = useCallback(
    async (id: string, preferred?: string) => {
      if (!id) return;
      const response = await fetch(`/api/v1/schedules?periodId=${id}`, {
        headers: { Authorization: `Bearer ${accessToken}` },
        cache: "no-store",
      });
      if (!response.ok) throw new Error(await apiError(response));
      const items = (await response.json()) as ScheduleItem[];
      setSchedules(items);
      const next =
        preferred && items.some(({ id: value }) => value === preferred)
          ? preferred
          : (items[0]?.id ?? "");
      setSelectedId(next);
      await loadDetail(next);
    },
    [accessToken, loadDetail],
  );
  useEffect(() => {
    void fetch("/api/v1/schedules/context", {
      headers: { Authorization: `Bearer ${accessToken}` },
      cache: "no-store",
    })
      .then(async (response) => {
        if (!response.ok) throw new Error(await apiError(response));
        return response.json() as Promise<Period[]>;
      })
      .then(async (items) => {
        setPeriods(items);
        const id =
          items.find(({ status }) => status === "ACTIVE")?.id ??
          items[0]?.id ??
          "";
        setPeriodId(id);
        await loadSchedules(id);
      })
      .catch((requestError: unknown) =>
        setError(
          requestError instanceof Error
            ? requestError.message
            : "No fue posible cargar los horarios.",
        ),
      )
      .finally(() => setLoading(false));
  }, [accessToken, loadSchedules]);

  useEffect(() => {
    if (!copiedBlock) return;
    function cancelCopyWithEscape(event: KeyboardEvent) {
      if (event.key === "Escape") setCopiedBlock(null);
    }
    window.addEventListener("keydown", cancelCopyWithEscape);
    return () => window.removeEventListener("keydown", cancelCopyWithEscape);
  }, [copiedBlock]);

  async function chooseSchedule(id: string) {
    setSelectedId(id);
    setCopiedBlock(null);
    setModuleDatesOpen(false);
    setLoading(true);
    try {
      await loadDetail(id);
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "No fue posible cargar el horario.",
      );
    } finally {
      setLoading(false);
    }
  }
  async function createSchedule(courseId: string, shift: Shift) {
    setError("");
    const response = await fetch("/api/v1/schedules", {
      method: "POST",
      headers,
      body: JSON.stringify({ courseId, shift }),
    });
    if (!response.ok) throw new Error(await apiError(response));
    const body = (await response.json()) as { id: string };
    setCreatorOpen(false);
    await loadSchedules(periodId, body.id);
  }
  async function saveBlock(values: object) {
    if (!detail) return false;
    setError("");
    try {
      const response = await fetch(
        editingBlock
          ? `/api/v1/schedules/${detail.id}/blocks/${editingBlock.id}`
          : `/api/v1/schedules/${detail.id}/blocks`,
        {
          method: editingBlock ? "PATCH" : "POST",
          headers,
          body: JSON.stringify(values),
        },
      );
      if (!response.ok) throw new Error(await apiError(response));
      setEditingBlock(undefined);
      await loadDetail(detail.id);
      return true;
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "No fue posible guardar el bloque.",
      );
      return false;
    }
  }
  async function removeBlock(block: Block) {
    if (
      !detail ||
      !window.confirm(`¿Eliminar la clase de “${block.subject.name}”?`)
    )
      return;
    const response = await fetch(
      `/api/v1/schedules/${detail.id}/blocks/${block.id}`,
      { method: "DELETE", headers: { Authorization: `Bearer ${accessToken}` } },
    );
    if (!response.ok) {
      setError(await apiError(response));
      return;
    }
    await loadDetail(detail.id);
  }

  async function removeSchedule(item: ScheduleItem) {
    if (!window.confirm(`¿Eliminar el borrador de ${item.course.level.career.name} - ${item.course.level.name}?`)) return;
    const response = await fetch(`/api/v1/schedules/${item.id}`, { method: "DELETE", headers: { Authorization: `Bearer ${accessToken}` } });
    if (!response.ok) { setError(await apiError(response)); return; }
    setCopiedBlock(null);
    await loadSchedules(periodId);
  }

  async function saveNightModuleDates(values: { moduleNumber: number; startDate: string; endDate: string }[]) {
    if (!detail) return false;
    setError("");
    for (const value of values) {
      const response = await fetch(`/api/v1/schedules/${detail.id}/modules/${value.moduleNumber}`, { method: "PATCH", headers, body: JSON.stringify({ startDate: value.startDate, endDate: value.endDate }) });
      if (!response.ok) { setError(await apiError(response)); return false; }
    }
    await loadDetail(detail.id);
    setModuleDatesOpen(false);
    return true;
  }

  async function pasteBlock(dayOfWeek: Day, startTime: string, endTime: string, moduleNumber?: number) {
    if (!detail || !copiedBlock) return;
    setError("");
    try {
      const response = await fetch(`/api/v1/schedules/${detail.id}/blocks`, {
        method: "POST",
        headers,
        body: JSON.stringify({
          subjectId: copiedBlock.subject.id,
          professorId: copiedBlock.professor.id,
          spaceId: copiedBlock.space?.id,
          dayOfWeek,
          startTime,
          endTime,
          moduleNumber,
        }),
      });
      if (!response.ok) throw new Error(await apiError(response));
      await loadDetail(detail.id);
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "No fue posible pegar el bloque.",
      );
    }
  }

  const activePeriod = periods.find(({ id }) => id === periodId);
  const existingKeys = new Set(
    schedules.map((item) => `${item.course.id}-${item.shift}`),
  );
  const blocks = detail?.version?.blocks ?? [];
  const hasSaturdayClasses = blocks.some(
    (block) => block.dayOfWeek === "SATURDAY",
  );
  const visibleDays = days.filter(
    (day) => day.id !== "SATURDAY" || hasSaturdayClasses,
  );
  const hasThirteenHourClasses = blocks.some(
    (block) => block.startTime >= "13:00" || block.endTime > "13:00",
  );
  const timeSlots =
    detail?.shift === "NIGHT"
      ? nightSlots
      : hasThirteenHourClasses
        ? daySlots
        : daySlots.slice(0, -1);
  return (
    <section className="schedules-panel">
      <header className="module-toolbar">
        <div>
          <p>Planificación semanal</p>
          <h2>Horarios académicos</h2>
          <span>
            {detail
              ? `${detail.course.level.career.name} · ${detail.course.level.name} · Jornada ${detail.shift === "DAY" ? "diurna" : "nocturna"}`
              : user.role === "ADMIN"
                ? "Administración institucional"
                : "Carreras asignadas"}
          </span>
        </div>
        <div className="schedule-toolbar-actions">
          <label>
            <span>Periodo</span>
            <select
              value={periodId}
              onChange={(event) => {
                const id = event.target.value;
                setPeriodId(id);
                void loadSchedules(id);
              }}
            >
              {periods.map((period) => (
                <option key={period.id} value={period.id}>
                  {period.name}
                </option>
              ))}
            </select>
          </label>
          {copiedBlock && (
            <button
              className="cancel-header-copy"
              type="button"
              onClick={() => setCopiedBlock(null)}
              title={`Cancelar copia de ${copiedBlock.subject.name}`}
            >
              Cancelar copia
            </button>
          )}
          {detail && (
            <>
            {detail.shift === "NIGHT" && <button className="edit-dates-button" type="button" onClick={() => setModuleDatesOpen(true)}>Editar fechas</button>}
            <PublishScheduleButton
              accessToken={accessToken}
              scheduleId={detail.id}
              publishedVersion={detail.publishedVersion?.version ?? null}
              onPublished={() => void loadDetail(detail.id)}
            />
            <ExportScheduleButton
              accessToken={accessToken}
              periodId={periodId}
              periodName={activePeriod?.name ?? ""}
              careerId={detail.course.level.career.id}
              careerName={detail.course.level.career.name}
              hayPublicado={Boolean(detail.publishedVersion)}
            />
            <button
              className="add-class-button"
              type="button"
              onClick={() => setEditingBlock(null)}
            >
              <span>+</span> Agregar clase
            </button>
            </>
          )}
          {!!schedules.length && (
            <button
              className={`schedule-gallery-toggle${galleryOpen ? " active" : ""}`}
              type="button"
              onClick={() => setGalleryOpen((current) => !current)}
            >
              {galleryOpen ? "Cerrar galería" : "Galería de horarios"}
            </button>
          )}
          <button
            className="create-button"
            type="button"
            disabled={!activePeriod?.courses.length}
            onClick={() => setCreatorOpen(true)}
          >
            <span>+</span> Nuevo horario
          </button>
        </div>
      </header>
      {error && <p className="panel-error schedule-error">{error}</p>}
      <div
        className={`schedule-layout${scheduleListCollapsed ? " list-collapsed" : ""}`}
      >
        <aside className="schedule-list">
          <header>
            <div>
              <p>Horarios del periodo</p>
              <strong>{schedules.length} registrados</strong>
            </div>
            <button
              className="schedule-list-toggle"
              type="button"
              onClick={() => setScheduleListCollapsed((current) => !current)}
              aria-label={
                scheduleListCollapsed
                  ? "Desplegar horarios"
                  : "Contraer horarios"
              }
              title={
                scheduleListCollapsed
                  ? "Desplegar horarios"
                  : "Contraer horarios"
              }
            >
              {scheduleListCollapsed ? "›" : "‹"}
            </button>
          </header>
          <div>
            {schedules.map((item) => (
              <div className="schedule-list-item" key={item.id}>
              <button
                className={`schedule-select-button${item.id === selectedId ? " active" : ""}`}
                type="button"
                onClick={() => void chooseSchedule(item.id)}
              >
                <span>{item.shift === "DAY" ? "D" : "N"}</span>
                <div>
                  <strong>{item.course.level.career.name}</strong>
                  <small>
                    {item.course.level.name} ·{" "}
                    {item.shift === "DAY" ? "Diurna" : "Nocturna"}
                  </small>
                </div>
                <em>{item.versions[0]?._count.blocks ?? 0} bloques</em>
              </button>
              {item.versions[0]?.status === "DRAFT" && <button className="schedule-delete-button" type="button" title="Eliminar borrador" aria-label={`Eliminar borrador de ${item.course.level.career.name}, ${item.course.level.name}`} onClick={() => void removeSchedule(item)}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3m3 0-1 13H7L6 7m4 4v5m4-5v5" /></svg></button>}
              </div>
            ))}
            {!schedules.length && (
              <p className="schedule-empty">
                Crea el primer horario del periodo.
              </p>
            )}
          </div>
        </aside>
        <section className={`weekly-planner${detail?.shift === "NIGHT" ? " night-planner" : ""}`}>
          {galleryOpen && (
            <ScheduleGallery
              schedules={schedules}
              selectedId={selectedId}
              onSelect={(id) => void chooseSchedule(id)}
              onClose={() => setGalleryOpen(false)}
            />
          )}
          {detail ? (
            <>
              {detail.shift === "NIGHT" ? (
                <NightScheduleGrid
                  detail={detail}
                  copiedBlock={copiedBlock}
                  onCopy={setCopiedBlock}
                  onEdit={setEditingBlock}
                  onRemove={removeBlock}
                  onPaste={(moduleNumber, start, end) => pasteBlock("MONDAY", start, end, moduleNumber)}
                />
              ) : <div
                className="hour-grid"
                style={{
                  gridTemplateColumns: `74px repeat(${visibleDays.length}, minmax(0, 1fr))`,
                  gridTemplateRows: `38px repeat(${timeSlots.length}, minmax(0, 1fr))`,
                }}
              >
                <div className="hour-grid-header time-heading">Horario</div>
                {visibleDays.map((day) => (
                  <div className="hour-grid-header" key={day.id}>
                    {day.label}
                  </div>
                ))}
                {timeSlots.map(([start, end], slotIndex) => (
                  <div
                    className="hour-grid-row"
                    key={start}
                    style={{
                      gridTemplateColumns: `74px repeat(${visibleDays.length}, minmax(0, 1fr))`,
                    }}
                  >
                    <div className="time-cell">
                      <strong>{start} -</strong>
                      <strong>{end} h</strong>
                    </div>
                    {visibleDays.map((day) => {
                      const blocks =
                        detail.version?.blocks.filter(
                          (block) =>
                            block.dayOfWeek === day.id &&
                            block.startTime === start,
                        ) ?? [];
                      return (
                        <div
                          className={`class-cell${copiedBlock && !blocks.length ? " paste-ready" : ""}`}
                          key={day.id}
                        >
                          {blocks.map((block) => {
                            const endSlotIndex = timeSlots.findIndex(
                              ([, slotEnd]) => slotEnd === block.endTime,
                            );
                            const span = Math.max(
                              1,
                              endSlotIndex >= slotIndex
                                ? endSlotIndex - slotIndex + 1
                                : 1,
                            );
                            return (
                            <article
                              className={span > 1 ? "spanning-block" : undefined}
                              key={block.id}
                              style={{
                                backgroundColor: block.professor.scheduleColor,
                                color: readableTextColor(block.professor.scheduleColor),
                                ...(span > 1
                                  ? { height: `calc(${span * 100}% + ${span - 1}px)` }
                                  : {}),
                              }}
                            >
                              <strong>{block.subject.name}</strong>
                              <span>{compactProfessor(block.professor)}</span>
                              <small>
                                {block.space ? scheduleSpaceName(block.space) : ""}
                              </small>
                              <div>
                                <button
                                  className={
                                    copiedBlock?.id === block.id
                                      ? "cancel-copy-button"
                                      : undefined
                                  }
                                  type="button"
                                  onClick={() =>
                                    setCopiedBlock((current) =>
                                      current?.id === block.id ? null : block,
                                    )
                                  }
                                >
                                  {copiedBlock?.id === block.id
                                    ? "Cancelar"
                                    : "Copiar"}
                                </button>
                                <button
                                  type="button"
                                  onClick={() => setEditingBlock(block)}
                                >
                                  Editar
                                </button>
                                <button
                                  className="delete-block-button"
                                  type="button"
                                  onClick={() => void removeBlock(block)}
                                  title="Eliminar clase"
                                  aria-label={`Eliminar ${block.subject.name}`}
                                >
                                  <svg viewBox="0 0 24 24" aria-hidden="true">
                                    <path d="M7 7l10 10M17 7 7 17" />
                                  </svg>
                                </button>
                              </div>
                            </article>
                            );
                          })}
                          {copiedBlock && !blocks.length && (
                            <button
                              className="paste-block-button"
                              type="button"
                              onClick={() => void pasteBlock(day.id, start, end)}
                              title={`Pegar ${copiedBlock.subject.name} el ${day.label} de ${start} a ${end}`}
                            >
                              + Pegar aquí
                            </button>
                          )}
                        </div>
                      );
                    })}
                  </div>
                ))}
              </div>}
            </>
          ) : (
            <div className="schedule-empty">
              {loading ? "Cargando horario…" : "Selecciona o crea un horario."}
            </div>
          )}
        </section>
      </div>
      {creatorOpen && activePeriod && (
        <ScheduleCreator
          courses={activePeriod.courses}
          existingKeys={existingKeys}
          onClose={() => setCreatorOpen(false)}
          onSave={createSchedule}
        />
      )}
      {editingBlock !== undefined && detail && (
        <BlockEditor
          block={editingBlock ?? undefined}
          detail={detail}
          onClose={() => setEditingBlock(undefined)}
          onSave={saveBlock}
        />
      )}
      {moduleDatesOpen && detail?.shift === "NIGHT" && <NightModuleDatesEditor modules={detail.modules} onClose={() => setModuleDatesOpen(false)} onSave={saveNightModuleDates} />}
    </section>
  );
}

function ScheduleGallery({ schedules, selectedId, onSelect, onClose }: { schedules: ScheduleItem[]; selectedId: string; onSelect: (id: string) => void; onClose: () => void }) {
  const currentIndex = Math.max(0, schedules.findIndex((item) => item.id === selectedId));
  const current = schedules[currentIndex];
  const move = (direction: number) => { const next = (currentIndex + direction + schedules.length) % schedules.length; const item = schedules[next]; if (item) onSelect(item.id); };
  if (!current) return null;
  return <aside className="schedule-gallery compact" aria-label="Galería de horarios"><button className="gallery-arrow" type="button" onClick={() => move(-1)} aria-label="Horario anterior">‹</button><div className="gallery-current"><span>{current.shift === "DAY" ? "D" : "N"}</span><div><small>Horario {currentIndex + 1} de {schedules.length}</small><strong>{current.course.level.career.name}</strong><em>{current.course.level.name} · {current.shift === "DAY" ? "Diurna" : "Nocturna"}</em></div></div><button className="gallery-arrow" type="button" onClick={() => move(1)} aria-label="Horario siguiente">›</button><button className="gallery-close" type="button" onClick={onClose} aria-label="Cerrar galería">×</button></aside>;
}

function nightDateLabel(value: string) {
  const date = new Date(value);
  const months = ["ene.", "feb.", "mar.", "abr.", "may.", "jun.", "jul.", "ago.", "sep.", "oct.", "nov.", "dic."];
  return `${date.getUTCDate()} ${months[date.getUTCMonth()]}`;
}

function nightLevelLabel(value: string) {
  const level = value.replace(/\s+nivel$/i, "").trim().toLowerCase();
  const ordinals: Record<string, string> = {
    primer: "PRIMERO",
    primero: "PRIMERO",
    segundo: "SEGUNDO",
    tercer: "TERCERO",
    tercero: "TERCERO",
    cuarto: "CUARTO",
    quinto: "QUINTO",
    sexto: "SEXTO",
  };
  return ordinals[level] ?? level.toUpperCase();
}

function NightModuleDatesEditor({ modules, onClose, onSave }: {
  modules: ScheduleModule[];
  onClose: () => void;
  onSave: (values: { moduleNumber: number; startDate: string; endDate: string }[]) => Promise<boolean>;
}) {
  const [values, setValues] = useState(() => modules.map((module) => ({ moduleNumber: module.moduleNumber, startDate: module.startDate.slice(0, 10), endDate: module.endDate.slice(0, 10) })));
  const [saving, setSaving] = useState(false);
  function update(moduleNumber: number, field: "startDate" | "endDate", value: string) { setValues((current) => current.map((module) => module.moduleNumber === moduleNumber ? { ...module, [field]: value } : module)); }
  async function submit(event: FormEvent) { event.preventDefault(); setSaving(true); await onSave(values); setSaving(false); }
  return <div className="drawer-backdrop"><aside className="schedule-drawer module-dates-drawer"><header><div><p>Calendario nocturno</p><h2>Editar fechas</h2></div><button type="button" onClick={onClose} aria-label="Cerrar edición de fechas">×</button></header><form onSubmit={(event) => void submit(event)}><p className="module-dates-help">Configura el rango de tres semanas correspondiente a cada módulo.</p><div className="module-dates-list">{values.map((module) => <section key={module.moduleNumber}><strong>Módulo {module.moduleNumber}</strong><small>Semanas {(module.moduleNumber - 1) * 3 + 1} a {module.moduleNumber * 3}</small><label><span>Fecha inicial</span><input required type="date" value={module.startDate} onChange={(event) => update(module.moduleNumber, "startDate", event.target.value)} /></label><label><span>Fecha final</span><input required type="date" min={module.startDate} value={module.endDate} onChange={(event) => update(module.moduleNumber, "endDate", event.target.value)} /></label></section>)}</div><footer><button className="cancel-button" type="button" onClick={onClose}>Cancelar</button><button className="save-button" type="submit" disabled={saving}>{saving ? "Guardando…" : "Guardar fechas"}</button></footer></form></aside></div>;
}

function NightScheduleGrid({ detail, copiedBlock, onCopy, onEdit, onRemove, onPaste }: {
  detail: Detail;
  copiedBlock: Block | null;
  onCopy: (block: Block | null) => void;
  onEdit: (block: Block) => void;
  onRemove: (block: Block) => Promise<void>;
  onPaste: (moduleNumber: number, start: string, end: string) => Promise<void>;
}) {
  const levelName = nightLevelLabel(detail.course.level.name);
  return <div className="night-excel-sheet">
    <div className="night-sheet-meta"><strong>Carrera:</strong><span>{detail.course.level.career.name.toUpperCase()}</span><b>Sección Nocturna</b></div>
    <div className="night-level-title">{levelName}</div>
    <div className="night-excel-grid">
      <div className="night-weeks-label" style={{ gridColumn: 1, gridRow: "1 / 3" }}>SEMANAS:</div>
      {detail.modules.map((module, index) => <div className={`night-date-range night-tone-${module.moduleNumber}`} key={`date-${module.id}`} style={{ gridColumn: `${index * 3 + 2} / span 3`, gridRow: 1 }}>{nightDateLabel(module.startDate)} - {nightDateLabel(module.endDate)}</div>)}
      <div className="night-support-date" style={{ gridColumn: 20, gridRow: 1 }}>14 sep.<br />18 sep.</div>
      {Array.from({ length: 18 }, (_, index) => <div className={`night-week-number night-tone-${Math.floor(index / 3) + 1}`} key={index} style={{ gridColumn: index + 2, gridRow: 2 }}>{index + 1}</div>)}
      <div className="night-support-dash" style={{ gridColumn: 20, gridRow: 2 }}>-</div>
      <div className="night-modules-label" style={{ gridColumn: 1, gridRow: 3 }}>MÓDULOS:</div>
      {detail.modules.map((module, index) => <div className={`night-module-number night-tone-${module.moduleNumber}`} key={`module-${module.id}`} style={{ gridColumn: `${index * 3 + 2} / span 3`, gridRow: 3 }}>{module.moduleNumber}</div>)}
      <div className="night-support-dash" style={{ gridColumn: 20, gridRow: 3 }}>-</div>
      {nightSlots.map(([start, end], rowIndex) => <div className="night-time-row" key={start} style={{ display: "contents" }}>
        <div className="night-time-cell" style={{ gridColumn: 1, gridRow: rowIndex + 4 }}>{start} - {end}</div>
        {detail.modules.map((module, index) => {
          const blocks = detail.version?.blocks.filter((block) => block.moduleNumber === module.moduleNumber && block.startTime === start) ?? [];
          return <div className={`night-class-cell class-cell${copiedBlock && !blocks.length ? " paste-ready" : ""}`} key={`${module.id}-${start}`} style={{ gridColumn: `${index * 3 + 2} / span 3`, gridRow: rowIndex + 4 }}>{blocks.map((block) => <article key={block.id} style={{ backgroundColor: block.professor.scheduleColor, color: readableTextColor(block.professor.scheduleColor) }}><strong>{block.subject.name}</strong><span>{compactProfessor(block.professor)}</span><small>{block.space ? scheduleSpaceName(block.space) : ""}</small><div><button className={copiedBlock?.id === block.id ? "cancel-copy-button" : undefined} type="button" onClick={() => onCopy(copiedBlock?.id === block.id ? null : block)}>{copiedBlock?.id === block.id ? "Cancelar" : "Copiar"}</button><button type="button" onClick={() => onEdit(block)}>Editar</button><button className="delete-block-button" type="button" onClick={() => void onRemove(block)} title="Eliminar clase" aria-label={`Eliminar ${block.subject.name}`}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 7l10 10M17 7 7 17" /></svg></button></div></article>)}{copiedBlock && !blocks.length && <button className="paste-block-button" type="button" onClick={() => void onPaste(module.moduleNumber, start, end)}>+ Pegar aquí</button>}</div>;
        })}
      </div>)}
      <div className="night-support-note" style={{ gridColumn: 20, gridRow: "4 / 6" }}>Notas<br />Supletorios</div>
    </div>
  </div>;
}

function ScheduleCreator({
  courses,
  existingKeys,
  onClose,
  onSave,
}: {
  courses: Course[];
  existingKeys: Set<string>;
  onClose: () => void;
  onSave: (courseId: string, shift: Shift) => Promise<void>;
}) {
  const [courseId, setCourseId] = useState(courses[0]?.id ?? "");
  const [shift, setShift] = useState<Shift>("DAY");
  const [saving, setSaving] = useState(false);
  const exists = existingKeys.has(`${courseId}-${shift}`);
  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    await onSave(courseId, shift);
    setSaving(false);
  }
  return (
    <div className="drawer-backdrop">
      <aside className="schedule-drawer">
        <header>
          <div>
            <p>Nuevo borrador</p>
            <h2>Crear horario</h2>
          </div>
          <button type="button" onClick={onClose}>
            ×
          </button>
        </header>
        <form onSubmit={(event) => void submit(event)}>
          <label>
            <span>Curso</span>
            <select
              value={courseId}
              onChange={(event) => setCourseId(event.target.value)}
            >
              {courses.map((course) => (
                <option key={course.id} value={course.id}>
                  {course.level.career.name} · {course.level.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span>Jornada</span>
            <select
              value={shift}
              onChange={(event) => setShift(event.target.value as Shift)}
            >
              <option value="DAY">Diurna</option>
              <option value="NIGHT">Nocturna</option>
            </select>
          </label>
          {exists && (
            <p className="form-note">
              Este horario ya existe. Al continuar se abrirá su borrador.
            </p>
          )}
          <footer>
            <button className="cancel-button" type="button" onClick={onClose}>
              Cancelar
            </button>
            <button className="save-button" type="submit" disabled={saving}>
              {saving
                ? "Abriendo…"
                : exists
                  ? "Abrir borrador"
                  : "Crear borrador"}
            </button>
          </footer>
        </form>
      </aside>
    </div>
  );
}

function BlockEditor({
  block,
  detail,
  onClose,
  onSave,
}: {
  block?: Block;
  detail: Detail;
  onClose: () => void;
  onSave: (values: object) => Promise<boolean>;
}) {
  const [form, setForm] = useState({
    subjectId: block?.subject.id ?? detail.catalogs.subjects[0]?.id ?? "",
    professorId: block?.professor.id ?? detail.catalogs.professors[0]?.id ?? "",
    spaceId: block?.space?.id ?? detail.catalogs.spaces[0]?.id ?? "",
    dayOfWeek: block?.dayOfWeek ?? ("MONDAY" as Day),
    moduleNumber: block?.moduleNumber ? String(block.moduleNumber) : "1",
    startTime: block?.startTime ?? (detail.shift === "DAY" ? "08:00" : "18:00"),
    endTime: block?.endTime ?? (detail.shift === "DAY" ? "09:00" : "19:30"),
  });
  const [saving, setSaving] = useState(false);
  function field(name: string, value: string) {
    setForm((current) => ({ ...current, [name]: value }));
  }
  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    await onSave({ ...form, spaceId: detail.shift === "NIGHT" ? undefined : form.spaceId, moduleNumber: detail.shift === "NIGHT" ? Number(form.moduleNumber) : undefined });
    setSaving(false);
  }
  return (
    <div className="drawer-backdrop">
      <aside className="schedule-drawer block-drawer">
        <header>
          <div>
            <p>{block ? "Editar bloque" : "Nueva clase"}</p>
            <h2>{block ? "Editar clase" : "Agregar clase"}</h2>
          </div>
          <button type="button" onClick={onClose}>
            ×
          </button>
        </header>
        <form onSubmit={(event) => void submit(event)}>
          <label>
            <span>Materia</span>
            <select
              required
              value={form.subjectId}
              onChange={(event) => field("subjectId", event.target.value)}
            >
              {detail.catalogs.subjects.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span>Profesor</span>
            <select
              required
              value={form.professorId}
              onChange={(event) => field("professorId", event.target.value)}
            >
              {detail.catalogs.professors.map((item) => (
                <option key={item.id} value={item.id}>
                  {compactProfessor(item)}
                </option>
              ))}
            </select>
          </label>
          {detail.shift === "DAY" && <label>
            <span>Aula o laboratorio</span>
            <select
              required
              value={form.spaceId}
              onChange={(event) => field("spaceId", event.target.value)}
            >
              {detail.catalogs.spaces.map((item) => (
                <option key={item.id} value={item.id}>
                  {scheduleSpaceName(item)}
                </option>
              ))}
            </select>
          </label>}
          {detail.shift === "NIGHT" ? <label><span>Módulo</span><select value={form.moduleNumber} onChange={(event) => field("moduleNumber", event.target.value)}>{detail.modules.map((module) => <option key={module.id} value={module.moduleNumber}>Módulo {module.moduleNumber}</option>)}</select></label> : <label>
              <span>Día</span>
              <select value={form.dayOfWeek} onChange={(event) => field("dayOfWeek", event.target.value)}>{days.map((day) => <option key={day.id} value={day.id}>{day.label}</option>)}</select>
            </label>}
          <div className="block-form-row">
            <label>
              <span>Inicio</span>
              <input
                required
                type="time"
                value={form.startTime}
                onChange={(event) => field("startTime", event.target.value)}
              />
            </label>
            <label>
              <span>Finalización</span>
              <input
                required
                type="time"
                value={form.endTime}
                onChange={(event) => field("endTime", event.target.value)}
              />
            </label>
          </div>
          <footer>
            <button className="cancel-button" type="button" onClick={onClose}>
              Cancelar
            </button>
            <button
              className="save-button"
              type="submit"
              disabled={
                saving || !form.subjectId || !form.professorId || (detail.shift === "DAY" && !form.spaceId)
              }
            >
              {saving ? "Validando…" : "Validar y guardar"}
            </button>
          </footer>
        </form>
      </aside>
    </div>
  );
}
