package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.CourseLevel;

import java.util.List;

public record FormationCourseDetailResponse(

        Long id,

        String title,

        String description,

        CourseLevel level,

        String category,

        int completedLessons,

        int totalMinutes,

        int progressPercent,

        List<FormationLessonSummary> lessons

) {
}
