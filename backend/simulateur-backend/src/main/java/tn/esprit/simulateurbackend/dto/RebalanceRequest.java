package tn.esprit.simulateurbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/*
 * Répartition cible : part de la valeur totale à placer sur chaque
 * actif. Les actifs non cités ne sont pas touchés ; le reste du
 * total reste en liquidités.
 */
public record RebalanceRequest(

        @NotNull(message = "La répartition cible est obligatoire.")
        @Size(min = 1, max = 20, message = "Indiquez entre 1 et 20 actifs.")
        List<@Valid Target> targets
) {

    public record Target(

            @NotBlank(message = "Le symbole de l'actif est obligatoire.")
            @Size(max = 30, message = "Le symbole de l'actif ne doit pas dépasser 30 caractères.")
            String assetSymbol,

            @NotNull(message = "La part cible est obligatoire.")
            @DecimalMin(value = "0", message = "La part cible ne peut pas être négative.")
            @DecimalMax(value = "100", message = "La part cible ne peut pas dépasser 100 %.")
            BigDecimal targetPercent
    ) {
    }
}
