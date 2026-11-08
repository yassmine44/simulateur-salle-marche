package tn.esprit.simulateurbackend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.simulateurbackend.dto.AdminCourseRequest;
import tn.esprit.simulateurbackend.dto.AdminCourseResponse;
import tn.esprit.simulateurbackend.dto.AdminLessonRequest;
import tn.esprit.simulateurbackend.service.AdminFormationService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/formation")
public class AdminFormationController {

    private final AdminFormationService service;


    public AdminFormationController(
            AdminFormationService service
    ) {
        this.service = service;
    }


    // =========================
    // FORMATIONS
    // =========================

    @GetMapping("/courses")
    public List<AdminCourseResponse> listCourses() {
        return service.listCourses();
    }


    @GetMapping("/courses/{courseId}")
    public AdminCourseResponse getCourse(
            @PathVariable Long courseId
    ) {
        return service.getCourse(courseId);
    }


    @PostMapping("/courses")
    public ResponseEntity<AdminCourseResponse> createCourse(
            @Valid @RequestBody AdminCourseRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.createCourse(request));
    }


    @PutMapping("/courses/{courseId}")
    public AdminCourseResponse updateCourse(
            @PathVariable Long courseId,
            @Valid @RequestBody AdminCourseRequest request
    ) {
        return service.updateCourse(courseId, request);
    }


    @DeleteMapping("/courses/{courseId}")
    public ResponseEntity<Void> deleteCourse(
            @PathVariable Long courseId
    ) {
        service.deleteCourse(courseId);

        return ResponseEntity.noContent().build();
    }


    // =========================
    // LEÇONS
    // =========================

    @PostMapping("/courses/{courseId}/lessons")
    public ResponseEntity<AdminCourseResponse> createLesson(
            @PathVariable Long courseId,
            @Valid @RequestBody AdminLessonRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(service.createLesson(courseId, request));
    }


    @PutMapping("/lessons/{lessonId}")
    public AdminCourseResponse updateLesson(
            @PathVariable Long lessonId,
            @Valid @RequestBody AdminLessonRequest request
    ) {
        return service.updateLesson(lessonId, request);
    }


    @DeleteMapping("/lessons/{lessonId}")
    public AdminCourseResponse deleteLesson(
            @PathVariable Long lessonId
    ) {
        return service.deleteLesson(lessonId);
    }
}