package tn.esprit.simulateurbackend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import tn.esprit.simulateurbackend.dto.ForgotPasswordRequest;
import tn.esprit.simulateurbackend.dto.LoginRequest;
import tn.esprit.simulateurbackend.dto.LoginResponse;
import tn.esprit.simulateurbackend.dto.MessageResponse;
import tn.esprit.simulateurbackend.dto.RegisterRequest;
import tn.esprit.simulateurbackend.dto.ResetPasswordRequest;
import tn.esprit.simulateurbackend.dto.UserResponse;

import tn.esprit.simulateurbackend.entity.AuditEventType;

import tn.esprit.simulateurbackend.security.RateLimitService;
import tn.esprit.simulateurbackend.security.RecaptchaService;

import tn.esprit.simulateurbackend.service.AuditService;
import tn.esprit.simulateurbackend.service.AuthenticationService;
import tn.esprit.simulateurbackend.service.EmailPasswordResetDeliveryService;
import tn.esprit.simulateurbackend.service.PasswordResetService;
import tn.esprit.simulateurbackend.service.RegistrationService;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registration;

    private final AuthenticationService authentication;

    private final PasswordResetService passwordResetService;

    private final EmailPasswordResetDeliveryService
            passwordResetDelivery;

    private final RecaptchaService recaptchaService;

    private final RateLimitService rateLimitService;

    private final AuditService auditService;


    public AuthController(
            RegistrationService registration,
            AuthenticationService authentication,
            PasswordResetService passwordResetService,
            EmailPasswordResetDeliveryService passwordResetDelivery,
            RecaptchaService recaptchaService,
            RateLimitService rateLimitService,
            AuditService auditService
    ) {

        this.registration =
                registration;

        this.authentication =
                authentication;

        this.passwordResetService =
                passwordResetService;

        this.passwordResetDelivery =
                passwordResetDelivery;

        this.recaptchaService =
                recaptchaService;

        this.rateLimitService =
                rateLimitService;

        this.auditService =
                auditService;
    }


    /*
     * ==========================================
     * REGISTER
     * ==========================================
     */

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {

        /*
         * 1. Rate limiting
         */
        rateLimitService.checkRegister(
                httpRequest
        );


        /*
         * 2. reCAPTCHA
         */
        recaptchaService.verify(
                request.recaptchaToken(),
                "register",
                httpRequest
        );


        /*
         * 3. Inscription
         */
        try {

            UserResponse response =
                    registration.register(
                            request
                    );


            /*
             * AUDIT : REGISTER SUCCESS
             */
            auditService.log(
                    AuditEventType.REGISTER_SUCCESS,
                    null,
                    request.email(),
                    null,
                    true,
                    "Création de compte réussie.",
                    httpRequest
            );


            return response;


        } catch (RuntimeException exception) {

            /*
             * AUDIT : REGISTER FAILED
             *
             * Important :
             * on ne stocke jamais le mot de passe.
             */
            auditService.log(
                    AuditEventType.REGISTER_FAILED,
                    null,
                    request.email(),
                    null,
                    false,
                    "Échec de création du compte.",
                    httpRequest
            );


            throw exception;
        }
    }


    /*
     * ==========================================
     * LOGIN
     * ==========================================
     */

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {

        /*
         * LOGIN_SUCCESS et LOGIN_FAILED
         * sont maintenant enregistrés dans
         * AuthenticationService.
         */

        rateLimitService.checkLogin(
                httpRequest
        );


        recaptchaService.verify(
                request.recaptchaToken(),
                "login",
                httpRequest
        );


        return authentication.login(
                request,
                httpRequest,
                httpResponse
        );
    }


    /*
     * ==========================================
     * CURRENT USER
     * ==========================================
     */

    @GetMapping("/me")
    public LoginResponse me(
            Authentication authentication
    ) {

        return this.authentication
                .currentUser(
                        authentication
                );
    }


    /*
     * ==========================================
     * LOGOUT
     * ==========================================
     */

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            HttpServletRequest request,
            Authentication authentication
    ) {

        /*
         * Il faut récupérer l'identité AVANT
         * de détruire la session.
         */
        String email = null;


        if (
                authentication != null &&
                        authentication.isAuthenticated()
        ) {

            email =
                    authentication.getName();
        }


        /*
         * AUDIT : LOGOUT
         */
        auditService.log(
                AuditEventType.LOGOUT,
                null,
                email,
                email,
                true,
                "Déconnexion utilisateur.",
                request
        );


        var session =
                request.getSession(false);


        if (session != null) {

            session.invalidate();
        }
    }


    /*
     * ==========================================
     * FORGOT PASSWORD
     * ==========================================
     */

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest
    ) {

        /*
         * 1. Rate limiting
         */
        rateLimitService.checkForgotPassword(
                httpRequest
        );


        /*
         * 2. reCAPTCHA
         */
        recaptchaService.verify(
                request.recaptchaToken(),
                "forgot_password",
                httpRequest
        );


        try {

            /*
             * On garde volontairement la réponse
             * générique pour empêcher
             * l'énumération des comptes.
             */
            passwordResetService
                    .createResetToken(
                            request.email()
                    )
                    .ifPresent(token ->

                            passwordResetDelivery.deliver(
                                    request.email(),
                                    token
                            )
                    );


            /*
             * AUDIT :
             * demande de réinitialisation acceptée.
             *
             * success=true signifie que la requête
             * a été traitée, PAS que l'adresse
             * existe forcément.
             */
            auditService.log(
                    AuditEventType.PASSWORD_RESET_REQUEST,
                    null,
                    request.email(),
                    null,
                    true,
                    "Demande de réinitialisation de mot de passe traitée.",
                    httpRequest
            );


        } catch (RuntimeException exception) {

            auditService.log(
                    AuditEventType.PASSWORD_RESET_REQUEST,
                    null,
                    request.email(),
                    null,
                    false,
                    "Erreur pendant le traitement de la demande de réinitialisation.",
                    httpRequest
            );


            throw exception;
        }


        return new MessageResponse(
                "Si un compte correspond à cette adresse, un lien de réinitialisation a été envoyé."
        );
    }


    /*
     * ==========================================
     * RESET PASSWORD
     * ==========================================
     */

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest
    ) {

        try {

            passwordResetService.resetPassword(
                    request
            );


            /*
             * AUDIT : PASSWORD RESET SUCCESS
             *
             * Pour l'instant nous ne mettons pas
             * l'e-mail car le ResetPasswordRequest
             * utilise normalement le token.
             *
             * On récupérera l'utilisateur directement
             * depuis PasswordResetService au prochain step.
             */
            auditService.log(
                    AuditEventType.PASSWORD_RESET_SUCCESS,
                    null,
                    null,
                    null,
                    true,
                    "Mot de passe réinitialisé avec succès.",
                    httpRequest
            );


        } catch (RuntimeException exception) {

            /*
             * On utilise le même événement avec
             * success=false.
             */
            auditService.log(
                    AuditEventType.PASSWORD_RESET_SUCCESS,
                    null,
                    null,
                    null,
                    false,
                    "Échec de réinitialisation du mot de passe.",
                    httpRequest
            );


            throw exception;
        }


        return new MessageResponse(
                "Votre mot de passe a été réinitialisé avec succès."
        );
    }
}