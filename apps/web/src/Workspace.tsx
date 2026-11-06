import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import type { AuthenticatedUser } from "./App";
import { CoordinatorsPanel } from "./CoordinatorsPanel";
import { CareersPanel } from "./CareersPanel";
import { ProfessorsPanel } from "./ProfessorsPanel";
import { LaboratoriesPanel } from "./LaboratoriesPanel";
import { ModuleIcon } from "./ModuleIcon";
import { AcademicStructurePanel } from "./AcademicStructurePanel";
import { AcademicPeriodsPanel } from "./AcademicPeriodsPanel";
import { SchedulesPanel } from "./SchedulesPanel";
import { ImportsPanel } from "./ImportsPanel";

interface WorkspaceProps {
  user: AuthenticatedUser;
  accessToken: string;
  darkMode: boolean;
  loggingOut: boolean;
  onToggleTheme: () => void;
  onLogout: () => void;
}

interface NavigationItem {
  id: string;
  label: string;
  icon: string;
  adminOnly?: boolean;
}

const navigation: NavigationItem[] = [
  { id: "inicio", label: "Inicio", icon: "⌂" },
  { id: "horarios", label: "Horarios", icon: "▦" },
  { id: "profesores", label: "Profesores", icon: "♙" },
  { id: "laboratorios", label: "Laboratorios", icon: "⌘" },
  { id: "coordinadores", label: "Coordinadores", icon: "♢", adminOnly: true },
  { id: "carreras", label: "Carreras", icon: "▤", adminOnly: true },
  { id: "estructura", label: "Estructura académica", icon: "≡" },
  { id: "periodos", label: "Periodos y cursos", icon: "◫" },
  { id: "importar", label: "Importar Excel", icon: "↥" },
  { id: "historial", label: "Historial", icon: "↻", adminOnly: true }
];

const adminActions = [
  {
    id: "coordinadores",
    number: "01",
    title: "Coordinadores",
    description: "Registra coordinadores y asigna las carreras que administrarán."
  },
  {
    id: "carreras",
    number: "02",
    title: "Carreras",
    description: "Consulta la estructura académica oficial y sus responsables."
  },
  {
    id: "profesores",
    number: "03",
    title: "Profesores",
    description: "Supervisa el directorio académico y sus vinculaciones."
  },
  {
    id: "laboratorios",
    number: "04",
    title: "Laboratorios",
    description: "Administra espacios, capacidades y carreras autorizadas."
  }
];

const coordinatorActions = [
  {
    id: "horarios",
    number: "01",
    title: "Planificar horarios",
    description: "Crea o continúa los horarios de tus carreras asignadas."
  },
  {
    id: "profesores",
    number: "02",
    title: "Profesores",
    description: "Gestiona docentes y sus vinculaciones académicas."
  },
  {
    id: "laboratorios",
    number: "03",
    title: "Laboratorios",
    description: "Consulta disponibilidad y realiza asignaciones."
  }
];

export function Workspace({
  user,
  accessToken,
  darkMode,
  loggingOut,
  onToggleTheme,
  onLogout
}: WorkspaceProps) {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [searchParams, setSearchParams] = useSearchParams();
  const isAdmin = user.role === "ADMIN";
  const items = navigation.filter((item) => isAdmin || !item.adminOnly);
  const requestedSection = searchParams.get("seccion") ?? "inicio";
  const activeSection = items.some((item) => item.id === requestedSection)
    ? requestedSection
    : "inicio";
  const actions = isAdmin ? adminActions : coordinatorActions;
  const activeLabel =
    items.find((item) => item.id === activeSection)?.label ?? "Inicio";

  function selectSection(section: string) {
    setSearchParams(section === "inicio" ? {} : { seccion: section });
  }

  return (
    <main
      className={`workspace${darkMode ? " dark" : ""}${
        sidebarCollapsed ? " sidebar-collapsed" : ""
      }`}
    >
      <aside className="workspace-sidebar">
        <div className="workspace-brand" aria-label="CRONO INTESUD">
          <div>
            <strong>CRONO</strong>
            <b>INTESUD</b>
            <small>Coordinación académica</small>
          </div>
        </div>
        <button
          className="sidebar-toggle"
          type="button"
          onClick={() => setSidebarCollapsed((current) => !current)}
          aria-label={sidebarCollapsed ? "Expandir menú" : "Contraer menú"}
          title={sidebarCollapsed ? "Expandir menú" : "Contraer menú"}
        >
          <span aria-hidden="true">{sidebarCollapsed ? "›" : "‹"}</span>
        </button>

        <nav aria-label="Navegación principal">
          <p className="nav-caption">Gestión académica</p>
          {items.map((item) => (
            <button
              className={activeSection === item.id ? "active" : ""}
              type="button"
              key={item.id}
              onClick={() => selectSection(item.id)}
            >
              <span aria-hidden="true">{item.icon}</span>
              <em>{item.label}</em>
            </button>
          ))}
        </nav>

        <div className="sidebar-user">
          <span className="user-avatar">
            {user.firstNames.charAt(0)}
            {user.lastNames.charAt(0)}
          </span>
          <div>
            <strong>{user.firstNames} {user.lastNames}</strong>
            <small>{isAdmin ? "Administrador" : "Coordinador"}</small>
          </div>
        </div>
      </aside>

      <section className="workspace-main">
        <header className="workspace-topbar">
          <div>
            <h1>{activeLabel}</h1>
          </div>
          <div className="workspace-actions">
            <span className="connection-state"><i /> Sesión segura</span>
            <button
              className="workspace-icon-button"
              type="button"
              onClick={onToggleTheme}
              aria-label={darkMode ? "Activar modo claro" : "Activar modo oscuro"}
              title={darkMode ? "Modo claro" : "Modo oscuro"}
            >
              {darkMode ? "☀" : "☾"}
            </button>
            <button
              className="logout-button"
              type="button"
              onClick={onLogout}
              disabled={loggingOut}
            >
              {loggingOut ? "Cerrando…" : "Cerrar sesión"}
            </button>
          </div>
        </header>

        <div className="workspace-content">
          {activeSection === "inicio" ? (
            <>
              <section className="welcome-strip">
                <div>
                  <p>{isAdmin ? "Administración institucional" : "Coordinación académica"}</p>
                  <h2>Bienvenido,<br /><strong>{user.firstNames}</strong></h2>
                  <span>
                    {isAdmin
                      ? "Gestiona la estructura académica y los accesos desde un solo lugar."
                      : "Trabaja únicamente con las carreras que tienes asignadas."}
                  </span>
                </div>
                <div className="period-badge">
                  <span className="period-icon"><ModuleIcon name="periodo" /></span>
                  <div>
                    <small>Periodo vigente</small>
                    <strong>2026 · 2026</strong>
                  </div>
                </div>
              </section>

              <section className="module-section">
                <div className="module-heading">
                  <div>
                    <p>Accesos principales</p>
                    <h2>{isAdmin ? "Administración del sistema" : "Tu planificación"}</h2>
                  </div>
                  <span>{actions.length} módulos disponibles</span>
                </div>
                <div className="module-grid">
                  {actions.map((action) => (
                    <button
                      className="module-card"
                      type="button"
                      key={action.id}
                      onClick={() => selectSection(action.id)}
                    >
                      <div className="module-card-top">
                        <span className={`module-icon module-icon-${action.id}`}>
                          <ModuleIcon name={action.id as "coordinadores" | "carreras" | "profesores" | "laboratorios" | "horarios"} />
                        </span>
                        <span className="module-number">{action.number}</span>
                      </div>
                      <div>
                        <h3>{action.title}</h3>
                        <p>{action.description}</p>
                      </div>
                      <span className="module-access">
                        Acceder <b aria-hidden="true">→</b>
                      </span>
                    </button>
                  ))}
                </div>
              </section>
            </>
          ) : activeSection === "horarios" ? (
            <SchedulesPanel accessToken={accessToken} user={user} />
          ) : activeSection === "coordinadores" && isAdmin ? (
            <CoordinatorsPanel accessToken={accessToken} />
          ) : activeSection === "carreras" && isAdmin ? (
            <CareersPanel accessToken={accessToken} />
          ) : activeSection === "profesores" ? (
            <ProfessorsPanel accessToken={accessToken} user={user} />
          ) : activeSection === "laboratorios" ? (
            <LaboratoriesPanel accessToken={accessToken} user={user} />
          ) : activeSection === "estructura" ? (
            <AcademicStructurePanel accessToken={accessToken} user={user} />
          ) : activeSection === "periodos" ? (
            <AcademicPeriodsPanel accessToken={accessToken} user={user} />
          ) : activeSection === "importar" ? (
            <ImportsPanel accessToken={accessToken} />
          ) : (
            <section className="module-placeholder">
              <span>{activeLabel.slice(0, 2).toUpperCase()}</span>
              <p>Módulo seleccionado</p>
              <h2>{activeLabel}</h2>
              <p>
                La navegación protegida ya está funcionando. Ahora conectaremos
                aquí la gestión y los datos reales de este módulo.
              </p>
              <button type="button" onClick={() => selectSection("inicio")}>
                ← Volver al inicio
              </button>
            </section>
          )}
        </div>
      </section>
    </main>
  );
}
