package tn.esprit.simulateurbackend.dto;

import java.util.List;

public record FormationLessonResponse(

        Long id,

        Long courseId,

        String courseTitle,

        String title,

        String content,

        int durationMinutes,

        String videoUrl,

        boolean completed,

        Integer bestScorePercent,

        Long previousLessonId,

        Long nextLessonId,

        List<FormationQuizQuestionResponse> quiz

) {
}
