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
import tn.esprit.simulateurbackend.service.AuthenticationService;
import tn.esprit.simulateurbackend.service.RegistrationService;
import org.springframework.security.core.Authentication;
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final RegistrationService registration;
    private final AuthenticationService authentication;

    public AuthController(
            RegistrationService registration,
            AuthenticationService authentication
    ) {
        this.registration = registration;
        this.authentication = authentication;
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
}