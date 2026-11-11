package com.cimse.seguridad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.sql.Timestamp;

/**
 * Envio de la clave temporal por correo electronico. Si el servidor de correo no esta configurado
 * (variable MAIL_HOST) o el envio falla, devuelve false y el administrador entrega la clave personalmente.
 */
@Service
public class CorreoService {

    private static final Logger LOG = LoggerFactory.getLogger(CorreoService.class);

    private final ObjectProvider<JavaMailSender> remitente;
    private final String host;
    private final String usuarioSmtp;
    private final String desde;
    private final String urlApp;

    public CorreoService(ObjectProvider<JavaMailSender> remitente,
                         @Value("${spring.mail.host:}") String host,
                         @Value("${spring.mail.username:}") String usuarioSmtp,
                         @Value("${cimse.mail.from:}") String desde,
                         @Value("${cimse.app-url:http://localhost:5173}") String urlApp) {
        this.remitente = remitente;
        this.host = host;
        this.usuarioSmtp = usuarioSmtp;
        this.desde = desde;
        this.urlApp = urlApp;
    }

    public boolean configurado() {
        return host != null && !host.isBlank();
    }

    public boolean enviarClaveTemporal(String destino, String nombre, String usuario, String clave,
                                       Timestamp vence, boolean reinicio) {
        JavaMailSender sender = remitente.getIfAvailable();
        if (!configurado() || sender == null || destino == null || destino.isBlank()) {
            LOG.warn("Correo no enviado a {}: el servidor de correo no esta configurado.", destino);
            return false;
        }
        try {
            String fecha = new SimpleDateFormat("dd/MM/yyyy HH:mm").format(vence);
            SimpleMailMessage m = new SimpleMailMessage();
            String remit = !desde.isBlank() ? desde : (!usuarioSmtp.isBlank() ? usuarioSmtp : "no-reply@cimse.local");
            m.setFrom(remit);
            m.setTo(destino);
            m.setSubject(reinicio ? "CIMSE: su contrasena fue restablecida" : "CIMSE: su acceso al sistema");
            m.setText("Hola " + nombre + ",\n\n"
                    + (reinicio ? "El administrador restablecio su contrasena.\n" : "Se creo su usuario en el Sistema Web CIMSE.\n")
                    + "\nUsuario: " + usuario
                    + "\nContrasena temporal: " + clave
                    + "\n\nIngrese en " + urlApp + " y cambie la contrasena antes del " + fecha
                    + ". Despues de esa fecha la clave temporal deja de funcionar y debera pedir al administrador que la restablezca."
                    + "\n\nLa nueva contrasena debe tener minimo 8 caracteres, una mayuscula, un numero y un caracter especial."
                    + "\n\nCIMSE Seguridad Privada");
            sender.send(m);
            return true;
        } catch (Exception e) {
            LOG.warn("No se pudo enviar el correo a {}: {}", destino, e.getMessage());
            return false;
        }
    }

    /** Avisa por correo a los administradores que una cuenta se bloqueo por intentos fallidos. */
    public boolean avisarBloqueo(java.util.List<String> destinos, String usuario, String nombre) {
        JavaMailSender sender = remitente.getIfAvailable();
        if (!configurado() || sender == null || destinos == null || destinos.isEmpty()) {
            LOG.warn("Aviso de bloqueo de {} no enviado por correo (sin servidor de correo o sin administradores con correo).", usuario);
            return false;
        }
        boolean alguno = false;
        String remit = !desde.isBlank() ? desde : (!usuarioSmtp.isBlank() ? usuarioSmtp : "no-reply@cimse.local");
        for (String destino : destinos) {
            try {
                SimpleMailMessage m = new SimpleMailMessage();
                m.setFrom(remit);
                m.setTo(destino);
                m.setSubject("CIMSE: cuenta bloqueada por intentos fallidos (" + usuario + ")");
                m.setText("La cuenta del usuario " + usuario + " (" + nombre + ") se bloqueo despues de 3 intentos fallidos de inicio de sesion."
                        + "\n\nIngrese a " + urlApp + " > Seguridad > Usuarios para desbloquearla o enviarle una nueva clave temporal."
                        + "\n\nSi usted no reconoce esta actividad, conviene revisar la auditoria del sistema."
                        + "\n\nCIMSE Seguridad Privada");
                sender.send(m);
                alguno = true;
            } catch (Exception e) {
                LOG.warn("No se pudo avisar el bloqueo a {}: {}", destino, e.getMessage());
            }
        }
        return alguno;
    }
}
