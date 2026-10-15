package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/*
 * Protection d'une position détenue : une vente take-profit
 * au-dessus du cours et une vente stop-loss en dessous.
 * La première exécutée annule l'autre (One-Cancels-the-Other).
 */
public record CreateOcoRequest(

        @NotBlank(message = "Le symbole de l'actif est obligatoire.")
        @Size(max = 30, message = "Le symbole de l'actif ne doit pas dépasser 30 caractères.")
        String assetSymbol,

        @NotNull(message = "La quantité est obligatoire.")
        @DecimalMin(value = "0.00000001", message = "La quantité doit être strictement positive.")
        BigDecimal quantity,

        @NotNull(message = "Le prix du take-profit est obligatoire.")
        @DecimalMin(value = "0.00000001", message = "Le prix du take-profit doit être strictement positif.")
        BigDecimal takeProfitPrice,

        @NotNull(message = "Le prix du stop-loss est obligatoire.")
        @DecimalMin(value = "0.00000001", message = "Le prix du stop-loss doit être strictement positif.")
        BigDecimal stopLossPrice,

        // Facultatif : le stop-loss devient un stop suiveur.
        @DecimalMin(value = "0.1", message = "L'écart du stop suiveur doit être d'au moins 0,1 %.")
        @DecimalMax(value = "50", message = "L'écart du stop suiveur ne peut pas dépasser 50 %.")
        BigDecimal trailPercent
) {
}
