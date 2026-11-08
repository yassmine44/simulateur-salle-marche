package tn.esprit.simulateurbackend.dto;

import java.util.List;

/*
 * Question de quiz envoyée au client : la bonne réponse n'y figure pas.
 */
public record FormationQuizQuestionResponse(

        Long id,

        String question,

        List<String> options

) {
}
