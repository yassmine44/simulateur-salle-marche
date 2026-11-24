package tn.esprit.simulateurbackend.dto.actuarial;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Demande de devis.
 *
 * @param profile     facteurs de tarification de l'assuré
 * @param years       horizon (ans) de la probabilité de sinistre ; la prime reste annuelle
 * @param assumptions hypothèses commerciales ; absentes = hypothèses enregistrées
 * @param explain     joindre l'explication XAI (par défaut : oui)
 * @param top         nombre de facteurs expliqués (par défaut : tous)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuoteRequest(

        @NotNull(message = "Le profil de l'assuré est obligatoire.")
        @Valid
        PolicyProfileRequest profile,

        @DecimalMin(value = "0.0", inclusive = false, message = "L'horizon doit être positif.")
        @DecimalMax(value = "10.0", message = "L'horizon ne peut pas dépasser 10 ans.")
        Double years,

        @Valid
        PricingAssumptionsDto assumptions,

        Boolean explain,

        @Min(value = 1, message = "Au moins un facteur doit être expliqué.")
        @Max(value = 9, message = "Le modèle n'a que 9 facteurs.")
        Integer top
) {

    public QuoteRequest {
        years = years == null ? 1.0 : years;
        explain = explain == null ? Boolean.TRUE : explain;
    }
}
