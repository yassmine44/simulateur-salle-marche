package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.FormationCourseDetailResponse;
import tn.esprit.simulateurbackend.dto.FormationCourseResponse;
import tn.esprit.simulateurbackend.dto.FormationLessonResponse;
import tn.esprit.simulateurbackend.dto.FormationLessonSummary;
import tn.esprit.simulateurbackend.dto.FormationQuizQuestionResponse;
import tn.esprit.simulateurbackend.dto.QuizResultResponse;
import tn.esprit.simulateurbackend.entity.Course;
import tn.esprit.simulateurbackend.entity.Lesson;
import tn.esprit.simulateurbackend.entity.LessonProgress;
import tn.esprit.simulateurbackend.entity.QuizQuestion;
import tn.esprit.simulateurbackend.repository.CourseRepository;
import tn.esprit.simulateurbackend.repository.LessonProgressRepository;
import tn.esprit.simulateurbackend.repository.LessonRepository;
import tn.esprit.simulateurbackend.repository.UserRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * Formation : catalogue, leçons, quiz et progression de l'utilisateur.
 *
 * Open-in-view est désactivé : chaque méthode est transactionnelle et
 * convertit les entités en DTO avant de rendre la main.
 */
@Service
public class FormationService {

    // Score minimal (en %) pour réussir un quiz et valider la leçon.
    private static final int PASS_PERCENT = 60;

    private final CourseRepository courseRepository;

    private final LessonRepository lessonRepository;

    private final LessonProgressRepository progressRepository;

    private final UserRepository userRepository;


    public FormationService(
            CourseRepository courseRepository,
            LessonRepository lessonRepository,
            LessonProgressRepository progressRepository,
            UserRepository userRepository
    ) {
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.progressRepository = progressRepository;
        this.userRepository = userRepository;
    }


    // =========================
    // CATALOGUE
    // =========================

    @Transactional(readOnly = true)
    public List<FormationCourseResponse> listCourses(String email) {

        Set<Long> done = completedLessonIds(resolveUserId(email));

        List<FormationCourseResponse> result = new ArrayList<>();

        for (Course course
                : courseRepository.findByPublishedTrueOrderByDisplayOrderAsc()) {

            int total = course.getLessons().size();

            int completed = countCompleted(course, done);

            result.add(new FormationCourseResponse(
                    course.getId(),
                    course.getTitle(),
                    course.getDescription(),
                    course.getLevel(),
                    course.getCategory(),
                    total,
                    completed,
                    totalMinutes(course),
                    percent(completed, total)
            ));
        }

        return result;
    }


    @Transactional(readOnly = true)
    public FormationCourseDetailResponse getCourse(
            String email,
            Long courseId
    ) {

        Set<Long> done = completedLessonIds(resolveUserId(email));

        Course course = findPublishedCourse(courseId);

        List<FormationLessonSummary> lessons = new ArrayList<>();

        for (Lesson lesson : course.getLessons()) {

            lessons.add(new FormationLessonSummary(
                    lesson.getId(),
                    lesson.getTitle(),
                    lesson.getDurationMinutes(),
                    !lesson.getQuiz().isEmpty(),
                    done.contains(lesson.getId())
            ));
        }

        int completed = countCompleted(course, done);

        return new FormationCourseDetailResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getLevel(),
                course.getCategory(),
                completed,
                totalMinutes(course),
                percent(completed, course.getLessons().size()),
                lessons
        );
    }


    // =========================
    // LEÇON
    // =========================

    @Transactional(readOnly = true)
    public FormationLessonResponse getLesson(
            String email,
            Long lessonId
    ) {

        Long userId = resolveUserId(email);

        Lesson lesson = findLesson(lessonId);

        Course course = findPublishedCourse(
                lesson.getCourse().getId()
        );

        List<Lesson> siblings = course.getLessons();

        int index = indexOf(siblings, lessonId);

        Long previousId = index > 0
                ? siblings.get(index - 1).getId()
                : null;

        Long nextId = index >= 0 && index < siblings.size() - 1
                ? siblings.get(index + 1).getId()
                : null;

        LessonProgress progress = progressRepository
                .findByUserIdAndLessonId(userId, lessonId)
                .orElse(null);

        List<FormationQuizQuestionResponse> quiz = new ArrayList<>();

        for (QuizQuestion question : lesson.getQuiz()) {

            quiz.add(new FormationQuizQuestionResponse(
                    question.getId(),
                    question.getQuestion(),
                    List.copyOf(question.getOptions())
            ));
        }

        return new FormationLessonResponse(
                lesson.getId(),
                course.getId(),
                course.getTitle(),
                lesson.getTitle(),
                lesson.getContent(),
                lesson.getDurationMinutes(),
                lesson.getVideoUrl(),
                progress != null,
                progress != null ? progress.getBestScorePercent() : null,
                previousId,
                nextId,
                quiz
        );
    }


    @Transactional
    public void completeLesson(
            String email,
            Long lessonId
    ) {

        Long userId = resolveUserId(email);

        Lesson lesson = findLesson(lessonId);

        findPublishedCourse(lesson.getCourse().getId());

        markCompleted(userId, lessonId, null);
    }


    // =========================
    // QUIZ
    // =========================

    @Transactional
    public QuizResultResponse submitQuiz(
            String email,
            Long lessonId,
            List<Integer> answers
    ) {

        Long userId = resolveUserId(email);

        Lesson lesson = findLesson(lessonId);

        findPublishedCourse(lesson.getCourse().getId());

        List<QuizQuestion> questions = lesson.getQuiz();

        if (questions.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cette leçon n'a pas de quiz."
            );
        }

        if (answers.size() != questions.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Il faut une réponse pour chacune des "
                            + questions.size() + " questions."
            );
        }

        int correct = 0;

        List<QuizResultResponse.QuestionResult> results =
                new ArrayList<>();

        for (int i = 0; i < questions.size(); i++) {

            QuizQuestion question = questions.get(i);

            Integer answer = answers.get(i);

            boolean isCorrect = answer != null
                    && answer == question.getCorrectIndex();

            if (isCorrect) {
                correct++;
            }

            results.add(new QuizResultResponse.QuestionResult(
                    question.getId(),
                    isCorrect,
                    question.getCorrectIndex(),
                    question.getExplanation()
            ));
        }

        int scorePercent = percent(correct, questions.size());

        boolean passed = scorePercent >= PASS_PERCENT;

        if (passed) {
            markCompleted(userId, lessonId, scorePercent);
        } else {
            recordBestScoreIfCompleted(userId, lessonId, scorePercent);
        }

        boolean completed = passed
                || progressRepository
                .findByUserIdAndLessonId(userId, lessonId)
                .isPresent();

        return new QuizResultResponse(
                correct,
                questions.size(),
                scorePercent,
                passed,
                completed,
                results
        );
    }


    // =========================
    // OUTILS
    // =========================

    private void markCompleted(
            Long userId,
            Long lessonId,
            Integer scorePercent
    ) {

        LessonProgress progress = progressRepository
                .findByUserIdAndLessonId(userId, lessonId)
                .orElseGet(() -> new LessonProgress(userId, lessonId));

        if (scorePercent != null) {

            Integer best = progress.getBestScorePercent();

            if (best == null || scorePercent > best) {
                progress.setBestScorePercent(scorePercent);
            }
        }

        progressRepository.save(progress);
    }


    private void recordBestScoreIfCompleted(
            Long userId,
            Long lessonId,
            int scorePercent
    ) {

        progressRepository
                .findByUserIdAndLessonId(userId, lessonId)
                .ifPresent(progress -> {

                    Integer best = progress.getBestScorePercent();

                    if (best == null || scorePercent > best) {
                        progress.setBestScorePercent(scorePercent);
                        progressRepository.save(progress);
                    }
                });
    }


    private Set<Long> completedLessonIds(Long userId) {

        Set<Long> ids = new HashSet<>();

        for (LessonProgress progress
                : progressRepository.findByUserId(userId)) {
            ids.add(progress.getLessonId());
        }

        return ids;
    }


    private int countCompleted(Course course, Set<Long> done) {

        int count = 0;

        for (Lesson lesson : course.getLessons()) {
            if (done.contains(lesson.getId())) {
                count++;
            }
        }

        return count;
    }


    private int totalMinutes(Course course) {

        int minutes = 0;

        for (Lesson lesson : course.getLessons()) {
            minutes += lesson.getDurationMinutes();
        }

        return minutes;
    }


    private int percent(int part, int total) {

        if (total <= 0) {
            return 0;
        }

        return Math.round(part * 100f / total);
    }


    private int indexOf(List<Lesson> lessons, Long lessonId) {

        for (int i = 0; i < lessons.size(); i++) {
            if (lessons.get(i).getId().equals(lessonId)) {
                return i;
            }
        }

        return -1;
    }


    private Course findPublishedCourse(Long courseId) {

        Course course = courseRepository
                .findById(courseId)
                .orElseThrow(() -> notFound("Formation introuvable."));

        if (!course.isPublished()) {
            throw notFound("Formation introuvable.");
        }

        return course;
    }


    private Lesson findLesson(Long lessonId) {

        return lessonRepository
                .findById(lessonId)
                .orElseThrow(() -> notFound("Leçon introuvable."));
    }


    private Long resolveUserId(String email) {

        return userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Utilisateur introuvable."
                ))
                .getId();
    }


    private ResponseStatusException notFound(String message) {

        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                message
        );
    }
}
