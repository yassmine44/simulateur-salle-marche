package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.CourseLevel;

/*
 * Carte de formation du catalogue, avec la progression de l'utilisateur.
 */
public record FormationCourseResponse(

        Long id,

        String title,

        String description,

        CourseLevel level,

        String category,

        int lessonCount,

        int completedLessons,

        int totalMinutes,

        int progressPercent

) {
}
