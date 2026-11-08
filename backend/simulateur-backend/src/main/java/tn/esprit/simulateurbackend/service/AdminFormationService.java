package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.simulateurbackend.dto.*;
import tn.esprit.simulateurbackend.entity.Course;
import tn.esprit.simulateurbackend.entity.Lesson;
import tn.esprit.simulateurbackend.entity.QuizQuestion;
import tn.esprit.simulateurbackend.repository.CourseRepository;
import tn.esprit.simulateurbackend.repository.LessonProgressRepository;
import tn.esprit.simulateurbackend.repository.LessonRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class AdminFormationService {

    // Lien YouTube accepté : watch?v=ID, youtu.be/ID ou embed/ID.
    private static final Pattern YOUTUBE = Pattern.compile(
            "^https://(www\\.)?(youtube\\.com/(watch\\?v=|embed/)|youtu\\.be/)"
                    + "[A-Za-z0-9_-]{11}([&?#].*)?$"
    );

    private final CourseRepository courseRepository;

    private final LessonRepository lessonRepository;

    private final LessonProgressRepository progressRepository;


    public AdminFormationService(
            CourseRepository courseRepository,
            LessonRepository lessonRepository,
            LessonProgressRepository progressRepository
    ) {
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
    }


    // =========================
    // FORMATIONS
    // =========================

    @Transactional(readOnly = true)
    public List<AdminCourseResponse> listCourses() {

        List<AdminCourseResponse> result = new ArrayList<>();

        for (Course course
                : courseRepository.findAllByOrderByDisplayOrderAsc()) {
            result.add(toResponse(course, false));
        }

        return result;
    }


    @Transactional(readOnly = true)
    public AdminCourseResponse getCourse(Long courseId) {

        return toResponse(findCourse(courseId), true);
    }


    @Transactional
    public AdminCourseResponse createCourse(
            AdminCourseRequest request
    ) {

        Course course = new Course();

        applyCourse(course, request);

        return toResponse(courseRepository.save(course), true);
    }


    @Transactional
    public AdminCourseResponse updateCourse(
            Long courseId,
            AdminCourseRequest request
    ) {

        Course course = findCourse(courseId);

        applyCourse(course, request);

        return toResponse(course, true);
    }


    @Transactional
    public void deleteCourse(Long courseId) {

        Course course = findCourse(courseId);

        List<Long> lessonIds = new ArrayList<>();

        for (Lesson lesson : course.getLessons()) {
            lessonIds.add(lesson.getId());
        }

        if (!lessonIds.isEmpty()) {
            progressRepository.deleteByLessonIdIn(lessonIds);
        }

        courseRepository.delete(course);
    }


    // =========================
    // LEÇONS
    // =========================

    @Transactional
    public AdminCourseResponse createLesson(
            Long courseId,
            AdminLessonRequest request
    ) {

        Course course = findCourse(courseId);

        Lesson lesson = new Lesson();

        applyLesson(lesson, request);

        course.addLesson(lesson);

        lessonRepository.saveAndFlush(lesson);

        return toResponse(course, true);
    }


    @Transactional
    public AdminCourseResponse updateLesson(
            Long lessonId,
            AdminLessonRequest request
    ) {

        Lesson lesson = findLesson(lessonId);

        applyLesson(lesson, request);

        lessonRepository.saveAndFlush(lesson);

        return toResponse(lesson.getCourse(), true);
    }


    @Transactional
    public AdminCourseResponse deleteLesson(Long lessonId) {

        Lesson lesson = findLesson(lessonId);

        Course course = lesson.getCourse();

        progressRepository.deleteByLessonId(lessonId);

        // orphanRemoval supprime la leçon et ses questions.
        course.getLessons().remove(lesson);

        courseRepository.saveAndFlush(course);

        return toResponse(course, true);
    }


    // =========================
    // APPLICATION DES CHAMPS
    // =========================

    private void applyCourse(
            Course course,
            AdminCourseRequest request
    ) {

        course.setTitle(request.title().trim());
        course.setDescription(request.description().trim());
        course.setLevel(request.level());
        course.setCategory(request.category().trim());
        course.setDisplayOrder(request.displayOrder());
        course.setPublished(request.published());
    }


    private void applyLesson(
            Lesson lesson,
            AdminLessonRequest request
    ) {

        lesson.setTitle(request.title().trim());
        lesson.setContent(request.content().strip());
        lesson.setDurationMinutes(request.durationMinutes());
        lesson.setDisplayOrder(request.displayOrder());
        lesson.setVideoUrl(normalizeVideoUrl(request.videoUrl()));

        // Remplacement complet du quiz (orphanRemoval supprime l'ancien).
        lesson.getQuiz().clear();

        for (AdminQuizQuestionRequest q : request.quiz()) {

            validateQuestion(q);

            QuizQuestion question = new QuizQuestion();

            question.setQuestion(q.question().trim());

            for (String option : q.options()) {
                question.getOptions().add(option.trim());
            }

            question.setCorrectIndex(q.correctIndex());

            question.setExplanation(
                    q.explanation() == null || q.explanation().isBlank()
                            ? null
                            : q.explanation().trim()
            );

            lesson.addQuestion(question);
        }
    }


    private void validateQuestion(AdminQuizQuestionRequest q) {

        if (q.correctIndex() < 0
                || q.correctIndex() >= q.options().size()) {

            throw badRequest(
                    "La bonne réponse de la question « "
                            + q.question() + " » doit être l'une des options."
            );
        }
    }


    private String normalizeVideoUrl(String raw) {

        if (raw == null || raw.isBlank()) {
            return null;
        }

        String url = raw.trim();

        if (!YOUTUBE.matcher(url).matches()) {
            throw badRequest(
                    "Le lien vidéo doit être un lien YouTube valide "
                            + "(ex. https://www.youtube.com/watch?v=XXXXXXXXXXX)."
            );
        }

        return url;
    }


    // =========================
    // MAPPING
    // =========================

    private AdminCourseResponse toResponse(
            Course course,
            boolean withLessons
    ) {

        List<AdminLessonResponse> lessons = new ArrayList<>();

        if (withLessons) {

            List<Lesson> sorted = new ArrayList<>(course.getLessons());

            sorted.sort(
                    Comparator
                            .comparingInt(Lesson::getDisplayOrder)
                            .thenComparing(
                                    Lesson::getId,
                                    Comparator.nullsLast(
                                            Comparator.naturalOrder()
                                    )
                            )
            );

            for (Lesson lesson : sorted) {
                lessons.add(toLessonResponse(lesson));
            }
        }

        return new AdminCourseResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getLevel(),
                course.getCategory(),
                course.getDisplayOrder(),
                course.isPublished(),
                course.getLessons().size(),
                lessons
        );
    }


    private AdminLessonResponse toLessonResponse(Lesson lesson) {

        List<AdminQuizQuestionResponse> quiz = new ArrayList<>();

        for (QuizQuestion question : lesson.getQuiz()) {

            quiz.add(new AdminQuizQuestionResponse(
                    question.getId(),
                    question.getQuestion(),
                    List.copyOf(question.getOptions()),
                    question.getCorrectIndex(),
                    question.getExplanation()
            ));
        }

        return new AdminLessonResponse(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getContent(),
                lesson.getDurationMinutes(),
                lesson.getDisplayOrder(),
                lesson.getVideoUrl(),
                quiz
        );
    }


    // =========================
    // OUTILS
    // =========================

    private Course findCourse(Long courseId) {

        return courseRepository
                .findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Formation introuvable."
                ));
    }


    private Lesson findLesson(Long lessonId) {

        return lessonRepository
                .findById(lessonId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Leçon introuvable."
                ));
    }


    private ResponseStatusException badRequest(String message) {

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}
