package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(

        @NotBlank(
                message = "L'adresse e-mail est obligatoire"
        )
        @Email(
                message = "L'adresse e-mail est invalide"
        )
        @Size(
                max = 150,
                message = "L'adresse e-mail est trop longue"
        )
        String email,

        @NotBlank(
                message = "La vérification reCAPTCHA est obligatoire"
        )
        String recaptchaToken

) {

    public ForgotPasswordRequest {

        email = email == null
                ? null
                : email.trim();
    }
}