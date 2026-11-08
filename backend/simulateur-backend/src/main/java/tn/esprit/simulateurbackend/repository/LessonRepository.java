package tn.esprit.simulateurbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.simulateurbackend.entity.Lesson;

public interface LessonRepository
        extends JpaRepository<Lesson, Long> {
}
