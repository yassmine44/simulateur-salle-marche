package tn.esprit.simulateurbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.simulateurbackend.entity.GamificationEvent;
import tn.esprit.simulateurbackend.entity.GamificationPortfolio;

import java.util.Optional;

public interface GamificationPortfolioRepository
        extends JpaRepository<GamificationPortfolio, Long> {

    Optional<GamificationPortfolio>
    findByUserIdAndEvent(
            Long userId,
            GamificationEvent event
    );
}
