interface ModuleIconProps {
  name: "coordinadores" | "carreras" | "profesores" | "laboratorios" | "horarios" | "periodo";
}

export function ModuleIcon({ name }: ModuleIconProps) {
  const common = {
    width: 24,
    height: 24,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 1.9,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    "aria-hidden": true
  };

  if (name === "coordinadores") {
    return (
      <svg {...common}>
        <path d="M16 20v-1.5c0-2-1.8-3.5-4-3.5H6c-2.2 0-4 1.5-4 3.5V20" />
        <circle cx="9" cy="7.5" r="3.5" />
        <path d="M18 10a3 3 0 1 0-2.2-5" />
        <path d="M17.5 15c2.5.1 4.5 1.5 4.5 3.5V20" />
      </svg>
    );
  }

  if (name === "carreras") {
    return (
      <svg {...common}>
        <path d="m3 9 9-5 9 5-9 5-9-5Z" />
        <path d="M7 12v4.5c2.7 2 7.3 2 10 0V12" />
        <path d="M21 9v6" />
      </svg>
    );
  }

  if (name === "profesores") {
    return (
      <svg {...common}>
        <circle cx="12" cy="7" r="4" />
        <path d="M4.5 21v-2.5c0-3.2 3.4-5.5 7.5-5.5s7.5 2.3 7.5 5.5V21" />
        <path d="m9.5 14 2.5 3 2.5-3" />
      </svg>
    );
  }

  if (name === "laboratorios") {
    return (
      <svg {...common}>
        <path d="M9 3h6" />
        <path d="M10 3v5l-5.2 9a2.7 2.7 0 0 0 2.3 4h9.8a2.7 2.7 0 0 0 2.3-4L14 8V3" />
        <path d="M7.5 15h9" />
        <path d="m10 12 4 4" />
      </svg>
    );
  }

  if (name === "periodo") {
    return (
      <svg {...common}>
        <rect x="3" y="5" width="18" height="16" rx="2" />
        <path d="M16 3v4M8 3v4M3 10h18" />
        <path d="m8.5 15 2.2 2.2 4.8-4.8" />
      </svg>
    );
  }

  return (
    <svg {...common}>
      <rect x="3" y="4" width="18" height="17" rx="2" />
      <path d="M8 2v4M16 2v4M3 9h18" />
      <path d="M7 13h3v3H7zM14 13h3v3h-3z" />
    </svg>
  );
}
