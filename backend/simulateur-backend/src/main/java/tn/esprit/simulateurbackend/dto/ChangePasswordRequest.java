package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

        @NotBlank(message = "Le mot de passe actuel est obligatoire")
        String currentPassword,

        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        @Size(
                min = 8,
                max = 72,
                message = "Le nouveau mot de passe doit contenir entre 8 et 72 caractères"
        )
        String newPassword,

        @NotBlank(message = "La confirmation du mot de passe est obligatoire")
        @Size(
                min = 8,
                max = 72,
                message = "La confirmation doit contenir entre 8 et 72 caractères"
        )
        String confirmPassword
) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[redacted]";
    }
}