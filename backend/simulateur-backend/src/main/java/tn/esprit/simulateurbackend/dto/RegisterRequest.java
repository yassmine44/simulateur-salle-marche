package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record RegisterRequest(

        @NotBlank
        @Size(max = 100)
        String firstName,

        @NotBlank
        @Size(max = 100)
        String lastName,

        @NotBlank
        @Pattern(
                regexp = "^[A-Za-z]{2}$",
                message = "Country code must contain exactly 2 letters"
        )
        String countryCode,

        @NotBlank
        @Size(max = 30)
        String phoneNumber,

        @NotBlank
        @Email
        @Size(max = 150)
        String email,

        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @NotBlank(
                message = "La vérification reCAPTCHA est obligatoire"
        )
        String recaptchaToken

) {

    public RegisterRequest {

        firstName = firstName == null
                ? null
                : firstName.trim();

        lastName = lastName == null
                ? null
                : lastName.trim();

        countryCode = countryCode == null
                ? null
                : countryCode
                .trim()
                .toUpperCase(Locale.ROOT);

        phoneNumber = phoneNumber == null
                ? null
                : phoneNumber.trim();

        email = email == null
                ? null
                : email.trim();
    }

    @Override
    public String toString() {
        return "RegisterRequest[redacted]";
    }
}