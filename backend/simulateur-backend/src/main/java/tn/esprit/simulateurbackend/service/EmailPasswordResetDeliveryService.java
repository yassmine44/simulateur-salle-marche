package tn.esprit.simulateurbackend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailPasswordResetDeliveryService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    public EmailPasswordResetDeliveryService(
            JavaMailSender mailSender
    ) {
        this.mailSender = mailSender;
    }

    public void deliver(
            String email,
            String rawToken
    ) {

        String resetUrl =
                frontendBaseUrl
                        + "/reset-password?token="
                        + rawToken;

        try {

            MimeMessage message =
                    mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            message,
                            true,
                            "UTF-8"
                    );

            helper.setFrom(
                    fromEmail
            );

            helper.setTo(
                    email
            );

            helper.setSubject(
                    "Réinitialisation de votre mot de passe - Trading Room"
            );

            helper.setText(
                    buildEmailContent(resetUrl),
                    true
            );

            mailSender.send(
                    message
            );

        } catch (MessagingException exception) {

            throw new IllegalStateException(
                    "Impossible d'envoyer l'e-mail de réinitialisation.",
                    exception
            );
        }
    }


    private String buildEmailContent(
            String resetUrl
    ) {

        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                    <meta charset="UTF-8">
                </head>

                <body style="
                    margin:0;
                    padding:0;
                    background:#f5f7fb;
                    font-family:Arial,sans-serif;
                    color:#0f172a;
                ">

                    <div style="
                        max-width:600px;
                        margin:40px auto;
                        background:#ffffff;
                        border-radius:14px;
                        padding:32px;
                        border:1px solid #e2e8f0;
                    ">

                        <div style="
                            font-size:20px;
                            font-weight:bold;
                            color:#1d4ed8;
                            margin-bottom:24px;
                        ">
                            Trading Room
                        </div>

                        <h2 style="
                            margin-bottom:12px;
                        ">
                            Réinitialisation de votre mot de passe
                        </h2>

                        <p style="
                            color:#64748b;
                            line-height:1.6;
                        ">
                            Une demande de réinitialisation du mot de passe
                            a été effectuée pour votre compte.
                        </p>

                        <p style="
                            color:#64748b;
                            line-height:1.6;
                        ">
                            Cliquez sur le bouton ci-dessous pour choisir
                            un nouveau mot de passe.
                        </p>

                        <div style="
                            margin:30px 0;
                            text-align:center;
                        ">

                            <a href="%s"
                               style="
                                   display:inline-block;
                                   padding:13px 22px;
                                   background:#1d4ed8;
                                   color:#ffffff;
                                   text-decoration:none;
                                   border-radius:8px;
                                   font-weight:bold;
                               ">
                                Réinitialiser mon mot de passe
                            </a>

                        </div>

                        <p style="
                            color:#64748b;
                            font-size:13px;
                        ">
                            Ce lien expire dans 30 minutes et ne peut être
                            utilisé qu'une seule fois.
                        </p>

                        <p style="
                            color:#64748b;
                            font-size:13px;
                        ">
                            Si vous n'avez pas demandé cette modification,
                            vous pouvez ignorer cet e-mail.
                        </p>

                        <hr style="
                            border:0;
                            border-top:1px solid #e2e8f0;
                            margin:28px 0;
                        ">

                        <p style="
                            color:#94a3b8;
                            font-size:11px;
                        ">
                            Trading Room - Intelligent Market Simulator
                        </p>

                    </div>

                </body>
                </html>
                """.formatted(resetUrl);
    }
}