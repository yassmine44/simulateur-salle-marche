package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/*
 * Index de l'option choisie pour chaque question, dans l'ordre du quiz.
 * Une valeur null signifie « pas de réponse ».
 */
public record QuizSubmitRequest(

        @NotNull(message = "Les réponses sont obligatoires.")
        List<Integer> answers

) {
}
