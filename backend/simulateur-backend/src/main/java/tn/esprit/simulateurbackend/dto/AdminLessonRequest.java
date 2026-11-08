package tn.esprit.simulateurbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record AdminLessonRequest(

        @NotBlank(message = "Le titre de la leçon est obligatoire.")
        @Size(max = 200, message = "Le titre ne doit pas dépasser 200 caractères.")
        String title,

        @NotBlank(message = "Le contenu de la leçon est obligatoire.")
        @Size(max = 20000, message = "Le contenu ne doit pas dépasser 20 000 caractères.")
        String content,

        @NotNull(message = "La durée est obligatoire.")
        @Min(value = 1, message = "La durée minimale est de 1 minute.")
        @Max(value = 240, message = "La durée maximale est de 240 minutes.")
        Integer durationMinutes,

        @NotNull(message = "L'ordre est obligatoire.")
        @Min(value = 0, message = "L'ordre ne peut pas être négatif.")
        Integer displayOrder,

        // Facultatif : lien YouTube.
        @Size(max = 500, message = "Le lien vidéo ne doit pas dépasser 500 caractères.")
        String videoUrl,

        @NotNull(message = "Le quiz est obligatoire (liste vide si aucun quiz).")
        @Size(max = 20, message = "Un quiz contient 20 questions au maximum.")
        List<@Valid AdminQuizQuestionRequest> quiz

) {
}
