import { FormEvent, useCallback, useEffect, useState } from "react";
import type { AuthenticatedUser } from "./App";

type Status = "ACTIVE" | "INACTIVE";
interface Career { id: string; name: string }
interface Subject { id: string; name: string; code: string | null; weeklyHours: number | null; requiresIndividualComputer: boolean; status: Status; _count: { scheduleBlocks: number } }
interface Level { id: string; name: string; order: number; status: Status; subjects: Subject[]; _count: { subjects: number; courses: number } }
interface Structure { id: string; name: string; levels: Level[] }
type Editor = { kind: "level"; value?: Level } | { kind: "subject"; value?: Subject };

async function apiError(response: Response) {
  try { const body = await response.json() as { message?: string | string[] }; return Array.isArray(body.message) ? body.message.join(". ") : body.message ?? "No fue posible completar la operación."; }
  catch { return "No fue posible conectar con el servidor."; }
}

export function AcademicStructurePanel({ accessToken, user }: { accessToken: string; user: AuthenticatedUser }) {
  const [careers, setCareers] = useState<Career[]>([]);
  const [careerId, setCareerId] = useState("");
  const [structure, setStructure] = useState<Structure | null>(null);
  const [levelId, setLevelId] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editor, setEditor] = useState<Editor | null>(null);
  const [saving, setSaving] = useState(false);
  const headers = { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" };
  const selectedLevel = structure?.levels.find((level) => level.id === levelId) ?? null;

  const loadStructure = useCallback(async (selectedCareerId: string, preserveLevel = true) => {
    if (!selectedCareerId) { setStructure(null); setLoading(false); return; }
    setError("");
    try {
      const response = await fetch(`/api/v1/academic-structure?careerId=${selectedCareerId}`, { headers: { Authorization: `Bearer ${accessToken}` }, cache: "no-store" });
      if (!response.ok) throw new Error(await apiError(response));
      const body = await response.json() as Structure;
      setStructure(body);
      setLevelId((current) => preserveLevel && body.levels.some(({ id }) => id === current) ? current : body.levels[0]?.id ?? "");
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "No fue posible cargar la estructura académica."); }
    finally { setLoading(false); }
  }, [accessToken]);

  useEffect(() => {
    void fetch("/api/v1/academic-structure/careers", { headers: { Authorization: `Bearer ${accessToken}` }, cache: "no-store" })
      .then(async (response) => { if (!response.ok) throw new Error(await apiError(response)); return response.json() as Promise<Career[]>; })
      .then((items) => { setCareers(items); const first = items[0]?.id ?? ""; setCareerId(first); return loadStructure(first, false); })
      .catch((requestError: unknown) => { setError(requestError instanceof Error ? requestError.message : "No fue posible cargar las carreras."); setLoading(false); });
  }, [accessToken, loadStructure]);

  async function removeLevel(level: Level) {
    if (!window.confirm(`¿Eliminar el nivel “${level.name}”?`)) return;
    await request(`/api/v1/academic-structure/levels/${level.id}`, "DELETE");
  }

  async function removeSubject(subject: Subject) {
    if (!window.confirm(`¿Eliminar la materia “${subject.name}”?`)) return;
    await request(`/api/v1/academic-structure/subjects/${subject.id}`, "DELETE");
  }

  async function request(url: string, method: string, body?: object) {
    setError("");
    try {
      const response = await fetch(url, { method, headers, body: body ? JSON.stringify(body) : undefined });
      if (!response.ok) throw new Error(await apiError(response));
      setEditor(null); await loadStructure(careerId);
      return true;
    } catch (requestError) { setError(requestError instanceof Error ? requestError.message : "No fue posible completar la operación."); return false; }
  }

  return <section className="academic-structure-panel">
    <header className="module-toolbar">
      <div><p>Planificación institucional</p><h2>Estructura académica</h2><span>Organiza niveles y materias antes de crear horarios.</span></div>
      <label className="structure-career-select"><span>Carrera</span><select value={careerId} onChange={(event) => { const id = event.target.value; setCareerId(id); setLoading(true); void loadStructure(id, false); }}>{careers.map((career) => <option key={career.id} value={career.id}>{career.name}</option>)}</select></label>
    </header>
    {error && <p className="panel-error">{error}</p>}
    {loading ? <div className="structure-empty">Cargando estructura académica…</div> : !careerId ? <div className="structure-empty">No tienes carreras disponibles.</div> : <div className="structure-layout">
      <section className="levels-pane">
        <header><div><p>Niveles o semestres</p><strong>{structure?.levels.length ?? 0} registrados</strong></div><button type="button" onClick={() => setEditor({ kind: "level" })}>+ Nuevo nivel</button></header>
        <div className="level-list">{structure?.levels.length ? structure.levels.map((level) => <article className={level.id === levelId ? "active" : ""} key={level.id} onClick={() => setLevelId(level.id)}>
          <span>{String(level.order).padStart(2, "0")}</span><div><strong>{level.name}</strong><small>{level._count.subjects} materias · un curso</small></div><div className="structure-row-actions"><button type="button" title="Editar nivel" onClick={(event) => { event.stopPropagation(); setEditor({ kind: "level", value: level }); }}>Editar</button><button className="trash-action" type="button" title="Eliminar nivel" aria-label={`Eliminar ${level.name}`} onClick={(event) => { event.stopPropagation(); void removeLevel(level); }}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3m3 0-1 13H7L6 7m4 4v5m4-5v5" /></svg></button></div>
        </article>) : <div className="pane-empty"><strong>Aún no hay niveles</strong><span>Crea el primer nivel de esta carrera.</span></div>}</div>
      </section>
      <section className="subjects-pane">
        <header><div><p>Materias</p><h3>{selectedLevel?.name ?? "Selecciona un nivel"}</h3></div><button type="button" disabled={!selectedLevel} onClick={() => setEditor({ kind: "subject" })}>+ Nueva materia</button></header>
        {selectedLevel ? <div className="subjects-table-wrap"><table><thead><tr><th>Materia</th><th>Código</th><th>Horas</th><th>Recurso</th><th aria-label="Acciones" /></tr></thead><tbody>{selectedLevel.subjects.length ? selectedLevel.subjects.map((subject) => <tr key={subject.id}><td><strong>{subject.name}</strong></td><td>{subject.code ?? "Sin código"}</td><td>{subject.weeklyHours ? `${subject.weeklyHours} h/semana` : "Por definir"}</td><td>{subject.requiresIndividualComputer ? <span className="computer-badge">Computador individual</span> : "Aula regular"}</td><td><div className="structure-row-actions"><button type="button" onClick={() => setEditor({ kind: "subject", value: subject })}>Editar</button><button className="trash-action" type="button" title="Eliminar materia" aria-label={`Eliminar ${subject.name}`} onClick={() => void removeSubject(subject)}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3m3 0-1 13H7L6 7m4 4v5m4-5v5" /></svg></button></div></td></tr>) : <tr><td colSpan={5} className="table-message">Este nivel todavía no tiene materias.</td></tr>}</tbody></table></div> : <div className="pane-empty"><strong>Selecciona o crea un nivel</strong><span>Después podrás registrar sus materias.</span></div>}
      </section>
    </div>}
    {editor?.kind === "level" && <LevelEditor level={editor.value} nextOrder={(structure?.levels.at(-1)?.order ?? 0) + 1} saving={saving} onClose={() => setEditor(null)} onSave={async (values) => { setSaving(true); const ok = await request(editor.value ? `/api/v1/academic-structure/levels/${editor.value.id}` : "/api/v1/academic-structure/levels", editor.value ? "PATCH" : "POST", { ...values, careerId }); setSaving(false); return ok; }} />}
    {editor?.kind === "subject" && selectedLevel && <SubjectEditor subject={editor.value} saving={saving} onClose={() => setEditor(null)} onSave={async (values) => { setSaving(true); const ok = await request(editor.value ? `/api/v1/academic-structure/subjects/${editor.value.id}` : "/api/v1/academic-structure/subjects", editor.value ? "PATCH" : "POST", { ...values, careerId, levelId: selectedLevel.id }); setSaving(false); return ok; }} />}
  </section>;
}

function LevelEditor({ level, nextOrder, saving, onClose, onSave }: { level?: Level; nextOrder: number; saving: boolean; onClose: () => void; onSave: (value: { name: string; order: number }) => Promise<boolean> }) {
  const [name, setName] = useState(level?.name ?? ""); const [order, setOrder] = useState(String(level?.order ?? nextOrder));
  function submit(event: FormEvent) { event.preventDefault(); void onSave({ name, order: Number(order) }); }
  return <div className="drawer-backdrop"><aside className="structure-drawer"><header><div><p>{level ? "Editar registro" : "Nuevo registro"}</p><h2>{level ? "Editar nivel" : "Registrar nivel"}</h2></div><button type="button" onClick={onClose}>×</button></header><form onSubmit={submit}><label><span>Nombre del nivel o semestre</span><input required minLength={2} maxLength={80} value={name} onChange={(event) => setName(event.target.value)} placeholder="Ej. Primer nivel" /></label><label><span>Orden</span><input required type="number" min="1" max="20" value={order} onChange={(event) => setOrder(event.target.value)} /></label><p className="form-note">El sistema manejará automáticamente un solo curso para este nivel.</p><footer><button className="cancel-button" type="button" onClick={onClose}>Cancelar</button><button className="save-button" type="submit" disabled={saving}>{saving ? "Guardando…" : "Guardar"}</button></footer></form></aside></div>;
}

function SubjectEditor({ subject, saving, onClose, onSave }: { subject?: Subject; saving: boolean; onClose: () => void; onSave: (value: { name: string; code?: string; weeklyHours?: number; requiresIndividualComputer: boolean }) => Promise<boolean> }) {
  const [name, setName] = useState(subject?.name ?? ""); const [code, setCode] = useState(subject?.code ?? ""); const [hours, setHours] = useState(subject?.weeklyHours ? String(subject.weeklyHours) : ""); const [computer, setComputer] = useState(subject?.requiresIndividualComputer ?? false);
  function submit(event: FormEvent) { event.preventDefault(); void onSave({ name, code: code || undefined, weeklyHours: hours ? Number(hours) : undefined, requiresIndividualComputer: computer }); }
  return <div className="drawer-backdrop"><aside className="structure-drawer"><header><div><p>{subject ? "Editar registro" : "Nuevo registro"}</p><h2>{subject ? "Editar materia" : "Registrar materia"}</h2></div><button type="button" onClick={onClose}>×</button></header><form onSubmit={submit}><label><span>Nombre de la materia</span><input required minLength={2} maxLength={140} value={name} onChange={(event) => setName(event.target.value)} placeholder="Ej. Programación I" /></label><div className="structure-form-row"><label><span>Código (opcional)</span><input maxLength={30} value={code} onChange={(event) => setCode(event.target.value.toUpperCase())} placeholder="DS-PRO-101" /></label><label><span>Horas semanales</span><input type="number" min="1" max="40" value={hours} onChange={(event) => setHours(event.target.value)} /></label></div><label className="computer-option"><input type="checkbox" checked={computer} onChange={(event) => setComputer(event.target.checked)} /><span>Esta materia requiere un computador por estudiante</span></label><footer><button className="cancel-button" type="button" onClick={onClose}>Cancelar</button><button className="save-button" type="submit" disabled={saving}>{saving ? "Guardando…" : "Guardar"}</button></footer></form></aside></div>;
}
