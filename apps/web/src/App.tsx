import { FormEvent, useEffect, useState } from "react";
import { Workspace } from "./Workspace";

type Step = "identify" | "verify" | "authenticated";

export interface AuthenticatedUser {
  id: string;
  firstNames: string;
  lastNames: string;
  email: string;
  role: "ADMIN" | "COORDINATOR";
  careerIds: string[];
}

interface AuthResponse {
  accessToken: string;
  user: AuthenticatedUser;
}

let refreshRequest: Promise<AuthResponse | null> | null = null;

function restoreSession() {
  if (!refreshRequest) {
    refreshRequest = fetch("/api/v1/auth/refresh", {
      method: "POST",
      credentials: "include"
    })
      .then(async (response) => {
        if (!response.ok) return null;
        return (await response.json()) as AuthResponse;
      })
      .finally(() => {
        refreshRequest = null;
      });
  }
  return refreshRequest;
}

async function readError(response: Response) {
  try {
    const body = (await response.json()) as { message?: string | string[] };
    if (Array.isArray(body.message)) return body.message.join(". ");
    return body.message ?? "No fue posible completar la solicitud.";
  } catch {
    return "No fue posible conectar con el servidor.";
  }
}

export function App() {
  const [darkMode, setDarkMode] = useState(() => {
    const savedTheme = localStorage.getItem("crono-theme");
    if (savedTheme) return savedTheme === "dark";
    return window.matchMedia("(prefers-color-scheme: dark)").matches;
  });
  // El logo es un PNG con el texto en negro horneado, así que el modo oscuro
  // necesita su propia variante en blanco para que siga siendo legible.
  const logoSrc = darkMode
    ? "/brand/Logo-Institucional-oscuro.png"
    : "/brand/Logo-Institucional.png";
  const [step, setStep] = useState<Step>("identify");
  const [nationalId, setNationalId] = useState("");
  const [code, setCode] = useState("");
  const [user, setUser] = useState<AuthenticatedUser | null>(null);
  const [accessToken, setAccessToken] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [checkingSession, setCheckingSession] = useState(true);

  useEffect(() => {
    localStorage.setItem("crono-theme", darkMode ? "dark" : "light");
    document.documentElement.style.colorScheme = darkMode ? "dark" : "light";
  }, [darkMode]);

  useEffect(() => {
    let active = true;
    void restoreSession()
      .then((session) => {
        if (!active || !session) return;
        setUser(session.user);
        setAccessToken(session.accessToken);
        setStep("authenticated");
      })
      .finally(() => {
        if (active) setCheckingSession(false);
      });
    return () => {
      active = false;
    };
  }, []);

  async function requestCode(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError("");
    setMessage("");

    try {
      const response = await fetch("/api/v1/auth/request-code", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ nationalId: nationalId.trim() })
      });
      if (!response.ok) throw new Error(await readError(response));

      const body = (await response.json()) as { message: string };
      setMessage(body.message);
      setStep("verify");
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "No fue posible solicitar el código."
      );
    } finally {
      setLoading(false);
    }
  }

  async function verifyCode(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError("");

    try {
      const response = await fetch("/api/v1/auth/verify-code", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ nationalId: nationalId.trim(), code })
      });
      if (!response.ok) throw new Error(await readError(response));

      const body = (await response.json()) as AuthResponse;
      setAccessToken(body.accessToken);
      setUser(body.user);
      setCode("");
      setMessage("");
      setStep("authenticated");
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "No fue posible verificar el código."
      );
    } finally {
      setLoading(false);
    }
  }

  async function logout() {
    setLoading(true);
    try {
      await fetch("/api/v1/auth/logout", {
        method: "POST",
        credentials: "include",
        headers: accessToken
          ? { Authorization: `Bearer ${accessToken}` }
          : undefined
      });
    } finally {
      setUser(null);
      setAccessToken("");
      setNationalId("");
      setCode("");
      setError("");
      setStep("identify");
      setLoading(false);
    }
  }

  if (checkingSession) {
    return (
      <main className={`session-loader${darkMode ? " dark" : ""}`}>
        <img
          src={logoSrc}
          alt="Instituto Superior Tecnológico Sudamericano"
        />
        <span />
        <p>Verificando sesión segura…</p>
      </main>
    );
  }

  if (step === "authenticated" && user) {
    return (
      <Workspace
        user={user}
        accessToken={accessToken}
        darkMode={darkMode}
        onToggleTheme={() => setDarkMode((current) => !current)}
        onLogout={() => void logout()}
        loggingOut={loading}
      />
    );
  }

  return (
    <main
      className={`access-page${darkMode ? " dark" : ""}${step === "verify" ? " verify-page" : ""}`}
    >
      <header className="topbar">
        <div className="institution-brand">
          <img
            src={logoSrc}
            alt="Instituto Superior Tecnológico Sudamericano, Quito Ecuador"
          />
        </div>
        <div className="topbar-actions">
          <div className="topbar-meta">
            <span><b aria-hidden="true">□</b> Periodo 2026 · 2026</span>
            <span><b aria-hidden="true">◇</b> Acceso seguro</span>
          </div>
          <button
            className="theme-toggle"
            type="button"
            onClick={() => setDarkMode((current) => !current)}
            aria-label={darkMode ? "Activar modo claro" : "Activar modo oscuro"}
            title={darkMode ? "Modo claro" : "Modo oscuro"}
          >
            <span aria-hidden="true">{darkMode ? "☀" : "☾"}</span>
          </button>
        </div>
      </header>

      <section className="main-content">
        <section className="access-content" aria-labelledby="access-title">
          <div className="product-name" aria-label="CRONO INTESUD">
            <span>CRONO</span><b>·</b><strong>INTESUD</strong>
          </div>
          {step === "authenticated" && user ? (
            <div className="access-state welcome">
              <span className="access-icon success-icon" aria-hidden="true">✓</span>
              <p className="section-kicker">Sesión iniciada</p>
              <h1 id="access-title">Hola,<br />{user.firstNames}</h1>
              <p className="subtitle">
                Ingresaste como{" "}
                <strong>{user.role === "ADMIN" ? "Administrador" : "Coordinador"}</strong>.
              </p>
              <button
                className="primary-button standalone-button"
                type="button"
                onClick={() => void logout()}
                disabled={loading}
              >
                {loading ? "Cerrando…" : "Cerrar sesión"}
              </button>
            </div>
          ) : step === "verify" ? (
            <div className="access-state verify-state">
              <button
                className="back-button"
                type="button"
                onClick={() => {
                  setStep("identify");
                  setCode("");
                  setError("");
                  setMessage("");
                }}
              >
                ← Cambiar cédula
              </button>
              <p className="section-kicker">Verificación institucional</p>
              <span className="kicker-rule" />
              <h1 id="access-title">Revisa tu correo</h1>
              <p className="subtitle">
                Ingresa el código enviado para la cédula terminada en{" "}
                <strong>{nationalId.slice(-4)}</strong>.
              </p>
              <form className="access-form" onSubmit={(event) => void verifyCode(event)}>
                <div className="field-group code-field">
                  <span className="field-caption">Código de acceso</span>
                  <input
                    id="access-code"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    pattern="[0-9]{6}"
                    maxLength={6}
                    value={code}
                    onChange={(event) =>
                      setCode(event.target.value.replace(/\D/g, ""))
                    }
                    placeholder="000000"
                    aria-label="Código de acceso"
                    autoFocus
                    required
                  />
                </div>
                <button
                  className="primary-button"
                  type="submit"
                  disabled={loading || code.length !== 6}
                >
                  {loading ? "Verificando…" : <>Ingresar <span>→</span></>}
                </button>
              </form>
              {message && <p className="form-message">{message}</p>}
              {error && <p className="form-error">{error}</p>}
            </div>
          ) : (
            <div className="access-state">
              <p className="section-kicker">Planificación académica</p>
              <span className="kicker-rule" />
              <h1 id="access-title">Acceso<br />institucional</h1>
              <form className="access-form" onSubmit={(event) => void requestCode(event)}>
                <div className="field-group">
                  <span className="id-card-icon" aria-hidden="true">▤</span>
                  <div>
                    <label htmlFor="national-id">Ingresa tu cédula</label>
                    <input
                      id="national-id"
                      inputMode="numeric"
                      autoComplete="username"
                      minLength={6}
                      maxLength={20}
                      value={nationalId}
                      onChange={(event) =>
                        setNationalId(event.target.value.replace(/\D/g, ""))
                      }
                      placeholder="Solo números"
                      autoFocus
                      required
                    />
                  </div>
                </div>
                <button
                  className="primary-button"
                  type="submit"
                  disabled={loading || nationalId.length < 6}
                >
                  {loading ? "Enviando…" : <>Continuar <span>→</span></>}
                </button>
              </form>
              {error && <p className="form-error">{error}</p>}
            </div>
          )}
        </section>

        <section className="visual-stage" aria-hidden="true">
          <div className="calendar-card calendar-back">
            <div className="calendar-days">
              <span>MIÉ</span><span>JUE</span><span>VIE</span><span>SÁB</span>
            </div>
            <div className="calendar-grid">
              <i className="event event-purple" />
              <i className="event event-coral" />
              <i className="event event-teal" />
            </div>
          </div>
          <div className="calendar-card calendar-front">
            <div className="calendar-days">
              <span>LUN</span><span>MAR</span><span>MIÉ</span>
              <span>JUE</span><span>VIE</span><span>SÁB</span>
            </div>
            <div className="calendar-body">
              <div className="calendar-hours">
                <span>08:00</span><span>10:00</span><span>12:00</span>
                <span>14:00</span><span>16:00</span><span>18:00</span>
              </div>
              <div className="calendar-grid">
                <i className="event event-purple" />
                <i className="event event-blue" />
                <i className="event event-coral" />
                <i className="event event-teal" />
                <i className="event event-blue-two" />
              </div>
            </div>
          </div>
        </section>
      </section>

      <footer className="page-footer">
        <span><b aria-hidden="true">◇</b> Solo personal autorizado</span>
        <span>
          <i className="eye-icon" aria-hidden="true" />
          Ingresa tu cédula y recibirás un código en tu correo institucional
        </span>
      </footer>
    </main>
  );
}
