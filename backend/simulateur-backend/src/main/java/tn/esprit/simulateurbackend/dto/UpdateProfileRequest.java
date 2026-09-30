package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record UpdateProfileRequest(

        @NotBlank(message = "Le prénom est obligatoire")
        @Size(max = 100, message = "Le prénom ne doit pas dépasser 100 caractères")
        String firstName,

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        String lastName,

        @NotBlank(message = "Le pays est obligatoire")
        @Pattern(
                regexp = "^[A-Za-z]{2}$",
                message = "Le code pays doit contenir exactement 2 lettres"
        )
        String countryCode,

        @NotBlank(message = "Le numéro de téléphone est obligatoire")
        @Size(max = 30, message = "Le numéro de téléphone est trop long")
        String phoneNumber
) {

    public UpdateProfileRequest {

        firstName = firstName == null
                ? null
                : firstName.trim();

        lastName = lastName == null
                ? null
                : lastName.trim();

        countryCode = countryCode == null
                ? null
                : countryCode.trim().toUpperCase(Locale.ROOT);

        phoneNumber = phoneNumber == null
                ? null
                : phoneNumber.trim();
    }
}