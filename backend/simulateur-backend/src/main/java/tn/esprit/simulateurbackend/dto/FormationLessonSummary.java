package tn.esprit.simulateurbackend.dto;

public record FormationLessonSummary(

        Long id,

        String title,

        int durationMinutes,

        boolean hasQuiz,

        boolean completed

) {
}
