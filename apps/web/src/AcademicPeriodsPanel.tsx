import { FormEvent, useCallback, useEffect, useState } from "react";
import type { AuthenticatedUser } from "./App";

type PeriodStatus = "DRAFT" | "ACTIVE" | "CLOSED" | "ARCHIVED";
interface Period { id: string; name: string; startDate: string; endDate: string; status: PeriodStatus; _count: { courses: number } }
interface Course { id: string; name: string; studentCount: number | null; status: "ACTIVE" | "INACTIVE"; level: { id: string; name: string; order: number; career: { id: string; name: string } }; _count: { schedules: number } }
interface PeriodDetail extends Omit<Period, "_count"> { courses: Course[]; shifts: ["DAY", "NIGHT"] }

const statusLabels: Record<PeriodStatus, string> = { DRAFT: "Borrador", ACTIVE: "Vigente", CLOSED: "Cerrado", ARCHIVED: "Archivado" };
async function apiError(response: Response) { try { const body = await response.json() as { message?: string | string[] }; return Array.isArray(body.message) ? body.message.join(". ") : body.message ?? "No fue posible completar la operación."; } catch { return "No fue posible conectar con el servidor."; } }
function dateValue(value: string) { return value.slice(0, 10); }
function displayDate(value: string) { return new Intl.DateTimeFormat("es-EC", { day: "2-digit", month: "short", year: "numeric", timeZone: "UTC" }).format(new Date(value)); }

export function AcademicPeriodsPanel({ accessToken, user }: { accessToken: string; user: AuthenticatedUser }) {
  const [periods, setPeriods] = useState<Period[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [detail, setDetail] = useState<PeriodDetail | null>(null);
  const [careerId, setCareerId] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [editor, setEditor] = useState<Period | null | undefined>(undefined);
  const headers = { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" };
  const isAdmin = user.role === "ADMIN";
  const careers = detail ? [...new Map(detail.courses.map((course) => [course.level.career.id, course.level.career])).values()] : [];
  const courses = detail?.courses.filter((course) => !careerId || course.level.career.id === careerId) ?? [];

  const loadDetail = useCallback(async (id: string) => {
    if (!id) { setDetail(null); return; }
    const response = await fetch(`/api/v1/academic-periods/${id}`, { headers: { Authorization: `Bearer ${accessToken}` }, cache: "no-store" });
    if (!response.ok) throw new Error(await apiError(response));
    setDetail(await response.json() as PeriodDetail);
  }, [accessToken]);

  const load = useCallback(async (preferredId?: string) => {
    setError("");
    try {
      const response = await fetch("/api/v1/academic-periods", { headers: { Authorization: `Bearer ${accessToken}` }, cache: "no-store" });
      if (!response.ok) throw new Error(await apiError(response));
      const items = await response.json() as Period[]; setPeriods(items);
      const id = preferredId && items.some((item) => item.id === preferredId) ? preferredId : items.find(({ status }) => status === "ACTIVE")?.id ?? items[0]?.id ?? "";
      setSelectedId(id); await loadDetail(id);
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "No fue posible cargar los periodos."); }
    finally { setLoading(false); }
  }, [accessToken, loadDetail]);

  useEffect(() => { void load(); }, [load]);

  async function request(url: string, method: string, body?: object) {
    setError("");
    try { const response = await fetch(url, { method, headers, body: body ? JSON.stringify(body) : undefined }); if (!response.ok) throw new Error(await apiError(response)); await load(selectedId); return true; }
    catch (requestError) { setError(requestError instanceof Error ? requestError.message : "No fue posible completar la operación."); return false; }
  }

  async function selectPeriod(id: string) { setSelectedId(id); setCareerId(""); setLoading(true); try { await loadDetail(id); } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "No fue posible cargar el periodo."); } finally { setLoading(false); } }

  async function removeCourse(course: Course) {
    if (!window.confirm(`¿Eliminar el curso de ${course.level.name} de “${course.level.career.name}”?`)) return;
    await request(`/api/v1/academic-periods/courses/${course.id}`, "DELETE");
  }

  return <section className="periods-panel">
    <header className="module-toolbar"><div><p>Planificación académica</p><h2>Periodos y cursos</h2><span>Un curso por nivel · jornadas diurna y nocturna</span></div>{isAdmin && <button className="create-button" type="button" onClick={() => setEditor(null)}><span>+</span> Nuevo periodo</button>}</header>
    {error && <p className="panel-error">{error}</p>}
    <div className="periods-layout">
      <aside className="period-list"><header><p>Periodos académicos</p><strong>{periods.length} registrados</strong></header><div>{periods.map((period) => <button className={period.id === selectedId ? "active" : ""} type="button" key={period.id} onClick={() => void selectPeriod(period.id)}><span className={`period-status ${period.status.toLowerCase()}`}>{statusLabels[period.status]}</span><strong>{period.name}</strong><small>{displayDate(period.startDate)} — {displayDate(period.endDate)}</small><em>{period._count.courses} cursos</em></button>)}{!periods.length && !loading && <p className="period-empty">No existen periodos.</p>}</div></aside>
      <section className="courses-pane">{detail ? <><header><div><p>Periodo seleccionado</p><h3>{detail.name}</h3><span>{displayDate(detail.startDate)} — {displayDate(detail.endDate)}</span></div><div className="period-actions">{isAdmin && <><button type="button" onClick={() => setEditor(periods.find(({ id }) => id === detail.id))}>Editar</button><button type="button" onClick={() => void request(`/api/v1/academic-periods/${detail.id}/sync-courses`, "POST")}>Sincronizar cursos</button><select aria-label="Estado del periodo" value={detail.status} onChange={(event) => void request(`/api/v1/academic-periods/${detail.id}/status`, "PATCH", { status: event.target.value })}>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></>}</div></header><div className="shift-strip"><div><span>D</span><strong>Jornada diurna</strong><small>Horarios de mañana y tarde</small></div><div><span>N</span><strong>Jornada nocturna</strong><small>Horarios por módulos</small></div><label><span>Filtrar carrera</span><select value={careerId} onChange={(event) => setCareerId(event.target.value)}><option value="">Todas las carreras</option>{careers.map((career) => <option key={career.id} value={career.id}>{career.name}</option>)}</select></label></div><div className="courses-table-wrap"><table><thead><tr><th>Carrera</th><th>Nivel</th><th>Curso</th><th>Estudiantes</th><th>Horarios</th>{isAdmin && <th aria-label="Acciones" />}</tr></thead><tbody>{courses.length ? courses.map((course) => <tr key={course.id}><td><strong>{course.level.career.name}</strong></td><td>{course.level.name}</td><td><span className="unique-course">Curso único</span></td><td><StudentCount course={course} onSave={async (studentCount) => request(`/api/v1/academic-periods/courses/${course.id}`, "PATCH", { studentCount })} /></td><td>{course._count.schedules ? `${course._count.schedules} creados` : "Sin horarios"}</td>{isAdmin && <td><button className="course-delete-button" type="button" title="Eliminar curso" aria-label={`Eliminar curso de ${course.level.name}`} onClick={() => void removeCourse(course)}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3m3 0-1 13H7L6 7m4 4v5m4-5v5" /></svg></button></td>}</tr>) : <tr><td colSpan={isAdmin ? 6 : 5} className="table-message">No existen cursos para este filtro.</td></tr>}</tbody></table></div></> : <div className="period-empty">{loading ? "Cargando periodo…" : "Selecciona o crea un periodo académico."}</div>}</section>
    </div>
    {editor !== undefined && <PeriodEditor period={editor ?? undefined} onClose={() => setEditor(undefined)} onSave={async (values) => { const ok = await request(editor ? `/api/v1/academic-periods/${editor.id}` : "/api/v1/academic-periods", editor ? "PATCH" : "POST", values); if (ok) setEditor(undefined); }} />}
  </section>;
}

function StudentCount({ course, onSave }: { course: Course; onSave: (value: number) => Promise<boolean> }) {
  const [value, setValue] = useState(course.studentCount ? String(course.studentCount) : ""); const [saving, setSaving] = useState(false);
  useEffect(() => setValue(course.studentCount ? String(course.studentCount) : ""), [course.studentCount]);
  async function save() { if (!value || Number(value) === course.studentCount) return; setSaving(true); await onSave(Number(value)); setSaving(false); }
  return <div className="student-count"><input aria-label={`Estudiantes de ${course.level.name}`} type="number" min="1" max="1000" value={value} onChange={(event) => setValue(event.target.value)} placeholder="Por definir" /><button type="button" disabled={!value || saving} onClick={() => void save()}>{saving ? "…" : "Guardar"}</button></div>;
}

function PeriodEditor({ period, onClose, onSave }: { period?: Period; onClose: () => void; onSave: (values: { name: string; startDate: string; endDate: string }) => Promise<void> }) {
  const [name, setName] = useState(period?.name ?? ""); const [startDate, setStartDate] = useState(period ? dateValue(period.startDate) : ""); const [endDate, setEndDate] = useState(period ? dateValue(period.endDate) : ""); const [saving, setSaving] = useState(false);
  async function submit(event: FormEvent) { event.preventDefault(); setSaving(true); await onSave({ name, startDate, endDate }); setSaving(false); }
  return <div className="drawer-backdrop"><aside className="period-drawer"><header><div><p>{period ? "Editar registro" : "Nuevo registro"}</p><h2>{period ? "Editar periodo" : "Registrar periodo"}</h2></div><button type="button" onClick={onClose}>×</button></header><form onSubmit={(event) => void submit(event)}><label><span>Nombre del periodo</span><input required minLength={3} maxLength={80} value={name} onChange={(event) => setName(event.target.value)} placeholder="Ej. 2026 - 2026" /></label><div><label><span>Fecha de inicio</span><input required type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} /></label><label><span>Fecha de finalización</span><input required type="date" min={startDate} value={endDate} onChange={(event) => setEndDate(event.target.value)} /></label></div><p className="form-note">Al registrar el periodo se generará un único curso para cada nivel activo.</p><footer><button className="cancel-button" type="button" onClick={onClose}>Cancelar</button><button className="save-button" type="submit" disabled={saving}>{saving ? "Guardando…" : "Guardar"}</button></footer></form></aside></div>;
}
