package com.aies.feature.inspeccionglp.data

import java.io.File
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/**
 * Equivalente Android de `EmailService.cs` (MailKit): mismo comportamiento — cuerpo en texto
 * plano, un solo adjunto, `SSLOnConnect` si `usarSsl` sino `STARTTLS`, remitente autenticado
 * único como From — sobre el puerto Android de JavaMail (android-mail/android-activation).
 * `Transport.send` es bloqueante: el llamador debe invocar esto en Dispatchers.IO.
 */
object EmailService {

    fun enviarInforme(
        smtpHost: String, smtpPuerto: Int, usuario: String, contraseña: String, usarSsl: Boolean,
        destinatarios: List<String>, asunto: String, cuerpo: String, rutaAdjunto: String?
    ) {
        val props = Properties().apply {
            put("mail.smtp.host", smtpHost)
            put("mail.smtp.port", smtpPuerto.toString())
            put("mail.smtp.auth", "true")
            if (usarSsl) {
                put("mail.smtp.socketFactory.port", smtpPuerto.toString())
                put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                put("mail.smtp.socketFactory.fallback", "false")
            } else {
                put("mail.smtp.starttls.enable", "true")
            }
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(usuario, contraseña)
        })

        val mensaje = MimeMessage(session).apply {
            setFrom(InternetAddress(usuario))
            destinatarios.forEach { addRecipient(Message.RecipientType.TO, InternetAddress(it)) }
            subject = asunto
        }

        val multipart = MimeMultipart().apply {
            addBodyPart(MimeBodyPart().apply { setText(cuerpo) })
        }

        if (!rutaAdjunto.isNullOrBlank()) {
            val archivo = File(rutaAdjunto)
            if (archivo.exists()) {
                multipart.addBodyPart(MimeBodyPart().apply {
                    dataHandler = DataHandler(FileDataSource(archivo))
                    fileName = archivo.name
                })
            }
        }

        mensaje.setContent(multipart)
        Transport.send(mensaje)
    }
}
