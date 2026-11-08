package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminQuizQuestionRequest(

        @NotBlank(message = "L'énoncé de la question est obligatoire.")
        @Size(max = 500, message = "L'énoncé ne doit pas dépasser 500 caractères.")
        String question,

        @NotNull(message = "Les options sont obligatoires.")
        @Size(min = 2, max = 6, message = "Une question a entre 2 et 6 options.")
        List<@NotBlank(message = "Une option ne peut pas être vide.")
        @Size(max = 300, message = "Une option ne doit pas dépasser 300 caractères.")
                String> options,

        @NotNull(message = "La bonne réponse est obligatoire.")
        Integer correctIndex,

        @Size(max = 600, message = "L'explication ne doit pas dépasser 600 caractères.")
        String explanation

) {
}

