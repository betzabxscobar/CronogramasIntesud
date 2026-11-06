import { ChangeEvent, FormEvent, useCallback, useEffect, useState } from "react";
import { ModuleIcon } from "./ModuleIcon";

interface CareerOption { id: string; name: string }
interface PeriodOption { id: string; name: string }
interface CatalogBlock { column: number; career: string; subjects: number }
interface SkippedCell { cell: string; value: string; reason: string }
interface ReviewRow {
  sheetName: string;
  rowNumber: number;
  entity: "level" | "subject" | "professor" | "space" | "block";
  status: "VALID" | "WARNING" | "ERROR";
  label: string;
  messages: string[];
}
interface BlockShift {
  shift: "DAY" | "NIGHT";
  sheetName: string;
  blocks: number;
  byLevel: Record<string, number>;
  ignoredProfessorBlocks: number;
  placeholders: number;
  notes: string[];
  missingModuleDates: number;
}
interface BlockPreview {
  periodName: string;
  shifts: BlockShift[];
  conflicts: number;
}
interface PreviewResult {
  job: {
    id: string;
    fileName: string;
    status: string;
    totalRows: number;
    validRows: number;
    warningRows: number;
    errorRows: number;
  };
  sheets: string[];
  activeCareerInBook: string | null;
  catalogBlocks: CatalogBlock[];
  counts: { subjects: number; professors: number; spaces: number; levels: number; blocks: number };
  blocks?: BlockPreview;
  needsReview: ReviewRow[];
  skipped: SkippedCell[];
  notes: string[];
  canCommit: boolean;
}

const STATUS_LABELS: Record<string, string> = {
  UPLOADED: "Recibido",
  VALIDATING: "Validando",
  INVALID: "Con errores",
  READY: "Listo para aplicar",
  IMPORTED: "Aplicado",
  CANCELLED: "Cancelado"
};

const ENTITY_LABELS: Record<ReviewRow["entity"], string> = {
  level: "Nivel",
  subject: "Materia",
  professor: "Docente",
  space: "Aula",
  block: "Bloque"
};

const SHIFT_LABELS: Record<"DAY" | "NIGHT", string> = {
  DAY: "Diurna",
  NIGHT: "Nocturna"
};

export function ImportsPanel({ accessToken }: { accessToken: string }) {
  const [careers, setCareers] = useState<CareerOption[]>([]);
  const [periods, setPeriods] = useState<PeriodOption[]>([]);
  const [careerId, setCareerId] = useState("");
  const [periodId, setPeriodId] = useState("");
  const [includeSchedule, setIncludeSchedule] = useState(true);
  const [shift, setShift] = useState<"ALL" | "DAY" | "NIGHT">("ALL");
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<PreviewResult | null>(null);
  const [applied, setApplied] = useState<{ career: string; applied: Record<string, number> } | null>(null);
  const [busy, setBusy] = useState<"preview" | "commit" | null>(null);
  const [error, setError] = useState("");

  const auth = useCallback(
    () => ({ Authorization: `Bearer ${accessToken}` }),
    [accessToken]
  );

  useEffect(() => {
    void fetch("/api/v1/careers", { headers: auth(), cache: "no-store" })
      .then(async (response) => {
        if (!response.ok) throw new Error(await leerError(response));
        return response.json() as Promise<CareerOption[]>;
      })
      .then((items) => {
        setCareers(items);
        setCareerId((current) => current || items[0]?.id || "");
      })
      .catch((e: unknown) =>
        setError(e instanceof Error ? e.message : "No se pudieron cargar las carreras.")
      );
  }, [auth]);

  useEffect(() => {
    void fetch("/api/v1/academic-periods", { headers: auth(), cache: "no-store" })
      .then(async (response) => {
        if (!response.ok) throw new Error(await leerError(response));
        return response.json() as Promise<PeriodOption[]>;
      })
      .then((items) => {
        setPeriods(items);
        setPeriodId((current) => current || items[0]?.id || "");
      })
      .catch(() => setPeriods([]));
  }, [auth]);

  async function leerError(response: Response) {
    try {
      const body = (await response.json()) as { message?: string | string[] };
      return Array.isArray(body.message) ? body.message.join(". ") : body.message ?? "No se pudo completar.";
    } catch {
      return "No se pudo conectar con el servidor.";
    }
  }

  function elegirArchivo(event: ChangeEvent<HTMLInputElement>) {
    const chosen = event.target.files?.[0] ?? null;
    setFile(chosen);
    setPreview(null);
    setApplied(null);
    setError("");
  }

  async function analizar(event: FormEvent) {
    event.preventDefault();
    if (!file || !careerId) return;
    setBusy("preview");
    setError("");
    setPreview(null);
    setApplied(null);
    try {
      const body = new FormData();
      body.append("file", file);
      body.append("careerId", careerId);
      if (includeSchedule && periodId) {
        body.append("includeSchedule", "true");
        body.append("periodId", periodId);
        body.append("shift", shift);
      }
      const response = await fetch("/api/v1/imports/preview", {
        method: "POST",
        headers: auth(),
        body
      });
      if (!response.ok) throw new Error(await leerError(response));
      setPreview((await response.json()) as PreviewResult);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "No se pudo analizar el archivo.");
    } finally {
      setBusy(null);
    }
  }

  async function confirmar() {
    if (!preview) return;
    setBusy("commit");
    setError("");
    try {
      const response = await fetch(`/api/v1/imports/${preview.job.id}/commit`, {
        method: "POST",
        headers: auth()
      });
      if (!response.ok) throw new Error(await leerError(response));
      const body = (await response.json()) as { career: string; applied: Record<string, number> };
      setApplied(body);
      setPreview(null);
      setFile(null);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "No se pudo aplicar la importación.");
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="imports-panel">
      <header className="module-toolbar">
        <div>
          <p>Migración desde Excel</p>
          <h2>Importar datos del libro</h2>
          <span>Sube el libro .xlsx, revisa lo que se detectó y confirma.</span>
        </div>
        <span className="imports-shield">
          <ModuleIcon name="periodo" />
          Nada se escribe hasta que confirmes
        </span>
      </header>

      {error && <p className="panel-error">{error}</p>}

      {applied && (
        <div className="imports-done">
          <h3>Importación aplicada en {applied.career}</h3>
          <ul>
            {Object.entries(applied.applied).map(([key, value]) => (
              <li key={key}>
                <strong>{value}</strong>
                <span>{{ levels: "niveles", subjects: "materias", professors: "docentes", spaces: "aulas", blocks: "bloques" }[key] ?? key}</span>
              </li>
            ))}
          </ul>
          <p className="form-note">
            Revisa Estructura académica para asignar el nivel a las materias que el libro no lo indicaba.
          </p>
        </div>
      )}

      {!preview && !applied && (
        <form className="imports-upload" onSubmit={analizar}>
          <div className="imports-fields">
            <label className="imports-field">
              <span>Carrera de destino</span>
              <select value={careerId} onChange={(e) => setCareerId(e.target.value)} required>
                {careers.map((career) => (
                  <option key={career.id} value={career.id}>
                    {career.name}
                  </option>
                ))}
              </select>
            </label>

            <label className="imports-field">
              <span>Periodo de los horarios</span>
              <select
                value={periodId}
                onChange={(e) => setPeriodId(e.target.value)}
                disabled={!includeSchedule}
                required={includeSchedule}
              >
                {periods.length === 0 && <option value="">No hay periodos creados</option>}
                {periods.map((period) => (
                  <option key={period.id} value={period.id}>
                    {period.name}
                  </option>
                ))}
              </select>
            </label>

            <label className="imports-field">
              <span>Turno a importar</span>
              <select
                value={shift}
                onChange={(e) => setShift(e.target.value as "ALL" | "DAY" | "NIGHT")}
                disabled={!includeSchedule}
              >
                <option value="ALL">Diurna y nocturna</option>
                <option value="DAY">Solo diurna</option>
                <option value="NIGHT">Solo nocturna</option>
              </select>
            </label>
          </div>

          <label className="imports-check">
            <input
              type="checkbox"
              checked={includeSchedule}
              onChange={(event) => setIncludeSchedule(event.target.checked)}
            />
            <span>
              Traer también los bloques de horario
              <small>
                Lee las hojas diurna y nocturna. Si eliges un solo turno puedes migrar el diurno aunque el
                nocturno tenga algo pendiente.
              </small>
            </span>
          </label>

          <label className="imports-dropzone">
            <input type="file" accept=".xlsx" onChange={elegirArchivo} />
            <strong>{file ? file.name : "Elige el libro .xlsx"}</strong>
            <span>
              {file
                ? `${(file.size / 1024).toFixed(0)} KB · listo para analizar`
                : "Debe ser el libro «Horarios» con la hoja Inicio."}
            </span>
          </label>

          <button
            className="create-button"
            type="submit"
            disabled={!file || !careerId || busy !== null || (includeSchedule && !periodId)}
          >
            {busy === "preview" ? "Analizando…" : "Analizar libro"}
          </button>
        </form>
      )}

      {preview && (
        <div className="imports-preview">
          <div className={`imports-verdict imports-verdict-${preview.job.status.toLowerCase()}`}>
            <strong>{STATUS_LABELS[preview.job.status] ?? preview.job.status}</strong>
            <span>{preview.job.fileName}</span>
            {preview.activeCareerInBook && (
              <span>El libro tiene seleccionada la carrera «{preview.activeCareerInBook}»</span>
            )}
          </div>

          <ul className="imports-counts">
            <li>
              <strong>{preview.counts.levels}</strong>
              <span>niveles</span>
            </li>
            <li>
              <strong>{preview.counts.subjects}</strong>
              <span>materias</span>
            </li>
            <li>
              <strong>{preview.counts.professors}</strong>
              <span>docentes</span>
            </li>
            <li>
              <strong>{preview.counts.spaces}</strong>
              <span>aulas</span>
            </li>
            {preview.blocks && (
              <li className="imports-count-blocks">
                <strong>{preview.counts.blocks}</strong>
                <span>bloques</span>
              </li>
            )}
          </ul>

          <div className="imports-totals">
            <span className="imports-total-valid">{preview.job.validRows} correctas</span>
            {preview.job.warningRows > 0 && (
              <span className="imports-total-warning">{preview.job.warningRows} por revisar</span>
            )}
            {preview.job.errorRows > 0 && (
              <span className="imports-total-error">{preview.job.errorRows} con error</span>
            )}
          </div>

          {preview.blocks && preview.blocks.shifts.length > 0 && (
            <div className="imports-blocks">
              <h3>
                Horarios del periodo {preview.blocks.periodName}
                {preview.blocks.conflicts === 0 && <b className="imports-ok">sin cruces</b>}
              </h3>
              <ul className="imports-shift-list">
                {preview.blocks.shifts.map((item) => (
                  <li key={item.shift}>
                    <strong>{SHIFT_LABELS[item.shift]}</strong>
                    <span>
                      {item.blocks} bloques · {Object.entries(item.byLevel)
                        .map(([nivel, total]) => `${nivel}º ${total}`)
                        .join(" · ")}
                    </span>
                    <small>
                      Se ignoraron {item.ignoredProfessorBlocks} bloques por docente (repiten la misma
                      información)
                      {item.placeholders > 0 &&
                        ` · ${item.placeholders} celdas del libro con la plantilla sin diligenciar`}
                    </small>
                    {item.notes.map((note) => (
                      <small key={note}>{note}</small>
                    ))}
                  </li>
                ))}
              </ul>
              {preview.blocks.conflicts > 0 && (
                <p className="imports-conflict">
                  {preview.blocks.conflicts}choques detectados: revisa las filas marcadas antes de aplicar.
                </p>
              )}
            </div>
          )}

          {preview.notes.length > 0 && (
            <ul className="imports-notes">
              {preview.notes.map((note) => (
                <li key={note}>{note}</li>
              ))}
            </ul>
          )}

          {preview.needsReview.length > 0 && (
            <div className="imports-review">
              <h3>Filas que conviene revisar ({preview.needsReview.length})</h3>
              <div className="imports-review-table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Fila</th>
                      <th>Estado</th>
                      <th>Tipo</th>
                      <th>Contenido</th>
                      <th>Observación</th>
                    </tr>
                  </thead>
                  <tbody>
                    {preview.needsReview.slice(0, 60).map((row, index) => (
                      <tr key={`${row.rowNumber}-${index}`} className={`imports-review-${row.status.toLowerCase()}`}>
                        <td>{row.rowNumber}</td>
                        <td>{ENTITY_LABELS[row.entity]}</td>
                        <td>
                          <span className={`imports-badge imports-badge-${row.status.toLowerCase()}`}>
                            {row.status === "WARNING"
                              ? "Revisar"
                              : row.status === "ERROR"
                                ? "Error"
                                : "Correcto"}
                          </span>
                        </td>
                        <td>{row.label || "—"}</td>
                        <td>{row.messages.join(" · ")}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {preview.needsReview.length > 60 && (
                <p className="imports-review-more">
                  y {preview.needsReview.length - 60} más…
                </p>
              )}
            </div>
          )}

          {preview.skipped.length > 0 && (
            <div className="imports-skipped">
              <h3>Celdas del libro que no son datos ({preview.skipped.length})</h3>
              <ul>
                {preview.skipped.slice(0, 12).map((cell) => (
                  <li key={cell.cell}>
                    <code>{cell.cell}</code> <q>{cell.value}</q> — {cell.reason}
                  </li>
                ))}
              </ul>
            </div>
          )}

          <footer className="imports-actions">
            <button
              className="cancel-button"
              type="button"
              onClick={() => setPreview(null)}
              disabled={busy !== null}
            >
              Descartar
            </button>
            <button
              className="save-button"
              type="button"
              onClick={() => void confirmar()}
              disabled={!preview.canCommit || busy !== null}
              title={
                preview.canCommit
                  ? undefined
                  : "Hay errores que impiden aplicar la importación. Revisa el archivo y vuelve a subirlo."
              }
            >
              {busy === "commit"
                ? "Aplicando…"
                : `Aplicar ${preview.job.validRows + preview.job.warningRows} registros`}
            </button>
          </footer>
        </div>
      )}
    </section>
  );
}
