package tn.esprit.simulateurbackend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import tn.esprit.simulateurbackend.entity.Course;
import tn.esprit.simulateurbackend.repository.CourseRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ActuarialFormationInitializerTest {

    @Test
    void seedsTheCourseWhenItIsMissing() {
        CourseRepository repository = mock(CourseRepository.class);
        when(repository.existsByTitle(ActuarialFormationInitializer.COURSE_TITLE)).thenReturn(false);

        new ActuarialFormationInitializer(repository).seed();

        verify(repository).save(any(Course.class));
    }

    @Test
    void isIdempotent() {
        CourseRepository repository = mock(CourseRepository.class);
        when(repository.existsByTitle(ActuarialFormationInitializer.COURSE_TITLE)).thenReturn(true);

        new ActuarialFormationInitializer(repository).seed();

        verify(repository, never()).save(any());
    }

    @Test
    void runsAfterTheBaseCatalogueInitializer() throws NoSuchMethodException {
        EventListener listener = ActuarialFormationInitializer.class
                .getMethod("seed")
                .getAnnotation(EventListener.class);
        assertNotNull(listener);
        assertArrayEquals(new Class<?>[]{ApplicationReadyEvent.class}, listener.value());
    }

    @Test
    void courseIsCompleteAndQuizzesAreConsistent() {
        Course course = new ActuarialFormationInitializer(mock(CourseRepository.class)).actuarialCourse();

        assertEquals(3, course.getLessons().size());
        course.getLessons().forEach(lesson -> {
            assertFalse(lesson.getQuiz().isEmpty());
            lesson.getQuiz().forEach(q -> {
                assertTrue(q.getCorrectIndex() >= 0 && q.getCorrectIndex() < q.getOptions().size());
                assertTrue(q.getOptions().stream().allMatch(o -> o.length() <= 300));
            });
        });
    }
}
