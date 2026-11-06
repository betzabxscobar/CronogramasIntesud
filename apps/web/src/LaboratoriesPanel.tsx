import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import type { AuthenticatedUser } from "./App";

type SpaceStatus = "AVAILABLE" | "UNAVAILABLE" | "MAINTENANCE" | "INACTIVE";
type SpaceType = "CLASSROOM" | "LABORATORY";
interface Career { id: string; name: string }
interface Laboratory {
  id: string; name: string; code: string; location: string | null;
  type: SpaceType;
  peopleCapacity: number | null; studentComputers: number | null; teacherComputers: number | null;
  hasProjector: boolean; hasDigitalBoard: boolean; status: SpaceStatus; careers: Career[];
  _count: { scheduleBlocks: number; reservations: number; unavailabilities: number };
}
interface LaboratoryForm {
  type: SpaceType; name: string; location: string; peopleCapacity: string;
  studentComputers: string; teacherComputers: string; hasProjector: boolean;
  hasDigitalBoard: boolean; careerIds: string[];
}
const emptyForm: LaboratoryForm = { type: "CLASSROOM", name: "", location: "", peopleCapacity: "", studentComputers: "0", teacherComputers: "0", hasProjector: false, hasDigitalBoard: false, careerIds: [] };
const statusLabels: Record<SpaceStatus, string> = { AVAILABLE: "Disponible", UNAVAILABLE: "No disponible", MAINTENANCE: "Mantenimiento", INACTIVE: "Inactivo" };

async function apiError(response: Response) {
  try {
    const body = await response.json() as { message?: string | string[] };
    return Array.isArray(body.message) ? body.message.join(". ") : body.message ?? "No fue posible completar la operación.";
  } catch { return "No fue posible conectar con el servidor."; }
}

export function LaboratoriesPanel({ accessToken, user }: { accessToken: string; user: AuthenticatedUser }) {
  const [laboratories, setLaboratories] = useState<Laboratory[]>([]);
  const [careers, setCareers] = useState<Career[]>([]);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [type, setType] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(5);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Laboratory | null>(null);
  const [form, setForm] = useState<LaboratoryForm>(emptyForm);
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);
  const hasLoaded = useRef(false);
  const tableWrapRef = useRef<HTMLDivElement>(null);
  const authHeaders = { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" };
  const isAdmin = user.role === "ADMIN";

  const loadLaboratories = useCallback(async () => {
    if (!hasLoaded.current) setLoading(true);
    setError("");
    const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
    if (search.trim()) params.set("search", search.trim());
    if (status) params.set("status", status);
    if (type) params.set("type", type);
    try {
      const response = await fetch(`/api/v1/laboratories?${params}`, { headers: { Authorization: `Bearer ${accessToken}` }, cache: "no-store" });
      if (!response.ok) throw new Error(await apiError(response));
      const body = await response.json() as { items: Laboratory[]; pagination: { total: number; totalPages: number } };
      setLaboratories(body.items); setTotal(body.pagination.total); setTotalPages(body.pagination.totalPages);
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "No fue posible cargar los laboratorios."); }
    finally { hasLoaded.current = true; setLoading(false); }
  }, [accessToken, page, pageSize, search, status, type]);

  useEffect(() => { const timeout = window.setTimeout(() => void loadLaboratories(), 250); return () => window.clearTimeout(timeout); }, [loadLaboratories]);
  useEffect(() => {
    const container = tableWrapRef.current;
    if (!container) return;
    const observer = new ResizeObserver(([entry]) => {
      if (!entry) return;
      const availableHeight = entry.contentRect.height - 38;
      const nextSize = Math.max(5, Math.min(8, Math.floor((availableHeight + 12) / 58)));
      setPageSize(nextSize);
    });
    observer.observe(container);
    return () => observer.disconnect();
  }, []);
  useEffect(() => { setPage(1); }, [pageSize]);
  useEffect(() => {
    void fetch("/api/v1/laboratories/careers", { headers: { Authorization: `Bearer ${accessToken}` }, cache: "no-store" })
      .then(async (response) => { if (!response.ok) throw new Error(await apiError(response)); return response.json() as Promise<Career[]>; })
      .then(setCareers).catch((requestError: unknown) => setError(requestError instanceof Error ? requestError.message : "No fue posible cargar las carreras."));
  }, [accessToken]);

  function openCreate() { setEditing(null); setForm(emptyForm); setFormError(""); setDrawerOpen(true); }
  function openEdit(lab: Laboratory) {
    setEditing(lab); setForm({ type: lab.type, name: lab.name, location: lab.location ?? "", peopleCapacity: String(lab.peopleCapacity ?? ""), studentComputers: String(lab.studentComputers ?? 0), teacherComputers: String(lab.teacherComputers ?? 0), hasProjector: lab.hasProjector, hasDigitalBoard: lab.hasDigitalBoard, careerIds: lab.careers.map(({ id }) => id) });
    setFormError(""); setDrawerOpen(true);
  }
  function updateField<K extends keyof LaboratoryForm>(field: K, value: LaboratoryForm[K]) { setForm((current) => ({ ...current, [field]: value })); }
  function toggleCareer(id: string) { setForm((current) => ({ ...current, careerIds: current.careerIds.includes(id) ? current.careerIds.filter((careerId) => careerId !== id) : [...current.careerIds, id] })); }

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!form.careerIds.length) { setFormError("Selecciona al menos una carrera autorizada."); return; }
    setSaving(true); setFormError("");
    try {
      const response = await fetch(editing ? `/api/v1/laboratories/${editing.id}` : "/api/v1/laboratories", { method: editing ? "PATCH" : "POST", headers: authHeaders, body: JSON.stringify({ ...form, peopleCapacity: form.peopleCapacity ? Number(form.peopleCapacity) : undefined, studentComputers: Number(form.studentComputers), teacherComputers: Number(form.teacherComputers), location: form.location || undefined }) });
      if (!response.ok) throw new Error(await apiError(response));
      setDrawerOpen(false); await loadLaboratories();
    } catch (requestError) { setFormError(requestError instanceof Error ? requestError.message : "No fue posible guardar el laboratorio."); }
    finally { setSaving(false); }
  }

  async function changeStatus(lab: Laboratory, nextStatus: SpaceStatus) {
    setError("");
    try { const response = await fetch(`/api/v1/laboratories/${lab.id}/status`, { method: "PATCH", headers: authHeaders, body: JSON.stringify({ status: nextStatus }) }); if (!response.ok) throw new Error(await apiError(response)); await loadLaboratories(); }
    catch (requestError) { setError(requestError instanceof Error ? requestError.message : "No fue posible cambiar el estado."); }
  }

  async function remove(lab: Laboratory) {
    if (!window.confirm(`¿Eliminar definitivamente ${lab.type === "CLASSROOM" ? "el aula" : "el laboratorio"} “${lab.name}”?`)) return;
    setError("");
    try { const response = await fetch(`/api/v1/laboratories/${lab.id}`, { method: "DELETE", headers: { Authorization: `Bearer ${accessToken}` } }); if (!response.ok) throw new Error(await apiError(response)); if (laboratories.length === 1 && page > 1) setPage((value) => value - 1); else await loadLaboratories(); }
    catch (requestError) { setError(requestError instanceof Error ? requestError.message : "No fue posible eliminar el laboratorio."); }
  }

  return <section className="laboratories-panel">
    <header className="module-toolbar"><div><p>Espacios institucionales</p><h2>Aulas y laboratorios</h2><span>{total} espacios registrados</span></div>{isAdmin && <button className="create-button" type="button" onClick={openCreate}><span>+</span> Nuevo espacio</button>}</header>
    <div className="laboratory-filters"><label><span>Buscar</span><input value={search} onChange={(event) => { setSearch(event.target.value); setPage(1); }} placeholder="Nombre o ubicación" /></label><label><span>Tipo</span><select value={type} onChange={(event) => { setType(event.target.value); setPage(1); }}><option value="">Aulas y laboratorios</option><option value="CLASSROOM">Aulas</option><option value="LABORATORY">Laboratorios</option></select></label><label><span>Estado</span><select value={status} onChange={(event) => { setStatus(event.target.value); setPage(1); }}><option value="">Todos los estados</option>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label></div>
    {error && <p className="panel-error">{error}</p>}
    <div className="laboratories-table-wrap" ref={tableWrapRef}><table className="laboratories-table"><thead><tr><th>Espacio</th><th>Capacidad</th><th>Equipamiento</th><th>Estado</th>{isAdmin && <th aria-label="Acciones" />}</tr></thead><tbody>
      {loading ? <tr><td colSpan={isAdmin ? 5 : 4} className="table-message">Cargando espacios…</td></tr> : laboratories.length === 0 ? <tr><td colSpan={isAdmin ? 5 : 4} className="table-message">No existen espacios con estos filtros.</td></tr> : laboratories.map((lab) => <tr key={lab.id}>
        <td><div className="laboratory-name"><span>{lab.type === "CLASSROOM" ? "AULA" : "LAB"}</span><div><strong>{lab.name}</strong><small>{lab.location ?? "Sin ubicación"}</small></div></div></td>
        <td><strong>{lab.peopleCapacity ? `${lab.peopleCapacity} personas` : "Por definir"}</strong><small>{lab.type === "LABORATORY" ? `${lab.studentComputers ?? 0} equipos para estudiantes · ${lab.teacherComputers ?? 0} docente` : "Aula académica"}</small></td>
        <td><div className="equipment-tags">{lab.hasProjector && <span>Proyector</span>}{lab.hasDigitalBoard && <span>Pizarra digital</span>}{!lab.hasProjector && !lab.hasDigitalBoard && <em>Equipamiento básico</em>}</div></td>
        <td><span className={`space-status ${lab.status.toLowerCase()}`}>{statusLabels[lab.status]}</span></td>
        {isAdmin && <td><div className="row-actions laboratory-actions"><button type="button" onClick={() => openEdit(lab)}>Editar</button><select aria-label={`Estado de ${lab.name}`} value={lab.status} onChange={(event) => void changeStatus(lab, event.target.value as SpaceStatus)}>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><button className="delete-row-button icon-delete-button" type="button" onClick={() => void remove(lab)} title="Eliminar espacio" aria-label={`Eliminar ${lab.name}`}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3m3 0-1 13H7L6 7m4 4v5m4-5v5" /></svg></button></div></td>}
      </tr>)}</tbody></table></div>
    <footer className="table-pagination"><span>Página {page} de {totalPages}</span><div><button type="button" disabled={page <= 1} onClick={() => setPage((value) => value - 1)}>Anterior</button><button type="button" disabled={page >= totalPages} onClick={() => setPage((value) => value + 1)}>Siguiente</button></div></footer>
    {drawerOpen && <div className="drawer-backdrop" role="presentation"><aside className="laboratory-drawer"><header><div><p>{editing ? "Editar registro" : "Nuevo registro"}</p><h2>{editing ? "Editar espacio" : "Registrar espacio"}</h2></div><button type="button" onClick={() => setDrawerOpen(false)} aria-label="Cerrar formulario">×</button></header><form onSubmit={(event) => void save(event)}><div className="laboratory-form-grid">
      <label><span>Tipo de espacio</span><select value={form.type} onChange={(event) => updateField("type", event.target.value as SpaceType)}><option value="CLASSROOM">Aula</option><option value="LABORATORY">Laboratorio</option></select></label><label><span>Nombre</span><input required minLength={2} maxLength={120} value={form.name} onChange={(event) => updateField("name", event.target.value)} /></label>
      <label className="wide"><span>Ubicación</span><input maxLength={180} value={form.location} onChange={(event) => updateField("location", event.target.value)} placeholder="Edificio, piso o aula" /></label><label><span>Capacidad de personas (opcional)</span><input type="number" min="1" max="1000" value={form.peopleCapacity} onChange={(event) => updateField("peopleCapacity", event.target.value)} /></label>{form.type === "LABORATORY" && <><label><span>Equipos estudiantes</span><input type="number" min="0" max="1000" value={form.studentComputers} onChange={(event) => updateField("studentComputers", event.target.value)} /></label><label><span>Equipos docentes</span><input type="number" min="0" max="100" value={form.teacherComputers} onChange={(event) => updateField("teacherComputers", event.target.value)} /></label></>}
    </div>{form.type === "LABORATORY" && <div className="equipment-options"><label><input type="checkbox" checked={form.hasProjector} onChange={(event) => updateField("hasProjector", event.target.checked)} /> Proyector</label><label><input type="checkbox" checked={form.hasDigitalBoard} onChange={(event) => updateField("hasDigitalBoard", event.target.checked)} /> Pizarra digital</label></div>}<fieldset><legend>Carreras autorizadas</legend><p>El espacio estará disponible únicamente para las carreras seleccionadas.</p><div className="laboratory-career-options">{careers.map((career) => <label key={career.id}><input type="checkbox" checked={form.careerIds.includes(career.id)} onChange={() => toggleCareer(career.id)} /><span>{career.name}</span></label>)}</div></fieldset>{formError && <p className="form-error">{formError}</p>}<footer><button className="cancel-button" type="button" onClick={() => setDrawerOpen(false)}>Cancelar</button><button className="save-button" type="submit" disabled={saving}>{saving ? "Guardando…" : editing ? "Guardar cambios" : "Registrar"}</button></footer></form></aside></div>}
  </section>;
}
