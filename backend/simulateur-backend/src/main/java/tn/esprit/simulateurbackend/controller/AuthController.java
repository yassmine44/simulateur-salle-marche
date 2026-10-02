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

import tn.esprit.simulateurbackend.security.RateLimitService;
import tn.esprit.simulateurbackend.security.RecaptchaService;

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


    public AuthController(
            RegistrationService registration,
            AuthenticationService authentication,
            PasswordResetService passwordResetService,
            EmailPasswordResetDeliveryService passwordResetDelivery,
            RecaptchaService recaptchaService,
            RateLimitService rateLimitService
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
         *
         * Bloque les requêtes excessives
         * avant même d'appeler Google reCAPTCHA.
         */
        rateLimitService.checkRegister(
                httpRequest
        );


        /*
         * 2. reCAPTCHA Enterprise
         */
        recaptchaService.verify(
                request.recaptchaToken(),
                "register",
                httpRequest
        );


        /*
         * 3. Création du compte
         */
        return registration.register(
                request
        );
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
         * 1. Rate limiting
         */
        rateLimitService.checkLogin(
                httpRequest
        );


        /*
         * 2. reCAPTCHA Enterprise
         */
        recaptchaService.verify(
                request.recaptchaToken(),
                "login",
                httpRequest
        );


        /*
         * 3. Authentification
         */
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
            HttpServletRequest request
    ) {

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
         *
         * Important ici pour empêcher
         * le spam d'e-mails.
         */
        rateLimitService.checkForgotPassword(
                httpRequest
        );


        /*
         * 2. reCAPTCHA Enterprise
         */
        recaptchaService.verify(
                request.recaptchaToken(),
                "forgot_password",
                httpRequest
        );


        /*
         * 3. Génération du token
         * et envoi de l'e-mail.
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
         * Réponse volontairement générique
         * pour éviter l'énumération
         * des comptes existants.
         */
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
            @Valid @RequestBody ResetPasswordRequest request
    ) {

        passwordResetService.resetPassword(
                request
        );


        return new MessageResponse(
                "Votre mot de passe a été réinitialisé avec succès."
        );
    }
}