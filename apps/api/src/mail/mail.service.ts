import { Injectable, Logger } from "@nestjs/common";
import { ConfigService } from "@nestjs/config";
import nodemailer, { type Transporter } from "nodemailer";

@Injectable()
export class MailService {
  private readonly logger = new Logger(MailService.name);
  private readonly transporter: Transporter | null;

  constructor(private readonly config: ConfigService) {
    const host = this.config.get<string>("SMTP_HOST");
    const user = this.config.get<string>("SMTP_USER");
    const password = this.config.get<string>("SMTP_PASSWORD");

    this.transporter =
      host && user && password
        ? nodemailer.createTransport({
            host,
            port: Number(this.config.get<string>("SMTP_PORT", "587")),
            secure: this.config.get<string>("SMTP_SECURE", "false") === "true",
            auth: { user, pass: password }
          })
        : null;
  }

  async sendLoginCode(email: string, displayName: string, code: string) {
    if (!this.transporter) {
      this.logger.error("El servicio SMTP no está configurado");
      return false;
    }

    try {
      const safeName = this.escapeHtml(displayName);
      await this.transporter.sendMail({
        from: this.config.getOrThrow<string>("SMTP_FROM"),
        to: email,
        subject: "Código de acceso a CRONO-INTESUD",
        text: `Hola ${displayName}. Tu código de acceso es ${code}. Vence en 5 minutos y solo puede utilizarse una vez.`,
        html: `
          <div style="font-family:Arial,sans-serif;color:#102332;max-width:560px;margin:auto">
            <p style="font-size:12px;letter-spacing:2px;text-transform:uppercase;color:#a44b18">
              CRONO-INTESUD
            </p>
            <h1 style="font-size:24px">Código de acceso</h1>
            <p>Hola ${safeName}, utiliza este código para ingresar:</p>
            <p style="font-size:36px;font-weight:700;letter-spacing:8px">${code}</p>
            <p>Vence en 5 minutos y solo puede utilizarse una vez.</p>
            <p>Si no solicitaste este código, puedes ignorar este mensaje.</p>
          </div>
        `
      });
      return true;
    } catch (error: unknown) {
      this.logger.error(
        "No se pudo enviar el código de acceso",
        error instanceof Error ? error.stack : undefined
      );
      return false;
    }
  }

  private escapeHtml(value: string) {
    return value.replace(
      /[&<>"']/g,
      (character) =>
        ({
          "&": "&amp;",
          "<": "&lt;",
          ">": "&gt;",
          '"': "&quot;",
          "'": "&#039;"
        })[character] ?? character
    );
  }
}
