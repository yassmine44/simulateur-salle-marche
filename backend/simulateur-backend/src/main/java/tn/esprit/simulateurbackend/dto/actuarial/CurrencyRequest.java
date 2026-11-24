package tn.esprit.simulateurbackend.dto.actuarial;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Devise d'affichage des montants (administration).
 *
 * @param code          code ISO 4217 (ex. TND)
 * @param label         libellé (ex. dinar tunisien)
 * @param rateFromModel unités de devise pour 1 euro (devise des modèles)
 * @param source        origine du taux (ex. cours moyen BCT)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CurrencyRequest(

        @NotNull(message = "Le code de devise est obligatoire.")
        @Pattern(regexp = "[A-Z]{3}", message = "Code de devise ISO à 3 lettres attendu (ex. TND).")
        String code,

        @NotBlank(message = "Le libellé de la devise est obligatoire.")
        @Size(max = 60, message = "Libellé trop long.")
        String label,

        @NotNull(message = "Le taux de conversion est obligatoire.")
        @DecimalMin(value = "0.0", inclusive = false, message = "Le taux doit être positif.")
        Double rateFromModel,

        @Size(max = 200, message = "Source trop longue.")
        String source
) {
}
