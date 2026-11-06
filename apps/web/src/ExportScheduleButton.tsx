import { useEffect, useRef, useState } from "react";

/**
 * Descarga el libro de horarios de la carrera y el periodo indicados.
 *
 * El archivo lleva la hoja de datos maestros, el horario diurno y el nocturno,
 * que es la misma disposición que el sistema sabe importar. Así el libro deja de
 * ser la fuente y pasa a ser una salida del sistema.
 *
 * El archivo sale de la versión publicada del horario, no del borrador que se
 * está viendo. Por eso el botón avisa antes de dejar que el servidor responda:
 * un error de "no está publicado" se entiende mucho mejor de antemano.
 */
export function ExportScheduleButton({
  accessToken,
  periodId,
  periodName,
  careerId,
  careerName,
  hayPublicado
}: {
  accessToken: string;
  periodId: string;
  periodName: string;
  careerId: string;
  careerName: string;
  /** Si el horario ya tiene una versión publicada que se pueda descargar. */
  hayPublicado: boolean;
}) {
  const [estado, setEstado] = useState<"inactivo" | "bajando" | "error">("inactivo");
  const [mensaje, setMensaje] = useState("");
  const temporizador = useRef<ReturnType<typeof setTimeout> | null>(null);

  // El aviso se retira solo: si se queda, tapa la rejilla del horario, que es
  // justo lo que la persona quiere ver después de exportar.
  useEffect(() => {
    if (estado !== "error") return;
    if (temporizador.current) clearTimeout(temporizador.current);
    temporizador.current = setTimeout(() => setEstado("inactivo"), 9000);
    return () => {
      if (temporizador.current) clearTimeout(temporizador.current);
    };
  }, [estado]);

  const deshabilitado = estado === "bajando" || !periodId || !careerId;

  async function descargar() {
    if (!hayPublicado) {
      setEstado("error");
      setMensaje(
        "Este horario todavía no está publicado. Pulsa «Publicar» para congelarlo y poderlo descargar."
      );
      return;
    }
    setEstado("bajando");
    setMensaje("");
    try {
      const respuesta = await fetch(
        `/api/v1/exports/horarios?periodId=${encodeURIComponent(periodId)}&careerId=${encodeURIComponent(careerId)}`,
        { headers: { Authorization: `Bearer ${accessToken}` } }
      );
      if (!respuesta.ok) {
        const cuerpo = (await respuesta.json().catch(() => null)) as { message?: string } | null;
        throw new Error(
          cuerpo?.message ?? "No se pudo generar el libro de horarios."
        );
      }
      const blob = await respuesta.blob();
      // El nombre lo decide el servidor; si no llega, se arma uno razonable.
      const cabecera = respuesta.headers.get("content-disposition") ?? "";
      const delNombre = /filename="([^"]+)"/.exec(cabecera)?.[1];
      const nombre = delNombre ?? `Horarios ${careerName} ${periodName}.xlsx`;

      const url = URL.createObjectURL(blob);
      const enlace = document.createElement("a");
      enlace.href = url;
      enlace.download = nombre;
      document.body.appendChild(enlace);
      enlace.click();
      enlace.remove();
      URL.revokeObjectURL(url);
      setEstado("inactivo");
    } catch (error) {
      setEstado("error");
      setMensaje(error instanceof Error ? error.message : "No se pudo generar el libro.");
    }
  }

  return (
    <>
      <button
        className="export-button"
        type="button"
        onClick={() => void descargar()}
        disabled={deshabilitado}
        title={
          !hayPublicado
            ? "Primero publica el horario: el archivo sale de la versión publicada."
            : "Descargar el libro de horarios en Excel"
        }
      >
        {estado === "bajando" ? "Generando…" : "Exportar Excel"}
      </button>
      {estado === "error" && (
        <p className="export-error" role="alert">
          {mensaje}
          <button
            type="button"
            onClick={() => setEstado("inactivo")}
            aria-label="Cerrar aviso"
          >
            ×
          </button>
        </p>
      )}
    </>
  );
}