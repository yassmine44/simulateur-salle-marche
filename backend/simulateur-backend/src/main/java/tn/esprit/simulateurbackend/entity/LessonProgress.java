package tn.esprit.simulateurbackend.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "lesson_progress",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_lesson_progress_user_lesson",
                columnNames = {"user_id", "lesson_id"}
        )
)
public class LessonProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    // Meilleur score au quiz, en pourcentage. Null si aucun quiz passé.
    @Column(name = "best_score_percent")
    private Integer bestScorePercent;


    public LessonProgress() {
    }


    public LessonProgress(Long userId, Long lessonId) {
        this.userId = userId;
        this.lessonId = lessonId;
        this.completedAt = Instant.now();
    }


    public Long getId() { return id; }

    public Long getUserId() { return userId; }

    public Long getLessonId() { return lessonId; }

    public Instant getCompletedAt() { return completedAt; }

    public Integer getBestScorePercent() { return bestScorePercent; }
    public void setBestScorePercent(Integer bestScorePercent) { this.bestScorePercent = bestScorePercent; }
}
