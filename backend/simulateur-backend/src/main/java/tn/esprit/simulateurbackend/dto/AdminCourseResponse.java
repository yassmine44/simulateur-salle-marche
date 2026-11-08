package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.CourseLevel;

import java.util.List;

public record AdminCourseResponse(

        Long id,

        String title,

        String description,

        CourseLevel level,

        String category,

        int displayOrder,

        boolean published,

        int lessonCount,

        List<AdminLessonResponse> lessons

) {
}
