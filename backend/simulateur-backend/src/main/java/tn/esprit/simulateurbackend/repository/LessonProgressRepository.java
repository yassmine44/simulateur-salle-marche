package tn.esprit.simulateurbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.simulateurbackend.entity.LessonProgress;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LessonProgressRepository
        extends JpaRepository<LessonProgress, Long> {

    List<LessonProgress> findByUserId(Long userId);

    Optional<LessonProgress> findByUserIdAndLessonId(
            Long userId,
            Long lessonId
    );

    // Nettoyage de la progression quand une leçon est supprimée.
    void deleteByLessonId(Long lessonId);

    void deleteByLessonIdIn(Collection<Long> lessonIds);
}