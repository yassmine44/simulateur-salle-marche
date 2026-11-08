package tn.esprit.simulateurbackend.dto;

import java.util.List;

public record AdminQuizQuestionResponse(

        Long id,

        String question,

        List<String> options,

        int correctIndex,

        String explanation

) {
}
