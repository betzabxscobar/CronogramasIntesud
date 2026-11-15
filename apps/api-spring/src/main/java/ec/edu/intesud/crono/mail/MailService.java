package ec.edu.intesud.crono.mail;

import ec.edu.intesud.crono.config.CronoProperties;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/** Envío de correo por SMTP. Si SMTP no está configurado, no envía y devuelve false. */
@Service
public class MailService {

  private static final Logger log = LoggerFactory.getLogger(MailService.class);

  private final JavaMailSender sender;
  private final String from;

  public MailService(CronoProperties properties) {
    CronoProperties.Smtp smtp = properties.smtp();
    this.from = smtp.from();
    this.sender = smtp.configured() ? createSender(smtp) : null;
  }

  /** Indica si hay un servidor SMTP configurado en el .env. */
  public boolean isConfigured() {
    return sender != null;
  }

  public boolean sendLoginCode(String email, String displayName, String code) {
    if (sender == null) {
      log.error("El servicio SMTP no está configurado");
      return false;
    }
    try {
      MimeMessage message = sender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
      helper.setFrom(new InternetAddress(from));
      helper.setTo(email);
      helper.setSubject("Código de acceso a CRONO-INTESUD");
      helper.setText(
          "Hola %s. Tu código de acceso es %s. Vence en 5 minutos y solo puede utilizarse una vez."
              .formatted(displayName, code),
          """
          <div style="font-family:Arial,sans-serif;color:#102332;max-width:560px;margin:auto">
            <p style="font-size:12px;letter-spacing:2px;text-transform:uppercase;color:#a44b18">
              CRONO-INTESUD
            </p>
            <h1 style="font-size:24px">Código de acceso</h1>
            <p>Hola %s, utiliza este código para ingresar:</p>
            <p style="font-size:36px;font-weight:700;letter-spacing:8px">%s</p>
            <p>Vence en 5 minutos y solo puede utilizarse una vez.</p>
            <p>Si no solicitaste este código, puedes ignorar este mensaje.</p>
          </div>
          """
              .formatted(HtmlUtils.htmlEscape(displayName), code));
      sender.send(message);
      return true;
    } catch (Exception exception) {
      log.error("No se pudo enviar el código de acceso", exception);
      return false;
    }
  }

  private static JavaMailSender createSender(CronoProperties.Smtp smtp) {
    JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
    mailSender.setHost(smtp.host());
    mailSender.setPort(smtp.port());
    mailSender.setUsername(smtp.user());
    mailSender.setPassword(smtp.password());
    mailSender.setDefaultEncoding("UTF-8");

    Properties mailProperties = mailSender.getJavaMailProperties();
    mailProperties.put("mail.smtp.auth", "true");
    mailProperties.put("mail.smtp.connectiontimeout", "10000");
    mailProperties.put("mail.smtp.timeout", "10000");
    // SMTP_SECURE=true es TLS directo (puerto 465); si no, se usa STARTTLS cuando hay.
    mailProperties.put(smtp.secure() ? "mail.smtp.ssl.enable" : "mail.smtp.starttls.enable", "true");
    return mailSender;
  }
}
