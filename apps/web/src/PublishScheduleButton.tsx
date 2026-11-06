import { useState } from "react";

/**
 * Publica el horario: congela la versión actual y abre la siguiente como
 * borrador con las mismas clases.
 *
 * Publicar es lo que habilita la exportación, y conviene que el estado se vea:
 * si el horario no está publicado, el archivo que se genera no saldría y la
 * persona se topa con un error sin entender por qué.
 */
export function PublishScheduleButton({
  accessToken,
  scheduleId,
  publishedVersion,
  onPublished
}: {
  accessToken: string;
  scheduleId: string;
  publishedVersion?: number | null;
  onPublished?: () => void;
}) {
  const [estado, setEstado] = useState<"inactivo" | "publicando" | "error">("inactivo");
  const [mensaje, setMensaje] = useState("");

  async function publicar() {
    setEstado("publicando");
    setMensaje("");
    try {
      const respuesta = await fetch(`/api/v1/schedules/${scheduleId}/publish`, {
        method: "POST",
        headers: { Authorization: `Bearer ${accessToken}` }
      });
      if (!respuesta.ok) {
        const cuerpo = (await respuesta.json().catch(() => null)) as { message?: string | string[] } | null;
        const texto = Array.isArray(cuerpo?.message)
          ? cuerpo.message.join(". ")
          : cuerpo?.message;
        throw new Error(texto ?? "No se pudo publicar el horario.");
      }
      setEstado("inactivo");
      onPublished?.();
    } catch (error) {
      setEstado("error");
      setMensaje(error instanceof Error ? error.message : "No se pudo publicar el horario.");
    }
  }

  const hayPublicado = typeof publishedVersion === "number";

  return (
    <>
      <button
        className={`publish-button${hayPublicado ? " is-published" : ""}`}
        type="button"
        onClick={() => void publicar()}
        disabled={estado === "publicando"}
        title={
          hayPublicado
            ? `La versión ${publishedVersion} está publicada y congelada. Al publicar otra vez se abre una nueva.`
            : "Publica este horario para poder exportarlo y repartirlo"
        }
      >
        {estado === "publicando"
          ? "Publicando…"
          : hayPublicado
            ? `Republicar (v${publishedVersion} publicada)`
            : "Publicar"}
      </button>
      {estado === "error" && (
        <p className="export-error" role="alert">
          {mensaje}
          <button type="button" onClick={() => setEstado("inactivo")} aria-label="Cerrar aviso">
            ×
          </button>
        </p>
      )}
    </>
  );
}