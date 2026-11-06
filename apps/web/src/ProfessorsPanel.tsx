import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import type { AuthenticatedUser } from "./App";

interface Career {
  id: string;
  name: string;
}

interface Professor {
  id: string;
  nationalId: string | null;
  firstNames: string;
  lastNames: string;
  professionalTitle: string | null;
  professionalTitleAbbreviation: string | null;
  scheduleColor: string;
  publicProfileEnabled: boolean;
  status: "ACTIVE" | "INACTIVE";
  careers: Career[];
}

interface ProfessorForm {
  nationalId: string;
  firstNames: string;
  lastNames: string;
  professionalTitle: string;
  scheduleColor: string;
  publicProfileEnabled: boolean;
  careerIds: string[];
}

interface ProfessorsPanelProps {
  accessToken: string;
  user: AuthenticatedUser;
}

const emptyForm: ProfessorForm = {
  nationalId: "",
  firstNames: "",
  lastNames: "",
  professionalTitle: "",
  scheduleColor: "#A9CBE4",
  publicProfileEnabled: true,
  careerIds: []
};

function compactProfessorName(professor: Professor) {
  const firstName = professor.firstNames.trim().split(/\s+/)[0] ?? "";
  const surnames = professor.lastNames.trim().split(/\s+/);
  const firstSurname = surnames[0] ?? "";
  return [professor.professionalTitleAbbreviation, firstName, firstSurname]
    .filter(Boolean)
    .join(" ");
}

async function apiError(response: Response) {
  try {
    const body = (await response.json()) as { message?: string | string[] };
    return Array.isArray(body.message)
      ? body.message.join(". ")
      : body.message ?? "No fue posible completar la operación.";
  } catch {
    return "No fue posible conectar con el servidor.";
  }
}

export function ProfessorsPanel({
  accessToken,
  user
}: ProfessorsPanelProps) {
  const [professors, setProfessors] = useState<Professor[]>([]);
  const [careers, setCareers] = useState<Career[]>([]);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [careerId, setCareerId] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(5);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Professor | null>(null);
  const [form, setForm] = useState<ProfessorForm>(emptyForm);
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);
  const hasLoaded = useRef(false);
  const tableWrapRef = useRef<HTMLDivElement>(null);

  const headers = {
    Authorization: `Bearer ${accessToken}`,
    "Content-Type": "application/json"
  };

  const loadProfessors = useCallback(async () => {
    if (!hasLoaded.current) setLoading(true);
    setError("");
    const params = new URLSearchParams({
      page: String(page),
      pageSize: String(pageSize)
    });
    if (search.trim()) params.set("search", search.trim());
    if (status) params.set("status", status);
    if (careerId) params.set("careerId", careerId);

    try {
      const response = await fetch(`/api/v1/professors?${params}`, {
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      if (!response.ok) throw new Error(await apiError(response));
      const body = (await response.json()) as {
        items: Professor[];
        pagination: { total: number; totalPages: number };
      };
      setProfessors(body.items);
      setTotal(body.pagination.total);
      setTotalPages(body.pagination.totalPages);
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "No fue posible cargar los profesores."
      );
    } finally {
      hasLoaded.current = true;
      setLoading(false);
    }
  }, [accessToken, careerId, page, pageSize, search, status]);

  const loadCareers = useCallback(async () => {
    try {
      const response = await fetch("/api/v1/professors/careers", {
        cache: "no-store",
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      if (!response.ok) throw new Error(await apiError(response));
      const availableCareers = (await response.json()) as Career[];
      setCareers(availableCareers);
      setCareerId((current) =>
        current && !availableCareers.some(({ id }) => id === current)
          ? ""
          : current
      );
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "No fue posible cargar las carreras disponibles."
      );
    }
  }, [accessToken]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void loadProfessors(), 250);
    return () => window.clearTimeout(timeout);
  }, [loadProfessors]);

  useEffect(() => {
    const container = tableWrapRef.current;
    if (!container) return;
    const observer = new ResizeObserver(([entry]) => {
      if (!entry) return;
      const availableHeight = entry.contentRect.height - 38;
      setPageSize(Math.max(5, Math.min(8, Math.floor((availableHeight + 12) / 58))));
    });
    observer.observe(container);
    return () => observer.disconnect();
  }, []);

  useEffect(() => { setPage(1); }, [pageSize]);

  useEffect(() => {
    const refreshCareers = () => void loadCareers();
    refreshCareers();
    window.addEventListener("careers:changed", refreshCareers);
    window.addEventListener("focus", refreshCareers);
    return () => {
      window.removeEventListener("careers:changed", refreshCareers);
      window.removeEventListener("focus", refreshCareers);
    };
  }, [loadCareers]);

  function openCreate() {
    setEditing(null);
    setForm(emptyForm);
    setFormError("");
    setDrawerOpen(true);
  }

  function openEdit(professor: Professor) {
    setEditing(professor);
    setForm({
      nationalId: professor.nationalId ?? "",
      firstNames: professor.firstNames,
      lastNames: professor.lastNames,
      professionalTitle: professor.professionalTitle ?? "",
      scheduleColor: professor.scheduleColor,
      publicProfileEnabled: professor.publicProfileEnabled,
      careerIds: professor.careers
        .filter((career) => careers.some(({ id }) => id === career.id))
        .map(({ id }) => id)
    });
    setFormError("");
    setDrawerOpen(true);
  }

  function updateField(
    field: keyof ProfessorForm,
    value: string | boolean
  ) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function toggleCareer(id: string) {
    setForm((current) => ({
      ...current,
      careerIds: current.careerIds.includes(id)
        ? current.careerIds.filter((careerId) => careerId !== id)
        : [...current.careerIds, id]
    }));
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!form.careerIds.length) {
      setFormError("Selecciona al menos una carrera para el profesor.");
      return;
    }
    setSaving(true);
    setFormError("");
    try {
      const response = await fetch(
        editing ? `/api/v1/professors/${editing.id}` : "/api/v1/professors",
        {
          method: editing ? "PATCH" : "POST",
          headers,
          body: JSON.stringify({
            ...form,
            nationalId: form.nationalId || undefined,
            professionalTitle: form.professionalTitle || undefined
          })
        }
      );
      if (!response.ok) throw new Error(await apiError(response));
      setDrawerOpen(false);
      await loadProfessors();
    } catch (requestError) {
      setFormError(
        requestError instanceof Error
          ? requestError.message
          : "No fue posible guardar el profesor."
      );
    } finally {
      setSaving(false);
    }
  }

  async function toggleStatus(professor: Professor) {
    const nextStatus =
      professor.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    setError("");
    try {
      const response = await fetch(`/api/v1/professors/${professor.id}/status`, {
        method: "PATCH",
        headers,
        body: JSON.stringify({ status: nextStatus })
      });
      if (!response.ok) throw new Error(await apiError(response));
      await loadProfessors();
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "No fue posible cambiar el estado."
      );
    }
  }

  async function deleteProfessor(professor: Professor) {
    const fullName = `${professor.firstNames} ${professor.lastNames}`;
    const confirmed = window.confirm(
      `¿Eliminar definitivamente al profesor “${fullName}”?`
    );
    if (!confirmed) return;

    setError("");
    try {
      const response = await fetch(`/api/v1/professors/${professor.id}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      if (!response.ok) throw new Error(await apiError(response));
      if (professors.length === 1 && page > 1) {
        setPage((current) => current - 1);
      } else {
        await loadProfessors();
      }
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "No fue posible eliminar el profesor."
      );
    }
  }

  return (
    <section className="professors-panel">
      <header className="module-toolbar">
        <div>
          <p>{user.role === "ADMIN" ? "Directorio institucional" : "Carreras asignadas"}</p>
          <h2>Gestión de profesores</h2>
          <span>{total} profesores disponibles</span>
        </div>
        <button className="create-button" type="button" onClick={openCreate}>
          <span>+</span> Nuevo profesor
        </button>
      </header>

      <div className="professor-filters">
        <label>
          <span>Buscar</span>
          <input
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(1);
            }}
            placeholder="Nombre, cédula o título"
          />
        </label>
        <label>
          <span>Carrera</span>
          <select
            value={careerId}
            onChange={(event) => {
              setCareerId(event.target.value);
              setPage(1);
            }}
          >
            <option value="">Todas las carreras</option>
            {careers.map((career) => (
              <option value={career.id} key={career.id}>{career.name}</option>
            ))}
          </select>
        </label>
        <label>
          <span>Estado</span>
          <select
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(1);
            }}
          >
            <option value="">Todos</option>
            <option value="ACTIVE">Activos</option>
            <option value="INACTIVE">Inactivos</option>
          </select>
        </label>
      </div>

      {error && <p className="panel-error">{error}</p>}

      <div className="professors-table-wrap" ref={tableWrapRef}>
        <table className="professors-table">
          <thead>
            <tr>
              <th>Profesor</th>
              <th>Título profesional</th>
              <th>Carreras</th>
              <th>Perfil público</th>
              <th>Estado</th>
              <th aria-label="Acciones" />
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={6} className="table-message">Cargando profesores…</td></tr>
            ) : professors.length === 0 ? (
              <tr><td colSpan={6} className="table-message">No existen profesores con estos filtros.</td></tr>
            ) : (
              professors.map((professor) => (
                <tr key={professor.id}>
                  <td>
                    <div className="professor-identity">
                      <span className="professor-avatar">
                        {professor.firstNames.charAt(0)}{professor.lastNames.charAt(0)}
                      </span>
                      <div>
                        <strong>{compactProfessorName(professor)}</strong>
                        <small>{professor.nationalId ?? "Sin cédula registrada"}</small>
                      </div>
                    </div>
                  </td>
                  <td>{professor.professionalTitle ?? "Sin título registrado"}</td>
                  <td>
                    <div className="career-tags">
                      {professor.careers.map((career) => (
                        <span key={career.id}>{career.name}</span>
                      ))}
                    </div>
                  </td>
                  <td>
                    <span className={`public-label ${professor.publicProfileEnabled ? "visible" : "hidden"}`}>
                      {professor.publicProfileEnabled ? "Visible" : "Oculto"}
                    </span>
                  </td>
                  <td>
                    <span className={`status-label ${professor.status.toLowerCase()}`}>
                      {professor.status === "ACTIVE" ? "Activo" : "Inactivo"}
                    </span>
                  </td>
                  <td>
                    <div className={`row-actions professor-actions${user.role === "ADMIN" ? " admin-actions" : ""}`}>
                      <button type="button" onClick={() => openEdit(professor)}>
                        Editar
                      </button>
                      <button type="button" onClick={() => void toggleStatus(professor)}>
                        {professor.status === "ACTIVE" ? "Desactivar" : "Activar"}
                      </button>
                      {user.role === "ADMIN" && (
                        <button
                          className="delete-row-button icon-delete-button"
                          type="button"
                          onClick={() => void deleteProfessor(professor)}
                          aria-label={`Eliminar a ${professor.firstNames} ${professor.lastNames}`}
                          title="Eliminar profesor"
                        >
                          <svg viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M4 7h16M9 7V4h6v3m3 0-1 13H7L6 7m4 4v5m4-5v5" />
                          </svg>
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <footer className="table-pagination">
        <span>Página {page} de {totalPages}</span>
        <div>
          <button type="button" disabled={page <= 1} onClick={() => setPage((value) => value - 1)}>
            Anterior
          </button>
          <button type="button" disabled={page >= totalPages} onClick={() => setPage((value) => value + 1)}>
            Siguiente
          </button>
        </div>
      </footer>

      {drawerOpen && (
        <div className="drawer-backdrop" role="presentation">
          <aside className="professor-drawer" aria-labelledby="professor-form-title">
            <header>
              <div>
                <p>{editing ? "Editar registro" : "Nuevo registro"}</p>
                <h2 id="professor-form-title">
                  {editing ? "Editar profesor" : "Registrar profesor"}
                </h2>
              </div>
              <button type="button" onClick={() => setDrawerOpen(false)} aria-label="Cerrar formulario">×</button>
            </header>
            <form onSubmit={(event) => void save(event)}>
              <div className="professor-form-sections">
                <section className="professor-form-section">
                  <header>
                    <div>
                      <h3>Datos del profesor</h3>
                      <p>Completa la información y selecciona las carreras vinculadas.</p>
                    </div>
                  </header>
              <div className="professor-form-grid">
                <label>
                  <span>Cédula</span>
                  <input
                    value={form.nationalId}
                    onChange={(event) => updateField("nationalId", event.target.value.replace(/\D/g, ""))}
                    placeholder="Opcional"
                    maxLength={20}
                  />
                </label>
                <label>
                  <span>Título profesional</span>
                  <input
                    value={form.professionalTitle}
                    onChange={(event) => updateField("professionalTitle", event.target.value)}
                    placeholder="Ej. Ingeniero, Mtr., MSc. o Tnlgo."
                    maxLength={180}
                  />
                </label>
                <label>
                  <span>Nombres</span>
                  <input
                    value={form.firstNames}
                    onChange={(event) => updateField("firstNames", event.target.value)}
                    minLength={2}
                    maxLength={100}
                    required
                  />
                </label>
                <label>
                  <span>Apellidos</span>
                  <input
                    value={form.lastNames}
                    onChange={(event) => updateField("lastNames", event.target.value)}
                    minLength={2}
                    maxLength={100}
                    required
                  />
                </label>
                <label className="professor-color-field">
                  <span>Color en el horario</span>
                  <div>
                    <input
                      type="color"
                      value={form.scheduleColor}
                      onChange={(event) => updateField("scheduleColor", event.target.value.toUpperCase())}
                      aria-label="Seleccionar color del profesor"
                    />
                    <strong>{form.scheduleColor}</strong>
                    <i style={{ backgroundColor: form.scheduleColor }} aria-hidden="true" />
                  </div>
                </label>
              </div>

              <label className="public-profile-toggle">
                <input
                  type="checkbox"
                  checked={form.publicProfileEnabled}
                  onChange={(event) => updateField("publicProfileEnabled", event.target.checked)}
                />
                <span>Mostrar este profesor en el directorio público</span>
              </label>

              <fieldset>
                <legend>Carreras vinculadas</legend>
                <p>Solo aparecen las carreras que puedes administrar.</p>
                <div className="professor-career-options">
                  {careers.map((career) => (
                    <label key={career.id}>
                      <input
                        type="checkbox"
                        checked={form.careerIds.includes(career.id)}
                        onChange={() => toggleCareer(career.id)}
                      />
                      <span>{career.name}</span>
                    </label>
                  ))}
                </div>
              </fieldset>
                </section>
              </div>

              {formError && <p className="form-error">{formError}</p>}
              <footer>
                <button className="cancel-button" type="button" onClick={() => setDrawerOpen(false)}>
                  Cancelar
                </button>
                <button className="save-button" type="submit" disabled={saving}>
                  {saving ? "Guardando…" : editing ? "Guardar cambios" : "Registrar"}
                </button>
              </footer>
            </form>
          </aside>
        </div>
      )}
    </section>
  );
}
