package tn.esprit.simulateurbackend.dto.actuarial;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Pourquoi l'assuré A paie-t-il plus (ou moins) que l'assuré B ? */
public record ComparisonRequest(

        @NotNull(message = "Le profil A est obligatoire.")
        @Valid
        PolicyProfileRequest profileA,

        @NotNull(message = "Le profil B est obligatoire.")
        @Valid
        PolicyProfileRequest profileB
) {
}
