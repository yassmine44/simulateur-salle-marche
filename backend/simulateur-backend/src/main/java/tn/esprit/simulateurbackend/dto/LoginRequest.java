package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank
        @Email
        @Size(max = 150)
        String email,

        @NotBlank
        String password,

        @NotBlank(
                message = "La vérification reCAPTCHA est obligatoire"
        )
        String recaptchaToken


) {

    public LoginRequest {
        email = email == null
                ? null
                : email.trim();
    }

    @Override
    public String toString() {
        return "LoginRequest[redacted]";
    }
}