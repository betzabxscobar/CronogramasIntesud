import { FormEvent, useCallback, useEffect, useRef, useState } from "react";

interface Career {
  id: string;
  name: string;
}

interface Coordinator {
  id: string;
  nationalId: string;
  firstNames: string;
  lastNames: string;
  email: string;
  status: "ACTIVE" | "INACTIVE";
  careers: Career[];
  lastLoginAt: string | null;
}

interface CoordinatorForm {
  nationalId: string;
  firstNames: string;
  lastNames: string;
  email: string;
  careerIds: string[];
}

interface CoordinatorsPanelProps {
  accessToken: string;
}

const emptyForm: CoordinatorForm = {
  nationalId: "",
  firstNames: "",
  lastNames: "",
  email: "",
  careerIds: []
};

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

export function CoordinatorsPanel({ accessToken }: CoordinatorsPanelProps) {
  const [coordinators, setCoordinators] = useState<Coordinator[]>([]);
  const [careers, setCareers] = useState<Career[]>([]);
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(5);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<Coordinator | null>(null);
  const [form, setForm] = useState<CoordinatorForm>(emptyForm);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const hasLoaded = useRef(false);
  const tableWrapRef = useRef<HTMLDivElement>(null);

  const headers = {
    Authorization: `Bearer ${accessToken}`,
    "Content-Type": "application/json"
  };

  const loadCoordinators = useCallback(async () => {
    if (!hasLoaded.current) setLoading(true);
    setError("");
    const params = new URLSearchParams({
      page: String(page),
      pageSize: String(pageSize)
    });
    if (search.trim()) params.set("search", search.trim());
    if (status) params.set("status", status);

    try {
      const response = await fetch(`/api/v1/coordinators?${params}`, {
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      if (!response.ok) throw new Error(await apiError(response));
      const body = (await response.json()) as {
        items: Coordinator[];
        pagination: { total: number; totalPages: number };
      };
      setCoordinators(body.items);
      setTotal(body.pagination.total);
      setTotalPages(body.pagination.totalPages);
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "No fue posible cargar los coordinadores."
      );
    } finally {
      hasLoaded.current = true;
      setLoading(false);
    }
  }, [accessToken, page, pageSize, search, status]);

  useEffect(() => {
    const timeout = window.setTimeout(() => void loadCoordinators(), 250);
    return () => window.clearTimeout(timeout);
  }, [loadCoordinators]);

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
    void fetch("/api/v1/coordinators/careers", {
      headers: { Authorization: `Bearer ${accessToken}` }
    })
      .then(async (response) => {
        if (!response.ok) throw new Error(await apiError(response));
        return response.json() as Promise<Career[]>;
      })
      .then(setCareers)
      .catch((requestError: unknown) => {
        setError(
          requestError instanceof Error
            ? requestError.message
            : "No fue posible cargar las carreras."
        );
      });
  }, [accessToken]);

  function openCreate() {
    setEditing(null);
    setForm(emptyForm);
    setFormError("");
    setDrawerOpen(true);
  }

  function openEdit(coordinator: Coordinator) {
    setEditing(coordinator);
    setForm({
      nationalId: coordinator.nationalId,
      firstNames: coordinator.firstNames,
      lastNames: coordinator.lastNames,
      email: coordinator.email,
      careerIds: coordinator.careers.map(({ id }) => id)
    });
    setFormError("");
    setDrawerOpen(true);
  }

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!form.careerIds.length) {
      setFormError("Selecciona al menos una carrera para el coordinador.");
      return;
    }
    setSaving(true);
    setFormError("");
    try {
      const response = await fetch(
        editing
          ? `/api/v1/coordinators/${editing.id}`
          : "/api/v1/coordinators",
        {
          method: editing ? "PATCH" : "POST",
          headers,
          body: JSON.stringify(form)
        }
      );
      if (!response.ok) throw new Error(await apiError(response));
      setDrawerOpen(false);
      await loadCoordinators();
    } catch (requestError) {
      setFormError(
        requestError instanceof Error
          ? requestError.message
          : "No fue posible guardar el coordinador."
      );
    } finally {
      setSaving(false);
    }
  }

  async function toggleStatus(coordinator: Coordinator) {
    const nextStatus =
      coordinator.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    setError("");
    try {
      const response = await fetch(
        `/api/v1/coordinators/${coordinator.id}/status`,
        {
          method: "PATCH",
          headers,
          body: JSON.stringify({ status: nextStatus })
        }
      );
      if (!response.ok) throw new Error(await apiError(response));
      await loadCoordinators();
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "No fue posible cambiar el estado."
      );
    }
  }

  async function deleteCoordinator(coordinator: Coordinator) {
    const fullName = `${coordinator.firstNames} ${coordinator.lastNames}`;
    const confirmed = window.confirm(
      `¿Eliminar definitivamente al coordinador “${fullName}”?`
    );
    if (!confirmed) return;

    setError("");
    try {
      const response = await fetch(`/api/v1/coordinators/${coordinator.id}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      if (!response.ok) throw new Error(await apiError(response));
      await loadCoordinators();
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "No fue posible eliminar el coordinador."
      );
    }
  }

  function updateField(field: keyof CoordinatorForm, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function toggleCareer(careerId: string) {
    setForm((current) => ({
      ...current,
      careerIds: current.careerIds.includes(careerId)
        ? current.careerIds.filter((id) => id !== careerId)
        : [...current.careerIds, careerId]
    }));
  }

  return (
    <section className="coordinators-panel">
      <header className="module-toolbar">
        <div>
          <p>Administración institucional</p>
          <h2>Gestión de coordinadores</h2>
          <span>{total} coordinadores registrados</span>
        </div>
        <button className="create-button" type="button" onClick={openCreate}>
          <span>+</span> Nuevo coordinador
        </button>
      </header>

      <div className="coordinator-filters">
        <label>
          <span>Buscar</span>
          <input
            value={search}
            onChange={(event) => {
              setSearch(event.target.value);
              setPage(1);
            }}
            placeholder="Cédula, nombre o correo"
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
            <option value="">Todos</option>
            <option value="ACTIVE">Activos</option>
            <option value="INACTIVE">Inactivos</option>
          </select>
        </label>
      </div>

      {error && <p className="panel-error">{error}</p>}

      <div className="coordinators-table-wrap" ref={tableWrapRef}>
        <table className="coordinators-table">
          <thead>
            <tr>
              <th>Coordinador</th>
              <th>Cédula</th>
              <th>Carreras asignadas</th>
              <th>Estado</th>
              <th aria-label="Acciones" />
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={5} className="table-message">Cargando coordinadores…</td></tr>
            ) : coordinators.length === 0 ? (
              <tr><td colSpan={5} className="table-message">No existen coordinadores con estos filtros.</td></tr>
            ) : (
              coordinators.map((coordinator) => (
                <tr key={coordinator.id}>
                  <td>
                    <strong>{coordinator.firstNames} {coordinator.lastNames}</strong>
                    <small>{coordinator.email}</small>
                  </td>
                  <td>{coordinator.nationalId}</td>
                  <td>
                    <div className="career-tags">
                      {coordinator.careers.length
                        ? coordinator.careers.map((career) => (
                            <span key={career.id}>{career.name}</span>
                          ))
                        : <em>Sin carreras</em>}
                    </div>
                  </td>
                  <td>
                    <span className={`status-label ${coordinator.status.toLowerCase()}`}>
                      {coordinator.status === "ACTIVE" ? "Activo" : "Inactivo"}
                    </span>
                  </td>
                  <td>
                    <div className="row-actions">
                      <button type="button" onClick={() => openEdit(coordinator)}>
                        Editar
                      </button>
                      <button
                        type="button"
                        onClick={() => void toggleStatus(coordinator)}
                      >
                        {coordinator.status === "ACTIVE" ? "Desactivar" : "Activar"}
                      </button>
                      <button
                        className="delete-row-button"
                        type="button"
                        onClick={() => void deleteCoordinator(coordinator)}
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
          <aside className="coordinator-drawer" aria-labelledby="coordinator-form-title">
            <header>
              <div>
                <p>{editing ? "Editar registro" : "Nuevo registro"}</p>
                <h2 id="coordinator-form-title">
                  {editing ? "Editar coordinador" : "Registrar coordinador"}
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
              <div className="form-grid">
                <label>
                  <span>Cédula</span>
                  <input
                    value={form.nationalId}
                    onChange={(event) =>
                      updateField("nationalId", event.target.value.replace(/\D/g, ""))
                    }
                    minLength={6}
                    maxLength={20}
                    placeholder="Ej. 1712345678"
                    required
                  />
                </label>
                <label>
                  <span>Correo institucional</span>
                  <input
                    type="email"
                    value={form.email}
                    onChange={(event) => updateField("email", event.target.value)}
                    placeholder="nombre@institucion.edu.ec"
                    required
                  />
                </label>
                <label>
                  <span>Nombres</span>
                  <input
                    value={form.firstNames}
                    onChange={(event) => updateField("firstNames", event.target.value)}
                    minLength={2}
                    maxLength={100}
                    placeholder="Nombres completos"
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
                    placeholder="Apellidos completos"
                    required
                  />
                </label>
              </div>

              <fieldset>
                <legend>Carreras asignadas</legend>
                <p>El coordinador solamente podrá trabajar con las carreras seleccionadas.</p>
                <div className="career-options">
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
