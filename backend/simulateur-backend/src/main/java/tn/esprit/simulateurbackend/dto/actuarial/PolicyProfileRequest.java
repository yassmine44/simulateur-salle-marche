package tn.esprit.simulateurbackend.dto.actuarial;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Facteurs de tarification d'un assuré.
 *
 * <p>Les bornes et valeurs acceptées reprennent exactement celles du moteur
 * actuariel (ml-service, {@code actuarial/schema.py}) : une catégorie jamais
 * vue à l'entraînement est refusée plutôt que tarifée avec un effet par défaut.
 */
public record PolicyProfileRequest(

        @NotNull(message = "L'âge du conducteur est obligatoire.")
        @Min(value = 18, message = "L'âge du conducteur doit être d'au moins 18 ans.")
        @Max(value = 100, message = "L'âge du conducteur ne peut pas dépasser 100 ans.")
        Integer driverAge,

        @NotNull(message = "Le coefficient bonus-malus est obligatoire.")
        @Min(value = 50, message = "Le bonus-malus minimal est 50.")
        @Max(value = 230, message = "Le bonus-malus maximal est 230.")
        Integer bonusMalus,

        @NotNull(message = "L'âge du véhicule est obligatoire.")
        @Min(value = 0, message = "L'âge du véhicule ne peut pas être négatif.")
        @Max(value = 100, message = "L'âge du véhicule ne peut pas dépasser 100 ans.")
        Integer vehicleAge,

        @NotNull(message = "La puissance du véhicule est obligatoire.")
        @Min(value = 4, message = "La puissance minimale est 4.")
        @Max(value = 15, message = "La puissance maximale est 15.")
        Integer vehiclePower,

        @NotNull(message = "La densité de population est obligatoire.")
        @Min(value = 1, message = "La densité minimale est 1 habitant/km².")
        @Max(value = 30000, message = "La densité maximale est 30 000 habitants/km².")
        Integer density,

        @NotNull(message = "La zone est obligatoire.")
        @Pattern(regexp = "[A-F]", message = "La zone doit être comprise entre A et F.")
        String area,

        @NotNull(message = "La marque du véhicule est obligatoire.")
        @Pattern(regexp = "B(1|2|3|4|5|6|10|11|12|13|14)", message = "Marque de véhicule inconnue.")
        String vehicleBrand,

        @NotNull(message = "Le carburant est obligatoire.")
        @Pattern(regexp = "Diesel|Regular", message = "Le carburant doit être Diesel ou Regular.")
        String vehicleGas,

        @NotNull(message = "La région est obligatoire.")
        @Pattern(
                regexp = "R(11|21|22|23|24|25|26|31|41|42|43|52|53|54|72|73|74|82|83|91|93|94)",
                message = "Région inconnue."
        )
        String region
) {
}
