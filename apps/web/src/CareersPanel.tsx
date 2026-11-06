import {
  FormEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState
} from "react";
import { ModuleIcon } from "./ModuleIcon";

interface Career {
  id: string;
  name: string;
  slug: string;
  status: "ACTIVE" | "INACTIVE";
  _count: {
    coordinators: number;
    professors: number;
  };
}

interface CareersPanelProps {
  accessToken: string;
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

export function CareersPanel({ accessToken }: CareersPanelProps) {
  const [careers, setCareers] = useState<Career[]>([]);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Career | null>(null);
  const [name, setName] = useState("");
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [pageSize, setPageSize] = useState(5);
  const hasLoaded = useRef(false);
  const tableWrapRef = useRef<HTMLDivElement>(null);

  const authHeaders = useMemo(
    () => ({
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json"
    }),
    [accessToken]
  );

  const loadCareers = useCallback(async () => {
    if (!hasLoaded.current) setLoading(true);
    setError("");
    try {
      const response = await fetch("/api/v1/careers", {
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      if (!response.ok) throw new Error(await apiError(response));
      setCareers((await response.json()) as Career[]);
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "No fue posible cargar las carreras."
      );
    } finally {
      hasLoaded.current = true;
      setLoading(false);
    }
  }, [accessToken]);

  useEffect(() => {
    void loadCareers();
  }, [loadCareers]);

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

  const filteredCareers = useMemo(() => {
    const term = search.trim().toLocaleLowerCase();
    return careers.filter(
      (career) =>
        (!status || career.status === status) &&
        (!term ||
          career.name.toLocaleLowerCase().includes(term) ||
          career.slug.toLocaleLowerCase().includes(term))
    );
  }, [careers, search, status]);

  const totalPages = Math.max(1, Math.ceil(filteredCareers.length / pageSize));
  const visibleCareers = filteredCareers.slice(
    (page - 1) * pageSize,
    page * pageSize
  );

  useEffect(() => {
    setPage((current) => Math.min(current, totalPages));
  }, [totalPages]);

  function openCreate() {
    setEditing(null);
    setName("");
    setFormError("");
    setDrawerOpen(true);
  }

  function openEdit(career: Career) {
    setEditing(career);
    setName(career.name);
    setFormError("");
    setDrawerOpen(true);
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    const normalizedName = name.trim();
    if (normalizedName.length < 2) {
      setFormError("Escribe el nombre completo de la carrera.");
      return;
    }

    setSaving(true);
    setFormError("");
    try {
      const response = await fetch(
        editing ? `/api/v1/careers/${editing.id}` : "/api/v1/careers",
        {
          method: editing ? "PATCH" : "POST",
          headers: authHeaders,
          body: JSON.stringify({ name: normalizedName })
        }
      );
      if (!response.ok) throw new Error(await apiError(response));
      setDrawerOpen(false);
      await loadCareers();
      window.dispatchEvent(new Event("careers:changed"));
    } catch (requestError) {
      setFormError(
        requestError instanceof Error
          ? requestError.message
          : "No fue posible guardar la carrera."
      );
    } finally {
      setSaving(false);
    }
  }

  async function toggleStatus(career: Career) {
    const nextStatus = career.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    setError("");
    try {
      const response = await fetch(`/api/v1/careers/${career.id}/status`, {
        method: "PATCH",
        headers: authHeaders,
        body: JSON.stringify({ status: nextStatus })
      });
      if (!response.ok) throw new Error(await apiError(response));
      await loadCareers();
      window.dispatchEvent(new Event("careers:changed"));
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "No fue posible cambiar el estado."
      );
    }
  }

  async function remove(career: Career) {
    const confirmed = window.confirm(
      `¿Eliminar definitivamente la carrera “${career.name}”?`
    );
    if (!confirmed) return;

    setError("");
    try {
      const response = await fetch(`/api/v1/careers/${career.id}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      if (!response.ok) throw new Error(await apiError(response));
      if (visibleCareers.length === 1 && page > 1) {
        setPage((current) => current - 1);
      }
      await loadCareers();
      window.dispatchEvent(new Event("careers:changed"));
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "No fue posible eliminar la carrera."
      );
    }
  }

  return (
    <section className="careers-panel">
      <header className="module-toolbar">
        <div>
          <p>Estructura académica</p>
          <h2>Gestión de carreras</h2>
          <span>{careers.length} carreras registradas</span>
        </div>
        <button className="create-button" type="button" onClick={openCreate}>
          <span>+</span> Nueva carrera
        </button>
      </header>

      <div className="career-filters">
        <label>
          <span>Buscar carrera</span>
          <input
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(1);
            }}
            placeholder="Buscar carrera"
          />
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
            <option value="">Todos los estados</option>
            <option value="ACTIVE">Activas</option>
            <option value="INACTIVE">Inactivas</option>
          </select>
        </label>
      </div>

      {error && <p className="panel-error">{error}</p>}

      <div className="careers-table-wrap" ref={tableWrapRef}>
        <table className="careers-table">
          <thead>
            <tr>
              <th>Carrera</th>
              <th>Identificador</th>
              <th>Coordinadores</th>
              <th>Profesores</th>
              <th>Estado</th>
              <th aria-label="Acciones" />
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={6} className="table-message">Cargando carreras…</td></tr>
            ) : visibleCareers.length === 0 ? (
              <tr><td colSpan={6} className="table-message">No existen carreras con estos filtros.</td></tr>
            ) : (
              visibleCareers.map((career) => (
                <tr key={career.id}>
                  <td>
                    <div className="career-name">
                      <span><ModuleIcon name="carreras" /></span>
                      <strong>{career.name}</strong>
                    </div>
                  </td>
                  <td><code>{career.slug}</code></td>
                  <td>{career._count.coordinators}</td>
                  <td>{career._count.professors}</td>
                  <td>
                    <span className={`status-label ${career.status.toLowerCase()}`}>
                      {career.status === "ACTIVE" ? "Activa" : "Inactiva"}
                    </span>
                  </td>
                  <td>
                    <div className="row-actions">
                      <button type="button" onClick={() => openEdit(career)}>
                        Editar
                      </button>
                      <button type="button" onClick={() => void toggleStatus(career)}>
                        {career.status === "ACTIVE" ? "Desactivar" : "Activar"}
                      </button>
                      <button
                        className="delete-row-button"
                        type="button"
                        onClick={() => void remove(career)}
                      >
                        Eliminar
                      </button>
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
          <button
            type="button"
            disabled={page <= 1}
            onClick={() => setPage((current) => current - 1)}
          >
            Anterior
          </button>
          <button
            type="button"
            disabled={page >= totalPages}
            onClick={() => setPage((current) => current + 1)}
          >
            Siguiente
          </button>
        </div>
      </footer>

      {drawerOpen && (
        <div className="drawer-backdrop" role="presentation">
          <aside className="career-drawer" aria-labelledby="career-form-title">
            <header>
              <div>
                <p>{editing ? "Editar registro" : "Nuevo registro"}</p>
                <h2 id="career-form-title">
                  {editing ? "Editar carrera" : "Registrar carrera"}
                </h2>
              </div>
              <button
                type="button"
                onClick={() => setDrawerOpen(false)}
                aria-label="Cerrar formulario"
              >
                ×
              </button>
            </header>
            <form onSubmit={(event) => void save(event)}>
              <label>
                <span>Nombre completo de la carrera</span>
                <input
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  minLength={2}
                  maxLength={150}
                  placeholder="Ej. Desarrollo de Software"
                  autoFocus
                  required
                />
              </label>
              <p className="career-form-note">
                El identificador técnico se generará automáticamente a partir
                del nombre.
              </p>
              {formError && <p className="form-error">{formError}</p>}
              <footer>
                <button
                  className="cancel-button"
                  type="button"
                  onClick={() => setDrawerOpen(false)}
                >
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
