package tn.esprit.simulateurbackend.dto.actuarial;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Hypothèses commerciales : prime commerciale =
 * prime pure × (1 + sécurité) / (1 − frais − bénéfice).
 *
 * <p>En réponse, {@code commercialFactor} donne le multiplicateur obtenu ;
 * il est ignoré en requête.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PricingAssumptionsDto(

        @NotNull(message = "Le taux de frais est obligatoire.")
        @DecimalMin(value = "0.0", message = "Le taux de frais ne peut pas être négatif.")
        @DecimalMax(value = "1.0", inclusive = false, message = "Le taux de frais doit être inférieur à 100 %.")
        Double expenseRatio,

        @NotNull(message = "Le taux de bénéfice est obligatoire.")
        @DecimalMin(value = "0.0", message = "Le taux de bénéfice ne peut pas être négatif.")
        @DecimalMax(value = "1.0", inclusive = false, message = "Le taux de bénéfice doit être inférieur à 100 %.")
        Double profitRatio,

        @NotNull(message = "Le chargement de sécurité est obligatoire.")
        @DecimalMin(value = "0.0", message = "Le chargement de sécurité ne peut pas être négatif.")
        @DecimalMax(value = "1.0", message = "Le chargement de sécurité ne peut pas dépasser 100 %.")
        Double safetyLoading,

        Double commercialFactor
) {

    public static PricingAssumptionsDto of(double expenseRatio, double profitRatio, double safetyLoading) {
        return new PricingAssumptionsDto(expenseRatio, profitRatio, safetyLoading, null);
    }

    /** Frais + bénéfice doivent laisser une part strictement positive aux sinistres. */
    @JsonIgnore
    @AssertTrue(message = "La somme frais + bénéfice doit être strictement inférieure à 100 %.")
    public boolean isRatioSumValid() {
        return expenseRatio == null || profitRatio == null || expenseRatio + profitRatio < 1.0;
    }
}
