package tn.esprit.simulateurbackend.dto.actuarial;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;

/**
 * Stress test du portefeuille d'assurance : chocs sur les sinistres, primes inchangées.
 *
 * @param frequencyShock +0,20 = 20 % de sinistres en plus
 * @param severityShock  +0,10 = sinistres 10 % plus chers
 * @param segmentation   by_risk_class, by_area ou by_bonus_malus
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StressRequest(

        @DecimalMin(value = "-0.5", message = "Le choc de fréquence minimal est −50 %.")
        @DecimalMax(value = "2.0", message = "Le choc de fréquence maximal est +200 %.")
        Double frequencyShock,

        @DecimalMin(value = "-0.5", message = "Le choc de coût minimal est −50 %.")
        @DecimalMax(value = "2.0", message = "Le choc de coût maximal est +200 %.")
        Double severityShock,

        @Pattern(regexp = "by_risk_class|by_area|by_bonus_malus", message = "Segmentation inconnue.")
        String segmentation
) {
}
