package tn.esprit.simulateurbackend.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import tn.esprit.simulateurbackend.dto.FormationCourseDetailResponse;
import tn.esprit.simulateurbackend.dto.FormationCourseResponse;
import tn.esprit.simulateurbackend.dto.FormationLessonResponse;
import tn.esprit.simulateurbackend.dto.MessageResponse;
import tn.esprit.simulateurbackend.dto.QuizResultResponse;
import tn.esprit.simulateurbackend.dto.QuizSubmitRequest;
import tn.esprit.simulateurbackend.service.FormationService;

import java.util.List;

/*
 * Espace de formation : accessible à tout utilisateur connecté
 * (règle anyRequest().authenticated() de SecurityConfig).
 */
@RestController
@RequestMapping("/api/formation")
public class FormationController {

    private final FormationService formationService;


    public FormationController(
            FormationService formationService
    ) {
        this.formationService = formationService;
    }


    @GetMapping("/courses")
    public ResponseEntity<List<FormationCourseResponse>>
    listCourses(
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                formationService.listCourses(
                        authentication.getName()
                )
        );
    }


    @GetMapping("/courses/{courseId}")
    public ResponseEntity<FormationCourseDetailResponse>
    getCourse(
            @PathVariable Long courseId,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                formationService.getCourse(
                        authentication.getName(),
                        courseId
                )
        );
    }


    @GetMapping("/lessons/{lessonId}")
    public ResponseEntity<FormationLessonResponse>
    getLesson(
            @PathVariable Long lessonId,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                formationService.getLesson(
                        authentication.getName(),
                        lessonId
                )
        );
    }


    @PostMapping("/lessons/{lessonId}/complete")
    public ResponseEntity<MessageResponse>
    completeLesson(
            @PathVariable Long lessonId,
            Authentication authentication
    ) {

        formationService.completeLesson(
                authentication.getName(),
                lessonId
        );

        return ResponseEntity.ok(
                new MessageResponse("Leçon terminée.")
        );
    }


    @PostMapping("/lessons/{lessonId}/quiz")
    public ResponseEntity<QuizResultResponse>
    submitQuiz(
            @PathVariable Long lessonId,
            @Valid @RequestBody QuizSubmitRequest request,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                formationService.submitQuiz(
                        authentication.getName(),
                        lessonId,
                        request.answers()
                )
        );
    }
}
