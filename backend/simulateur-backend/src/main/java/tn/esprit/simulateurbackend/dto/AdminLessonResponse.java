package tn.esprit.simulateurbackend.dto;

import java.util.List;

public record AdminLessonResponse(

        Long id,

        String title,

        String content,

        int durationMinutes,

        int displayOrder,

        String videoUrl,

        List<AdminQuizQuestionResponse> quiz

) {
}