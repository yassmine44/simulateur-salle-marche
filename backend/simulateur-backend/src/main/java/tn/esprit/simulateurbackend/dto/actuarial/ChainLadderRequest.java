package tn.esprit.simulateurbackend.dto.actuarial;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Triangle de développement cumulé fourni par l'utilisateur.
 *
 * <p>La ligne i contient les montants cumulés payés de l'année d'origine i,
 * jusqu'à la diagonale (les cellules futures sont absentes ou {@code null}).
 * La cohérence de la forme (carré, diagonale, montants positifs) est vérifiée
 * par le moteur actuariel, qui renvoie un message précis.
 *
 * @param origins         années d'origine (une par ligne, optionnel)
 * @param triangle        montants cumulés, ligne par ligne
 * @param confidenceLevel niveau de l'intervalle de confiance (0,95 par défaut)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChainLadderRequest(

        List<Integer> origins,

        @NotNull(message = "Le triangle est obligatoire.")
        @Size(min = 3, max = 30, message = "Le triangle doit compter entre 3 et 30 années d'origine.")
        List<List<Double>> triangle,

        @DecimalMin(value = "0.5", inclusive = false, message = "Le niveau de confiance doit dépasser 50 %.")
        @DecimalMax(value = "1.0", inclusive = false, message = "Le niveau de confiance doit être inférieur à 100 %.")
        Double confidenceLevel
) {
}
