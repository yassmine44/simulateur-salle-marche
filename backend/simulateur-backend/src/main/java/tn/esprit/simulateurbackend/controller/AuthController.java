package tn.esprit.simulateurbackend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import tn.esprit.simulateurbackend.dto.LoginRequest;
import tn.esprit.simulateurbackend.dto.LoginResponse;
import tn.esprit.simulateurbackend.dto.RegisterRequest;
import tn.esprit.simulateurbackend.dto.UserResponse;
import tn.esprit.simulateurbackend.service.*;
import org.springframework.security.core.Authentication;

import tn.esprit.simulateurbackend.dto.ForgotPasswordRequest;
import tn.esprit.simulateurbackend.dto.ResetPasswordRequest;
import tn.esprit.simulateurbackend.dto.MessageResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registration;
    private final AuthenticationService authentication;

    private final PasswordResetService passwordResetService;
    private final EmailPasswordResetDeliveryService passwordResetDelivery;
    public AuthController(
            RegistrationService registration,
            AuthenticationService authentication,
            PasswordResetService passwordResetService,
            EmailPasswordResetDeliveryService passwordResetDelivery
    ) {
        this.registration = registration;
        this.authentication = authentication;
        this.passwordResetService = passwordResetService;
        this.passwordResetDelivery = passwordResetDelivery;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return registration.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        return authentication.login(
                request,
                httpRequest,
                httpResponse
        );
    }
    @GetMapping("/me")
    public LoginResponse me(Authentication authentication) {
        return this.authentication.currentUser(authentication);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            HttpServletRequest request
    ) {
        var session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }
    }
    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {

        passwordResetService
                .createResetToken(request.email())
                .ifPresent(token ->
                        passwordResetDelivery.deliver(
                                request.email(),
                                token
                        )
                );

        return new MessageResponse(
                "Si un compte correspond à cette adresse, un lien de réinitialisation a été envoyé."
        );
    }
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