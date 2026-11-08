package tn.esprit.simulateurbackend.dto;

import java.util.List;

public record QuizResultResponse(

        int correctCount,

        int totalQuestions,

        int scorePercent,

        boolean passed,

        boolean lessonCompleted,

        List<QuestionResult> results

) {

    public record QuestionResult(

            Long questionId,

            boolean correct,

            int correctIndex,

            String explanation

    ) {
    }
}
